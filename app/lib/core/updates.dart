import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../app_info.dart';
import '../app_settings.dart';
import '../platform.dart';
import 'android_core.dart';
import 'core_api.dart';
import 'core_controller.dart';
import 'models.dart';

/// The release's APKs, one per processor type (scripts/build-android.ps1,
/// .github/workflows/build.yml); others get the universal one.
const _apkAbis = {'arm64-v8a', 'armeabi-v7a', 'x86_64'};

/// Where the Download button goes: the release's file for this copy of the
/// app, which the browser downloads at once (no page to choose from): on a
/// phone the APK for its processor; on Windows the installer for an installed
/// copy (its uninstaller is beside it) and the zip for a portable one; on
/// macOS the disk image; on Linux the archive for the processor.
Future<String> downloadUrl(Release release) async {
  if (!release.url.contains('/releases/tag/')) return release.url;
  final String name;
  if (Platform.isAndroid) {
    final abi = await AndroidCore.abi().catchError((Object _) => '');
    name = apkName(abi);
  } else if (Platform.isWindows) {
    final dir = File(Platform.resolvedExecutable).parent.path;
    final installed = File('$dir${Platform.pathSeparator}unins000.exe');
    name = windowsName(installed: installed.existsSync());
  } else if (Platform.isMacOS) {
    name = 'macOS.dmg';
  } else if (Platform.isLinux) {
    name = linuxName(Platform.version);
  } else {
    return release.url;
  }
  return releaseFileUrl(release.url, name);
}

/// The APK's name part for processor [abi].
String apkName(String abi) =>
    'Android-${_apkAbis.contains(abi) ? abi : 'universal'}.apk';

/// The Windows file's name part: the installer, or the portable zip.
String windowsName({required bool installed}) =>
    installed ? 'Windows-Setup-x64.exe' : 'Windows-x64.zip';

/// The Linux archive's name part for the processor in Dart's
/// [platformVersion] ("... on \"linux_arm64\"").
String linuxName(String platformVersion) =>
    'Linux-${platformVersion.contains('arm64') ? 'arm64' : 'amd64'}.tar.gz';

/// The file `CLXY-<version>-[name]` in the release whose page is
/// [releaseUrl] (".../releases/tag/v1.2.3"): its versioned name, so the
/// downloaded file says which version it is.
String releaseFileUrl(String releaseUrl, String name) {
  final tag = releaseUrl.substring(releaseUrl.lastIndexOf('/') + 1);
  final version = tag.replaceFirst(RegExp('^[vV]'), '');
  return '${releaseUrl.replaceFirst('/releases/tag/', '/releases/download/')}'
      '/CLXY-$version-$name';
}

/// The APK for processor [abi] in the release whose page is [releaseUrl].
String apkUrl(String releaseUrl, String abi) =>
    releaseFileUrl(releaseUrl, apkName(abi));

/// Opens the release's download (see [downloadUrl]).
Future<void> openDownload(Release release) async =>
    openUrl(await downloadUrl(release));

/// The local proxy for GitHub requests, on networks that block GitHub: the
/// VPN's own while it runs without a proxy login; null otherwise.
String? updateProxy(CoreStatus? status) {
  final proxy = status?.proxy;
  if (status?.state.phase != Phase.running || proxy == null) return null;
  if (proxy.loginRequired) return null;
  return CoreLocator.listenUri(proxy.listen).authority;
}

typedef UpdateLookup = Future<Release?> Function(String? proxy);
typedef UpdateFound = void Function(Release? release);
typedef UpdateNotification = Future<bool> Function(Release release);

/// One update lookup policy shared by successful-connection and scheduled
/// checks. A working result is cached; failed checks are briefly throttled so
/// reconnect flapping or an offline phone cannot hammer GitHub.
class UpdateCheckCoordinator {
  UpdateCheckCoordinator({
    required this.lookup,
    required this.onFound,
    required this.notify,
    DateTime Function()? now,
    this.failureThrottle = const Duration(minutes: 5),
    this._lastCheckedAt,
    this._knownRelease,
    this._notifiedVersion = '',
  }) : _now = now ?? DateTime.now;

  final UpdateLookup lookup;
  final UpdateFound onFound;
  final UpdateNotification notify;
  final DateTime Function() _now;
  final Duration failureThrottle;

  DateTime? _lastCheckedAt;
  DateTime? _lastAttemptAt;
  Release? _knownRelease;
  String _notifiedVersion;
  String? _lastConnectionKey;
  Future<Release?>? _inFlight;
  bool _announceAfterCheck = false;

  DateTime? get lastCheckedAt => _lastCheckedAt;
  Release? get available => _knownRelease;

  /// Starts one fresh lookup for each distinct confirmed connection. Multiple
  /// status polls for the same connection are ignored; concurrent checks join
  /// the in-flight request, and only failed requests receive a cooldown.
  Future<Release?> onSuccessfulConnection(
    String connectionKey, {
    String? proxy,
  }) async {
    if (connectionKey.isEmpty || connectionKey == _lastConnectionKey) {
      return _knownRelease;
    }
    _lastConnectionKey = connectionKey;

    final cached = _knownRelease;
    if (cached != null) await _announce(cached);
    if (_inFlight != null) {
      _announceAfterCheck = true;
      return _inFlight;
    }
    if (_isWithin(_lastAttemptAt, failureThrottle)) return cached;
    return _check(proxy: proxy, announce: true);
  }

  /// Runs the periodic check only when no recent successful result or failed
  /// attempt already covers it.
  Future<Release?> checkIfDue({required Duration interval, String? proxy}) {
    if (_isWithin(_lastCheckedAt, interval) ||
        _isWithin(_lastAttemptAt, failureThrottle)) {
      return Future<Release?>.value(_knownRelease);
    }
    return _check(proxy: proxy, announce: false);
  }

  /// Keeps manual About-page results in sync with the automatic cache.
  void remember(Release? release, {DateTime? checkedAt}) {
    _knownRelease = release;
    if (checkedAt != null) _lastCheckedAt = checkedAt;
  }

  bool _isWithin(DateTime? time, Duration duration) {
    if (time == null) return false;
    final elapsed = _now().difference(time);
    return elapsed.isNegative || elapsed < duration;
  }

  Future<Release?> _check({required String? proxy, required bool announce}) {
    final active = _inFlight;
    if (active != null) {
      _announceAfterCheck = _announceAfterCheck || announce;
      return active;
    }
    _announceAfterCheck = announce;
    final raw = _perform(proxy);
    late final Future<Release?> tracked;
    tracked = raw.whenComplete(() {
      if (identical(_inFlight, tracked)) _inFlight = null;
      _announceAfterCheck = false;
    });
    _inFlight = tracked;
    return tracked;
  }

  Future<Release?> _perform(String? proxy) async {
    _lastAttemptAt = _now();
    final Release? release;
    try {
      release = await lookup(proxy);
    } on Exception {
      // Offline, timed out, or rate-limited. The next eligible connection
      // retries after a short in-memory cooldown; nothing is persisted as a
      // successful check.
      return null;
    } catch (_) {
      // A malformed response or platform error must not disturb the VPN UI.
      return null;
    }

    _lastAttemptAt = null;
    _lastCheckedAt = _now();
    _knownRelease = release;
    onFound(release);
    if (release != null && _announceAfterCheck) await _announce(release);
    return release;
  }

  Future<void> _announce(Release release) async {
    if (release.version.isEmpty || release.version == _notifiedVersion) return;
    try {
      if (await notify(release)) _notifiedVersion = release.version;
    } catch (_) {
      // Notifications are optional: permission denial or a missing channel
      // must never turn an otherwise successful update check into a crash.
    }
  }
}

/// Watches controller polls and signals only a newly confirmed running
/// connection, not the intermediate "connecting" state.
class SuccessfulConnectionUpdateListener {
  SuccessfulConnectionUpdateListener(this.core, this.onConnected) {
    core.addListener(_changed);
    _changed();
  }

  final CoreController core;
  final Future<void> Function(String connectionKey, String? proxy) onConnected;
  String? _lastConnectionKey;
  bool _disposed = false;

  void _changed() {
    if (_disposed) return;
    final status = core.status;
    if (status == null ||
        core.unavailable != null ||
        status.state.phase != Phase.running) {
      _lastConnectionKey = null;
      return;
    }
    if (!status.confirmedBy(core.lastTest)) return;

    final proxy = status.proxy;
    final key = [
      proxy?.startedAt?.toUtc().microsecondsSinceEpoch ?? '',
      status.networkChangedAt?.toUtc().microsecondsSinceEpoch ?? '',
      status.selection,
      proxy?.countryCode ?? '',
    ].join('|');
    if (key == _lastConnectionKey) return;
    _lastConnectionKey = key;
    unawaited(onConnected(key, updateProxy(status)).catchError((Object _) {}));
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    core.removeListener(_changed);
  }
}

/// Looks for a newer release on GitHub every 5 days: the app looks a minute
/// after it starts, and then every hour, whether 5 days have passed since the
/// last look that worked. Each distinct successful VPN connection also starts
/// a fresh check. Results survive a restart; Android shows an actionable
/// notification when a newer release is found.
abstract final class Updates {
  /// How long a successful scheduled check holds.
  static const every = Duration(days: 5);

  /// The newer release, once one was found.
  static final available = ValueNotifier<Release?>(null);

  static Timer? _timer;
  static Timer? _initialTimer;
  static CoreController? _core;
  static AppSettings? _settings;
  static UpdateCheckCoordinator? _checker;
  static SuccessfulConnectionUpdateListener? _connectionListener;

  static void start(CoreController core, AppSettings settings) {
    if (githubRepo.isEmpty || _timer != null) return;
    _core = core;
    _settings = settings;
    // A version found earlier, unless this app is already that one.
    if (newerVersion(settings.updateVersion, appVersion)) {
      available.value = Release(settings.updateVersion, settings.updateUrl);
    }
    _checker = UpdateCheckCoordinator(
      lookup: (proxy) => checkForUpdate(proxy: proxy),
      onFound: found,
      notify: (release) async {
        if (!Platform.isAndroid) return false;
        final shown = await AndroidCore.showUpdateNotification(release.version);
        if (shown) {
          _settings?.update(updateNotifiedVersion: release.version);
        }
        return shown;
      },
      lastCheckedAt: settings.updateCheckedAt,
      knownRelease: available.value,
      notifiedVersion: settings.updateNotifiedVersion,
    );
    _connectionListener = SuccessfulConnectionUpdateListener(core, (
      key,
      proxy,
    ) async {
      await _checker?.onSuccessfulConnection(key, proxy: proxy);
    });
    _initialTimer = Timer(const Duration(minutes: 1), _due);
    _timer = Timer.periodic(const Duration(hours: 1), (_) => _due());
  }

  /// Stops timers and detaches the controller observer, mainly for clean
  /// application shutdown and isolated tests.
  static void stop() {
    _initialTimer?.cancel();
    _initialTimer = null;
    _timer?.cancel();
    _timer = null;
    _connectionListener?.dispose();
    _connectionListener = null;
    _checker = null;
    _core = null;
    _settings = null;
  }

  static Future<void> _due() async {
    final checker = _checker;
    if (checker == null) return;
    await checker.checkIfDue(
      interval: every,
      proxy: updateProxy(_core?.status),
    );
  }

  /// The result of a check, here or on the About page: a newer [release], or
  /// null when this version is the newest.
  static void found(Release? release) {
    available.value = release;
    final checkedAt = DateTime.now();
    _checker?.remember(release, checkedAt: checkedAt);
    _settings?.update(
      updateCheckedAt: checkedAt,
      updateVersion: release?.version ?? '',
      updateUrl: release?.url ?? '',
    );
  }
}
