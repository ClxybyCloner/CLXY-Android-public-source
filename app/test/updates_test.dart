import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/app_info.dart';
import 'package:clxy/core/core_controller.dart';
import 'package:clxy/core/models.dart';
import 'package:clxy/core/updates.dart';

const _page = 'https://github.com/o/r/releases/tag/v1.2.3';

Map<String, Object?> _statusJson({
  required String phase,
  required String startedAt,
  required String testedAt,
  required bool testOk,
  String selection = 'us',
  String countryCode = 'us',
}) => {
  'state': {'phase': phase, 'message': phase},
  'proxy': {
    'listen': '127.0.0.1:1080',
    'started_at': startedAt,
    'country_code': countryCode,
  },
  'last_test': {'at': testedAt, 'ok': testOk},
  'selection': selection,
  'network_changed_at': '2026-10-29T08:59:00Z',
};

void main() {
  test('the embedded updater uses the update-only public repository', () {
    expect(githubRepo, 'ClxybyCloner/CLXY-Android');
    expect(
      apkUrl('https://github.com/$githubRepo/releases/tag/v1.0.9', 'arm64-v8a'),
      'https://github.com/ClxybyCloner/CLXY-Android/releases/download/v1.0.9/'
      'CLXY-1.0.9-Android-arm64-v8a.apk',
    );
  });

  test('v1.0.19 is offered to v1.0.18 after a successful connection', () async {
    expect(newerVersion('1.0.19', '1.0.18'), isTrue);
    expect(newerVersion('1.0.18', '1.0.18'), isFalse);
    final notices = <String>[];
    final checker = UpdateCheckCoordinator(
      lookup: (_) async => const Release(
        '1.0.19',
        'https://github.com/ClxybyCloner/CLXY-Android/releases/tag/v1.0.19',
      ),
      onFound: (_) {},
      notify: (release) async {
        notices.add(release.version);
        return true;
      },
    );

    await checker.onSuccessfulConnection('confirmed-v1.0.18-connection');

    expect(notices, ['1.0.19']);
  });

  test('a phone downloads the APK for its processor', () {
    expect(
      apkUrl(_page, 'arm64-v8a'),
      'https://github.com/o/r/releases/download/v1.2.3/CLXY-1.2.3-Android-arm64-v8a.apk',
    );
    expect(
      apkUrl(_page, 'armeabi-v7a'),
      endsWith('/v1.2.3/CLXY-1.2.3-Android-armeabi-v7a.apk'),
    );
    expect(apkUrl(_page, 'x86_64'), endsWith('-x86_64.apk'));
  });

  test('another processor gets the universal APK', () {
    expect(apkUrl(_page, 'x86'), endsWith('/CLXY-1.2.3-Android-universal.apk'));
    expect(apkUrl(_page, ''), endsWith('/CLXY-1.2.3-Android-universal.apk'));
  });

  test('a computer downloads the file for its kind of copy', () {
    expect(
      releaseFileUrl(_page, windowsName(installed: true)),
      'https://github.com/o/r/releases/download/v1.2.3/CLXY-1.2.3-Windows-Setup-x64.exe',
    );
    expect(
      releaseFileUrl(_page, windowsName(installed: false)),
      endsWith('/v1.2.3/CLXY-1.2.3-Windows-x64.zip'),
    );
    expect(linuxName('3.10.0 (stable) on "linux_arm64"'), 'Linux-arm64.tar.gz');
    expect(linuxName('3.10.0 (stable) on "linux_x64"'), 'Linux-amd64.tar.gz');
  });

  test(
    'only a newly confirmed successful connection triggers an update check',
    () {
      final core = CoreController(() async => throw StateError('unused'));
      final connections = <(String, String?)>[];
      final listener = SuccessfulConnectionUpdateListener(core, (
        key,
        proxy,
      ) async {
        connections.add((key, proxy));
      });

      core.status = CoreStatus.fromJson(
        _statusJson(
          phase: 'running',
          startedAt: '2026-10-29T09:00:00Z',
          testedAt: '2026-10-29T09:01:00Z',
          testOk: false,
        ),
      );
      core.notifyListeners();
      expect(
        connections,
        isEmpty,
        reason: 'a running but unconfirmed tunnel is not connected',
      );

      core.status = CoreStatus.fromJson(
        _statusJson(
          phase: 'running',
          startedAt: '2026-10-29T09:00:00Z',
          testedAt: '2026-10-29T09:01:00Z',
          testOk: true,
        ),
      );
      core.notifyListeners();
      expect(connections, hasLength(1));
      expect(connections.single.$2, '127.0.0.1:1080');

      core.notifyListeners();
      expect(
        connections,
        hasLength(1),
        reason: 'polling the same connection does not trigger again',
      );

      core.status = CoreStatus.fromJson(
        _statusJson(
          phase: 'running',
          startedAt: '2026-10-29T09:05:00Z',
          testedAt: '2026-10-29T09:06:00Z',
          testOk: true,
          selection: 'gb',
          countryCode: 'gb',
        ),
      );
      core.notifyListeners();
      expect(
        connections,
        hasLength(2),
        reason: 'a different confirmed location is a new connection',
      );

      listener.dispose();
      core.dispose();
    },
  );

  test('each distinct successful connection checks again but duplicate polls do not', () async {
    var now = DateTime.utc(2026, 10, 29, 10);
    var lookups = 0;
    final announced = <String>[];
    final found = <Release?>[];
    var latest = const Release(
      '1.0.11',
      'https://github.com/o/r/releases/tag/v1.0.11',
    );
    final checker = UpdateCheckCoordinator(
      lookup: (_) async {
        lookups++;
        return latest;
      },
      onFound: found.add,
      notify: (release) async {
        announced.add(release.version);
        return true;
      },
      now: () => now,
    );

    await checker.onSuccessfulConnection(
      'connection-1',
      proxy: '127.0.0.1:1080',
    );
    expect(lookups, 1);
    expect(found.single?.version, '1.0.11');
    expect(announced, ['1.0.11']);

    await checker.onSuccessfulConnection('connection-1');
    now = now.add(const Duration(minutes: 1));
    await checker.onSuccessfulConnection('connection-2');
    expect(
      lookups,
      2,
      reason: 'a new successful connection starts a fresh lookup',
    );
    expect(announced, ['1.0.11'], reason: 'a version is announced only once');

    now = now.add(const Duration(minutes: 1));
    latest = const Release(
      '1.0.12',
      'https://github.com/o/r/releases/tag/v1.0.12',
    );
    await checker.onSuccessfulConnection('connection-3');
    expect(lookups, 3);
    expect(announced, ['1.0.11', '1.0.12']);
  });

  test('a successful no-update result is cached without a notice', () async {
    var now = DateTime.utc(2026, 10, 29, 10);
    var lookups = 0;
    var foundCalls = 0;
    var notices = 0;
    final checker = UpdateCheckCoordinator(
      lookup: (_) async {
        lookups++;
        return null;
      },
      onFound: (_) => foundCalls++,
      notify: (_) async {
        notices++;
        return true;
      },
      now: () => now,
    );

    await checker.onSuccessfulConnection('connection-1');
    expect(lookups, 1);
    expect(foundCalls, 1);
    expect(checker.lastCheckedAt, now);
    expect(notices, 0);

    now = now.add(const Duration(minutes: 3));
    await checker.onSuccessfulConnection('connection-2');
    expect(lookups, 2, reason: 'a new successful connection checks again');
    expect(notices, 0);
  });

  test(
    'offline or rate-limited checks fail quietly and retry after cooldown',
    () async {
      var now = DateTime.utc(2026, 10, 29, 10);
      var fail = true;
      var lookups = 0;
      var notices = 0;
      final checker = UpdateCheckCoordinator(
        lookup: (_) async {
          lookups++;
          if (fail) throw const HttpException('HTTP 403');
          return const Release(
            '1.0.11',
            'https://github.com/o/r/releases/tag/v1.0.11',
          );
        },
        onFound: (_) {},
        notify: (_) async {
          notices++;
          return true;
        },
        now: () => now,
      );

      await checker.onSuccessfulConnection('connection-1');
      expect(lookups, 1);
      expect(
        checker.lastCheckedAt,
        isNull,
        reason: 'a failed request is not cached as success',
      );
      now = now.add(const Duration(minutes: 1));
      await checker.onSuccessfulConnection('connection-2');
      expect(
        lookups,
        1,
        reason: 'reconnect flapping is held by the failure cooldown',
      );

      fail = false;
      now = now.add(const Duration(minutes: 5));
      await checker.onSuccessfulConnection('connection-3');
      expect(lookups, 2);
      expect(notices, 1);
    },
  );
}
