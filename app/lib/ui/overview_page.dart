import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../app_settings.dart';
import '../core/core_controller.dart';
import '../core/updates.dart';
import '../core/models.dart';
import '../core/ports.dart';
import '../l10n/app_localizations.dart';
import '../platform.dart';
import 'format.dart';
import 'port_dialog.dart';
import 'browser_signin.dart';
import 'vendor_text.dart';
import 'widgets.dart';
import 'connection_globe.dart';
import 'network_location.dart';
import 'mobile_visuals.dart';
part 'desktop_dashboard.dart';

/// The home page: the switch, the state, where the traffic goes, and the
/// data used; the phone's (_PhoneHome) and the computer's (_DesktopHome)
/// share the look, not the logic. Developer mode adds the details.
class OverviewPage extends StatelessWidget {
  const OverviewPage({
    super.key,
    required this.core,
    required this.settings,
    this.onOpenServers,
    this.onOpenStats,
    this.onOpenAccounts,
    this.onOpenSettings,
    this.onOpenLogs,
    this.onOpenConnection,
    this.phone = false,
  });

  final CoreController core;
  final AppSettings settings;

  /// The phone layout: a colored hero and the data left (_PhoneHome).
  final bool phone;

  /// Opens the server list, stats, and accounts from the home page's cards (a
  /// page on the phone, a tab on the computer).
  final VoidCallback? onOpenServers;
  final VoidCallback? onOpenStats;
  final VoidCallback? onOpenAccounts;
  final VoidCallback? onOpenSettings;
  final VoidCallback? onOpenLogs;

  /// Opens the connection settings, when Mozilla's servers are blocked.
  final VoidCallback? onOpenConnection;

  @override
  Widget build(BuildContext context) {
    final status = core.status!;
    final showIp = settings.showPublicIp;
    if (phone) {
      return _PhoneHome(
        core: core,
        status: status,
        showIp: showIp,
        settings: settings,
        onOpenServers: onOpenServers,
        onOpenStats: onOpenStats,
        onOpenAccounts: onOpenAccounts,
        onOpenConnection: onOpenConnection,
      );
    }
    return _DesktopHome(
      core: core,
      status: status,
      showIp: showIp,
      settings: settings,
      onOpenServers: onOpenServers,
      onOpenAccounts: onOpenAccounts,
      onOpenSettings: onOpenSettings,
      onOpenLogs: onOpenLogs,
      onOpenConnection: onOpenConnection,
    );
  }
}

/// Opens the connection settings under "Mozilla's servers are blocked":
/// HTTP/2, HTTP/3, or HTTP/1.1, one of which a network may let through.
class _ConnectionSettingsButton extends StatelessWidget {
  const _ConnectionSettingsButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 6),
    child: Center(
      child: TextButton.icon(
        onPressed: onPressed,
        icon: const Icon(Icons.tune_rounded, size: 18),
        label: Text(AppLocalizations.of(context).connectionSettings),
      ),
    ),
  );
}

/// Under "This location isn't available for your account": switches to
/// Mozilla's recommended location (an empty `proxy.country`).
class _RecommendedLocationButton extends StatelessWidget {
  const _RecommendedLocationButton({required this.core});

  final CoreController core;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 6),
    child: Center(
      child: TextButton.icon(
        onPressed: () => runShowingErrors(context, () => core.setLocation('')),
        icon: const Icon(Icons.public_rounded, size: 18),
        label: Text(AppLocalizations.of(context).useRecommendedLocation),
      ),
    ),
  );
}

/// An amber box when another program (another VPN or proxy) replaced the
/// system proxy the core set, so applications no longer use the VPN; the
/// button takes it back. The core never does that by itself.
class _SystemProxyTakenNote extends StatelessWidget {
  const _SystemProxyTakenNote({required this.core, required this.by});

  final CoreController core;

  /// What the system proxy points at now; empty when it was turned off.
  final String by;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _ProblemBox(
            text: by.isEmpty
                ? l.systemProxyTakenOff
                : l.systemProxyTakenBy(ltr(by)),
            color: StatusColors.busy,
            icon: Icons.warning_amber_rounded,
          ),
          const SizedBox(height: 6),
          TextButton.icon(
            onPressed: () =>
                runShowingErrors(context, () => core.setSystemProxy('set')),
            icon: const Icon(Icons.refresh_rounded, size: 18),
            label: Text(l.setSystemProxyAgain),
          ),
        ],
      ),
    );
  }
}

/// An amber box when the public IP is in another country than the chosen
/// one: a hint, not an error; the connection works.
class _ExitMismatchNote extends StatelessWidget {
  const _ExitMismatchNote({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 10),
    child: _ProblemBox(
      text: text,
      color: StatusColors.busy,
      icon: Icons.warning_amber_rounded,
    ),
  );
}

/// The widest a [_ProblemBox] grows.
const _problemBoxWidth = 460.0;

/// A home-page problem in a tinted box, so it stands out from the ordinary
/// hints: red for an error, amber for a warning. The text is darkened (or,
/// at night, lightened) toward the page's text for contrast on the tint.
class _ProblemBox extends StatelessWidget {
  const _ProblemBox({
    required this.text,
    required this.color,
    required this.icon,
    this.tooltip = '',
  });

  final String text;
  final Color color;
  final IconData icon;

  /// The core's own words, on hover or a long press.
  final String tooltip;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final ink = Color.lerp(color, dark ? Colors.white : Colors.black, 0.25)!;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: _problemBoxWidth),
        child: Tooltip(
          message: tooltip,
          child: Container(
            padding: const EdgeInsetsDirectional.fromSTEB(12, 9, 14, 9),
            decoration: BoxDecoration(
              color: color.withValues(alpha: dark ? 0.18 : 0.11),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: color.withValues(alpha: 0.4)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(icon, size: 18, color: color),
                const SizedBox(width: 8),
                Flexible(
                  child: Text(
                    text,
                    style: Theme.of(context).textTheme.bodyMedium
                        ?.copyWith(color: ink, fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The accounts page, from the power button without an account; the
/// sign-in form when no page is given.
void _openAccounts(
  BuildContext context,
  CoreController core,
  AppSettings settings,
  VoidCallback? onOpenAccounts,
) {
  if (onOpenAccounts != null) {
    onOpenAccounts();
  } else {
    startAddAccount(context, core, settings);
  }
}

/// Desktop routing adapter. The phone keeps its existing home and controls.
class _DesktopHome extends StatelessWidget {
  const _DesktopHome({
    required this.core,
    required this.status,
    required this.showIp,
    required this.settings,
    this.onOpenServers,
    this.onOpenAccounts,
    this.onOpenSettings,
    this.onOpenLogs,
    this.onOpenConnection,
  });

  final CoreController core;
  final CoreStatus status;
  final bool showIp;
  final AppSettings settings;
  final VoidCallback? onOpenServers;
  final VoidCallback? onOpenAccounts;
  final VoidCallback? onOpenSettings;
  final VoidCallback? onOpenLogs;
  final VoidCallback? onOpenConnection;

  @override
  Widget build(BuildContext context) => _DesktopDashboard(
    core: core,
    status: status,
    showIp: showIp,
    settings: settings,
    onOpenServers: onOpenServers,
    onOpenAccounts: onOpenAccounts,
    onOpenSettings: onOpenSettings,
    onOpenLogs: onOpenLogs,
    onOpenConnection: onOpenConnection,
  );
}

/// A small frosted pill on the home background.
class _HomePill extends StatelessWidget {
  const _HomePill({required this.palette, required this.child});

  final _HomePalette palette;
  final Widget child;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
    decoration: ShapeDecoration(
      color: palette.card,
      shape: StadiumBorder(side: BorderSide(color: palette.cardBorder)),
    ),
    child: child,
  );
}

class _HomePalette {
  const _HomePalette({
    required this.dark,
    required this.top,
    required this.bottom,
    required this.text,
    required this.muted,
    required this.card,
    required this.cardBorder,
    required this.idle,
    required this.connected,
    required this.busy,
    required this.error,
    required this.second,
    required this.third,
    required this.discOff,
    required this.discOffEdge,
  });

  final bool dark;
  final Color top;
  final Color bottom;
  final Color text;
  final Color muted;
  final Color card;
  final Color cardBorder;
  final Color idle;
  final Color connected;
  final Color busy;
  final Color error;

  /// Colors of the background's soft lights, with the state's.
  final Color second;
  final Color third;
  final List<Color> discOff;
  final Color discOffEdge;

  static const night = _HomePalette(
    dark: true,
    top: Color(0xFF010314),
    bottom: Color(0xFF040924),
    text: Colors.white,
    muted: Midnight.muted,
    card: Color(0xFF07112E),
    cardBorder: Color(0xFF2855B8),
    idle: Color(0xFF2189FF),
    connected: Color(0xFF2CE6FF),
    busy: Midnight.busy,
    error: Midnight.error,
    second: Color(0xFF1088FF),
    third: Color(0xFF9D34FF),
    discOff: [Color(0xFF182B68), Color(0xFF071134)],
    discOffEdge: Color(0x5538B6FF),
  );

  /// 1:1 CLXY Cosmic Neon palette for the phone home screen.
  static const phoneNight = _HomePalette(
    dark: true,
    top: Color(0xFF010314),
    bottom: Color(0xFF03071E),
    text: Colors.white,
    muted: ClxyMobileColors.muted,
    card: ClxyMobileColors.panel,
    cardBorder: ClxyMobileColors.lineStrong,
    idle: Color(0xFF2189FF),
    connected: Color(0xFF2CE6FF),
    busy: Midnight.busy,
    error: Midnight.error,
    second: Color(0xFF1088FF),
    third: Color(0xFF9D34FF),
    discOff: [Color(0xFF162862), Color(0xFF060E2E)],
    discOffEdge: Color(0x6638B6FF),
  );

  static const day = _HomePalette(
    dark: false,
    top: Color(0xFFE9EEFF),
    bottom: Color(0xFFF9FAFF),
    text: Color(0xFF151A35),
    muted: Color(0xFF646C92),
    card: Color(0xD9FFFFFF),
    cardBorder: Color(0x12202A66),
    idle: Color(0xFF6A5CF0),
    connected: Color(0xFF0DB37C),
    busy: Color(0xFFF08C00),
    error: Color(0xFFE5484D),
    second: Color(0xFF38B6FF),
    third: Color(0xFFFF7EB6),
    discOff: [Colors.white, Color(0xFFF0F2FF)],
    discOffEdge: Color(0x336A5CF0),
  );
}

/// The phone's home: 3D rotating cosmic globe at the top of the hero,
/// sculpted wave mound + multi-layered neon power button overlapping its base,
/// followed by the 3 stacked glassmorphic cards:
/// 1. Total Traffic (Download / Upload / Total)
/// 2. Server Selector (Flag, Country, Location/Status, 4 Green Signal Bars)
/// 3. Account Data (3D Neon Cyan Cylinder, Used / Limit GB, % Badge, Progress Bar)
class _PhoneHome extends StatelessWidget {
  const _PhoneHome({
    required this.core,
    required this.status,
    required this.showIp,
    required this.settings,
    this.onOpenServers,
    this.onOpenStats,
    this.onOpenAccounts,
    this.onOpenConnection,
  });

  final CoreController core;
  final CoreStatus status;

  /// Settings, Show the public IP.
  final bool showIp;
  final AppSettings settings;
  final VoidCallback? onOpenServers;
  final VoidCallback? onOpenStats;
  final VoidCallback? onOpenAccounts;
  final VoidCallback? onOpenConnection;

  Future<void> _toggle(bool on) async {
    if (status.systemProxySupported) {
      await core.setSystemProxy(on ? 'clear' : 'set');
    } else {
      await core.setOn(!on);
    }
    if (!on) unawaited(core.runTest().catchError((_) {}));
  }

  @override
  Widget build(BuildContext context) {
    final base = Theme.of(context);
    final p = base.brightness == Brightness.dark
        ? _HomePalette.phoneNight
        : _HomePalette.day;
    final page = AnnotatedRegion<SystemUiOverlayStyle>(
      value: (p.dark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark)
          .copyWith(statusBarColor: Colors.transparent),
      child: DecoratedBox(
        decoration: const BoxDecoration(color: Colors.transparent),
        child: LayoutBuilder(
          builder: (context, box) =>
              SingleChildScrollView(child: _content(context, p, box.maxWidth)),
        ),
      ),
    );
    return p.dark ? Theme(data: midnightTheme(base), child: page) : page;
  }

  Widget _content(BuildContext context, _HomePalette p, double width) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final look = PhaseLook.of(l, status);
    final running = status.state.phase == Phase.running;
    final canSwitch = status.systemProxySupported;
    final accountUsage = status.activeAccount?.usage;
    final showDataUsage = accountUsage != null && !accountUsage.unlimited;
    final side = math.max(18.0, (width - 426) / 2);
    final boxSide = math.max(0.0, (width - 2 * side - _problemBoxWidth) / 2);
    Widget problem(Widget box) => Padding(
      padding: EdgeInsets.symmetric(horizontal: boxSide),
      child: box,
    );
    final on = canSwitch
        ? status.systemProxySet
        : status.state.phase != Phase.off;
    final proxy = status.proxy;
    final confirming =
        on && running && !canSwitch && !status.confirmedBy(core.lastTest);
    final failing =
        confirming &&
        ((proxy?.wakeError.isNotEmpty ?? false) || core.lastTest?.ok == false);
    final failure = failing ? failureText(l, proxy, core.lastTest) : null;
    final gaveUp = !on && status.connectFailure.isNotEmpty;
    final connected =
        running && (status.systemProxySet || !canSwitch) && !confirming;
    final accent = !on
        ? p.idle
        : confirming
        ? p.busy
        : switch (status.state.phase) {
            Phase.running => p.connected,
            Phase.error => p.error,
            _ => p.busy,
          };
    final label = confirming ? l.stateConnecting : look.label;
    final place = homeLocation(l, status, connected: connected);
    final mismatch = exitMismatchText(l, status);

    void handlePowerPress() {
      if (status.totalAccounts == 0) {
        _openAccounts(context, core, settings, onOpenAccounts);
      } else {
        runShowingErrors(context, () => _toggle(on));
      }
    }

    return Stack(
      children: [
        // Ambient cosmic neon glows
        Positioned(
          top: -90,
          left: -80,
          child: _Glow(color: accent, size: 340, alpha: p.dark ? 0.20 : 0.18),
        ),
        Positioned(
          top: 150,
          right: -120,
          child: _Glow(color: p.second, size: 320, alpha: p.dark ? 0.15 : 0.14),
        ),
        Positioned(
          bottom: -60,
          left: -60,
          child: _Glow(color: p.third, size: 300, alpha: p.dark ? 0.12 : 0.10),
        ),
        Padding(
          padding: EdgeInsets.fromLTRB(side, 4, side, 22),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1. COSMIC 3D ROTATING GLOBE + WAVE HILL + NEON POWER BUTTON HERO
              SizedBox(
                height: 392,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(30),
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      const ColoredBox(color: Color(0xFF020518)),
                      // Clean Cosmic Clouds & Atmosphere Backdrop
                      Positioned(
                        top: -18,
                        left: -6,
                        right: -6,
                        child: Image.asset(
                          'assets/cosmic_globe_backdrop.png',
                          width: double.infinity,
                          fit: BoxFit.fitWidth,
                          filterQuality: FilterQuality.medium,
                          excludeFromSemantics: true,
                        ),
                      ),
                      // Top & Bottom Smooth Vignette
                      const Positioned.fill(
                        child: IgnorePointer(
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              gradient: LinearGradient(
                                begin: Alignment.topCenter,
                                end: Alignment.bottomCenter,
                                colors: [
                                  Color(0x38010314),
                                  Color(0x00010314),
                                  Color(0x55010314),
                                  Color(0xE6010314),
                                ],
                                stops: [0.0, 0.28, 0.65, 1.0],
                              ),
                            ),
                          ),
                        ),
                      ),

                      // Interactive 3D Rotating Globe in the upper half of the Hero
                      // (Rotates to User's Origin IP when Disconnected, and flashes +
                      // rotates to Destination Server when Connected!)
                      Align(
                        alignment: const Alignment(0, -0.78),
                        child: _PhoneRouteMap(
                          key: connected
                              ? const ValueKey('clxy-phone-route-map')
                              : const ValueKey('clxy-phone-origin-map'),
                          tracker: NetworkLocationScope.of(context),
                          status: status,
                          connected: connected,
                          showIp: showIp,
                          palette: p,
                          busy: core.switchingProxy,
                          onTap: handlePowerPress,
                        ),
                      ),

                      // Sculpted Dark-Blue Wave Mound cradling the Power Button
                      Positioned(
                        left: 0,
                        right: 0,
                        bottom: 0,
                        height: 196,
                        child: IgnorePointer(
                          child: CustomPaint(
                            painter: ClxyWaveMoundPainter(glowColor: accent),
                          ),
                        ),
                      ),

                      // Multi-Layered Neon Power Button ("Tap to Disconnect" / "Tap to Connect")
                      Align(
                        alignment: const Alignment(0, 0.88),
                        child: _PowerOrb(
                          palette: p,
                          on: on,
                          connected: connected,
                          accent: accent,
                          busy: core.switchingProxy,
                          tooltip: on
                              ? l.tapToDisconnectPhone
                              : l.tapToConnectPhone,
                          onPressed: handlePowerPress,
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 10),

              // Problem / Warning banners when applicable
              if (failing || (status.noNetwork && on) || gaveUp)
                problem(
                  _ProblemBox(
                    text: status.noNetwork && on
                        ? l.noNetwork
                        : failure != null
                        ? failure.text
                        : failureCodeText(l, status.connectFailure),
                    color: p.error,
                    icon: Icons.error_outline_rounded,
                    tooltip: status.noNetwork ? '' : failure?.detail ?? '',
                  ),
                )
              else if ((on && !connected) || look.hint.isNotEmpty)
                Tooltip(
                  message: status.state.phase == Phase.error
                      ? neutralizeProviderIdentifiers(status.state.message)
                      : '',
                  child: Text(
                    look.hint.isNotEmpty ? look.hint : l.tapToDisconnectPhone,
                    textAlign: TextAlign.center,
                    style: theme.textTheme.bodyMedium?.copyWith(color: p.muted),
                  ),
                ),
              if (failure?.code == Failure.unreachable &&
                  onOpenConnection != null)
                _ConnectionSettingsButton(onPressed: onOpenConnection!),
              if (isLocationRefused(failure?.code ?? '') ||
                  (gaveUp && isLocationRefused(status.connectFailure)))
                _RecommendedLocationButton(core: core),
              if (connected && mismatch != null)
                problem(_ExitMismatchNote(text: mismatch)),
              if (canSwitch &&
                  status.systemProxySet &&
                  status.systemProxyTakenBy != null)
                problem(
                  _SystemProxyTakenNote(
                    core: core,
                    by: status.systemProxyTakenBy!,
                  ),
                ),
              if (status.state.proxyPortBlocked) ...[
                const SizedBox(height: 10),
                Center(
                  child: FilledButton.tonalIcon(
                    onPressed: () async {
                      final port = await showPortDialog(
                        context,
                        taken: status.state.address,
                      );
                      if (port != null && context.mounted) {
                        await runShowingErrors(
                          context,
                          () => core.setProxyPort(port),
                        );
                      }
                    },
                    icon: const Icon(Icons.settings_ethernet_rounded),
                    label: Text(l.changePort),
                  ),
                ),
              ],
              if (running &&
                  proxy != null &&
                  !canSwitch &&
                  sharedWithLan(proxy.listen)) ...[
                const SizedBox(height: 8),
                Center(child: _LanChip(port: portOf(proxy.listen) ?? 10810)),
              ],

              const SizedBox(height: 10),
              _UpdateNotice(settings: settings, palette: p),
              _Notices(notices: homeNotices(status.notices)),
              _LowQuotaNotice(
                core: core,
                status: status,
                settings: settings,
                connected: connected,
                palette: p,
              ),

              // 2. CARD 1: TOTAL TRAFFIC (Matches top glass card in screenshot)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 6),
                child: _PhoneLiveData(
                  status: status,
                  palette: p,
                  onTap: onOpenStats,
                ),
              ),

              const SizedBox(height: 12),

              // 3. CARD 2: SERVER SELECTOR (Flag + Country + Location + 4 Green Bars)
              _HomeSummaryCard(
                palette: p,
                status: label,
                place: place,
                accent: accent,
                onTap: onOpenServers,
              ),

              // 4. CARD 3: ACCOUNT DATA (3D Neon Cyan Cylinder + Used / Total GB + % Badge)
              if (showDataUsage) ...[
                const SizedBox(height: 12),
                _HomeData(status: status, palette: p, onTap: onOpenAccounts),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// A soft round light for the background.
class _Glow extends StatelessWidget {
  const _Glow({required this.color, required this.size, required this.alpha});

  final Color color;
  final double size;
  final double alpha;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: AnimatedContainer(
      duration: const Duration(milliseconds: 600),
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: RadialGradient(
          colors: [
            color.withValues(alpha: alpha),
            color.withValues(alpha: 0),
          ],
        ),
      ),
    ),
  );
}

/// Card 2 in the CLXY screenshot: Dual-tone Cyan-to-Purple border card showing
/// the selected Country Flag, Country Name, Location caption + Connection status,
/// dropdown chevron, vertical divider, and 4 glowing neon-green signal bars.
class _HomeSummaryCard extends StatelessWidget {
  const _HomeSummaryCard({
    required this.palette,
    required this.status,
    required this.place,
    required this.accent,
    this.onTap,
  });

  final _HomePalette palette;
  final String status;
  final HomePlace place;
  final Color accent;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    // "Connected to" already communicates this state; avoid repeating it.
    final showConnectionStatus =
        status.isNotEmpty && place.caption != l.connectedTo;
    return ClxyDualToneCard(
      onTap: onTap,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 50),
        child: Row(
          children: [
            // Left Flag Badge
            CountryFlag(code: place.flag, width: 42),
            const SizedBox(width: 14),

            // Country Name + Caption & Connection Status
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    place.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: const Color(0xFFF1F6FF),
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Wrap(
                    spacing: 6,
                    runSpacing: 2,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Text(
                        place.caption,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: const Color(0xFF7F97C7),
                          fontSize: 12.5,
                        ),
                      ),
                      if (showConnectionStatus) ...[
                        const Text(
                          '·',
                          style: TextStyle(color: Color(0xFF5C78B0)),
                        ),
                        Text(
                          status,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: accent,
                            fontSize: 12.5,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                      // Keep sectionConnection semantics/text accessible
                      Offstage(child: Text(l.sectionConnection)),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(width: 8),
            const Icon(
              Icons.keyboard_arrow_down_rounded,
              color: Color(0xFFB0C4EA),
              size: 22,
            ),
            Container(
              width: 1,
              height: 32,
              margin: const EdgeInsets.symmetric(horizontal: 12),
              color: const Color(0x4D5577C2),
            ),

            // 4 Ascending Neon Green Signal Bars
            Row(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                for (final h in const [7.0, 11.5, 16.0, 20.5])
                  Container(
                    width: 3.8,
                    height: h,
                    margin: const EdgeInsets.only(left: 3),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(99),
                      color: ClxyMobileColors.green,
                      boxShadow: [
                        BoxShadow(
                          color: ClxyMobileColors.green.withValues(alpha: 0.45),
                          blurRadius: 6,
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Card 1 in the CLXY screenshot: Pill-rounded glassmorphic Total Traffic card
/// with opposing cyan/blue arrows, centered "Total Traffic" title,
/// "↓ 318.9 MB | ↑ 245.7 MB" row, horizontal divider, and "Total: 564.6 MB".
class _PhoneLiveData extends StatelessWidget {
  const _PhoneLiveData({
    required this.status,
    required this.palette,
    this.onTap,
  });

  final CoreStatus status;
  final _HomePalette palette;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final traffic = status.traffic;
    final total = traffic == null ? 0 : traffic.up + traffic.down;

    return ClxyTrafficGlassCard(
      onTap: onTap,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Top-left opposing cyan/blue arrows icon
          const PositionedDirectional(
            start: 0,
            top: 0,
            child: Icon(
              Icons.swap_vert_rounded,
              color: Color(0xFF47C8FF),
              size: 20,
            ),
          ),
          // Right chevron
          const PositionedDirectional(
            end: 0,
            child: Icon(
              Icons.chevron_right_rounded,
              color: Color(0xFF9AB0DC),
              size: 20,
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  l.totalTraffic,
                  textAlign: TextAlign.center,
                  style: theme.textTheme.titleSmall?.copyWith(
                    color: const Color(0xFFEFF5FF),
                    fontSize: 14.5,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: _PhoneTrafficMetric(
                        label: l.download,
                        value: traffic == null
                            ? '—'
                            : formatBytes(traffic.down),
                        color: const Color(0xFF36C2FF),
                        icon: Icons.arrow_downward_rounded,
                      ),
                    ),
                    Container(
                      width: 1,
                      height: 18,
                      margin: const EdgeInsets.symmetric(horizontal: 10),
                      color: const Color(0x595C82D6),
                    ),
                    Expanded(
                      child: _PhoneTrafficMetric(
                        label: l.upload,
                        value: traffic == null ? '—' : formatBytes(traffic.up),
                        color: const Color(0xFF36C2FF),
                        icon: Icons.arrow_upward_rounded,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Container(
                  height: 1,
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      colors: [
                        Colors.transparent,
                        Color(0x665C85DF),
                        Colors.transparent,
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 5),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      Icons.storage_rounded,
                      size: 13,
                      color: Color(0xFF8EACDF),
                    ),
                    const SizedBox(width: 5),
                    Flexible(
                      child: Text(
                        traffic == null
                            ? 'Total: —'
                            : 'Total: ${formatBytes(total)}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: const Color(0xFFD8E5FF),
                          fontSize: 12.5,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _PhoneTrafficMetric extends StatelessWidget {
  const _PhoneTrafficMetric({
    required this.label,
    required this.value,
    required this.color,
    required this.icon,
  });

  final String label;
  final String value;
  final Color color;
  final IconData icon;

  @override
  Widget build(BuildContext context) => Semantics(
    label: label,
    child: FittedBox(
      fit: BoxFit.scaleDown,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color, size: 16),
          const SizedBox(width: 4),
          Text(
            value,
            maxLines: 1,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w700,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    ),
  );
}

/// Interactive 3D Rotating Globe in the upper half of the phone Hero.
/// Always stays mounted so it rotates smoothly onto the user's Origin IP (e.g. Iran)
/// when disconnected, and flashes + rotates onto the Exit Server (e.g. USA) when connected.
class _PhoneRouteMap extends StatelessWidget {
  const _PhoneRouteMap({
    super.key,
    required this.tracker,
    required this.status,
    required this.connected,
    required this.showIp,
    required this.palette,
    required this.busy,
    required this.onTap,
  });

  final NetworkLocationTracker tracker;
  final CoreStatus status;
  final bool connected;
  final bool showIp;
  final _HomePalette palette;
  final bool busy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final p = palette;
    final l = AppLocalizations.of(context);
    final exit = status.exit;
    final exitIps = <String>{
      exit?.ipv4 ?? '',
      exit?.ipv6 ?? '',
      status.lastTest?.exitIpv4 ?? '',
      status.lastTest?.exitIpv6 ?? '',
    }..removeWhere((ip) => ip.trim().isEmpty);
    final origin = tracker.originLocation;
    final originMatchesExit =
        origin != null &&
        exitIps.any(
          (ip) => ip.trim().toLowerCase() == origin.ip.trim().toLowerCase(),
        );
    final safeOrigin = originMatchesExit ? null : origin;
    final exitIp = [
      exit?.ipv4 ?? '',
      exit?.ipv6 ?? '',
      status.lastTest?.exitIpv4 ?? '',
      status.lastTest?.exitIpv6 ?? '',
    ].firstWhere((ip) => ip.isNotEmpty, orElse: () => '');
    final exitCode = exit?.countryCode ?? '';
    final startLabel = showIp
        ? (safeOrigin?.ip.isNotEmpty == true ? safeOrigin!.ip : '—')
        : (safeOrigin?.countryCode.toUpperCase() ?? '—');
    final endLabel = showIp
        ? (exitIp.isNotEmpty ? exitIp : '—')
        : (exitCode.isNotEmpty ? exitCode.toUpperCase() : '—');
    final originName = safeOrigin?.countryName.trim().isNotEmpty == true
        ? safeOrigin!.countryName.trim()
        : safeOrigin?.countryCode.toUpperCase() ?? '';
    final startDescription = safeOrigin == null
        ? (tracker.originLocationLoading
              ? 'Checking origin'
              : 'Origin location unavailable')
        : '$originName${showIp ? ' (${safeOrigin.ip})' : ''}';
    final exitName = exit?.countryName ?? status.lastTest?.exitCountry ?? '';
    final endDescription = exitName.isEmpty
        ? (exitIp.isEmpty ? 'Exit location unavailable' : exitIp)
        : '$exitName${showIp && exitIp.isNotEmpty ? ' ($exitIp)' : ''}';
    final routeDescription =
        'Origin: $startDescription → Exit: $endDescription';
    const size = 218.0;
    const shape = CircleBorder();

    return SizedBox.square(
      dimension: 236,
      child: Center(
        child: Container(
          key: const ValueKey('clxy-phone-route-globe-circle'),
          width: size,
          height: size,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: const Color(0xFF1088FF).withValues(alpha: 0.25),
                blurRadius: 32,
                spreadRadius: 2,
              ),
            ],
          ),
          child: Material(
            color: Colors.transparent,
            shape: shape,
            clipBehavior: Clip.antiAlias,
            child: Stack(
              fit: StackFit.expand,
              children: [
                Positioned.fill(
                  child: InkWell(
                    onTap: busy ? null : onTap,
                    customBorder: shape,
                    child: ConnectionGlobe(
                      tracker: tracker,
                      exit: exit,
                      connected: connected,
                      compact: true,
                      showIp: showIp,
                    ),
                  ),
                ),
                if (connected)
                  Positioned(
                    left: 34,
                    right: 34,
                    bottom: 54,
                    child: IgnorePointer(
                      child: Tooltip(
                        message: routeDescription,
                        child: Container(
                          key: const ValueKey('clxy-phone-route-labels'),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xD9051032),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(
                              color: ClxyMobileColors.cyan.withValues(
                                alpha: 0.45,
                              ),
                            ),
                          ),
                          child: Directionality(
                            textDirection: TextDirection.ltr,
                            child: Row(
                              children: [
                                Expanded(
                                  child: FittedBox(
                                    fit: BoxFit.scaleDown,
                                    alignment: Alignment.centerLeft,
                                    child: Text(
                                      startLabel,
                                      maxLines: 1,
                                      style: Theme.of(context)
                                          .textTheme
                                          .labelSmall
                                          ?.copyWith(
                                            color: safeOrigin == null
                                                ? p.muted
                                                : const Color(0xFF00FFA3),
                                            fontWeight: FontWeight.w700,
                                          ),
                                    ),
                                  ),
                                ),
                                Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 6,
                                  ),
                                  child: Icon(
                                    Icons.arrow_forward_rounded,
                                    size: 14,
                                    color: p.connected,
                                  ),
                                ),
                                Expanded(
                                  child: FittedBox(
                                    fit: BoxFit.scaleDown,
                                    alignment: Alignment.centerRight,
                                    child: Text(
                                      endLabel,
                                      maxLines: 1,
                                      style: Theme.of(context)
                                          .textTheme
                                          .labelSmall
                                          ?.copyWith(
                                            color: p.connected,
                                            fontWeight: FontWeight.w700,
                                          ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                if (!busy &&
                    (tracker.originLocationError != null ||
                        originMatchesExit ||
                        (origin == null && !tracker.originLocationLoading)))
                  Positioned(
                    top: 18,
                    right: 18,
                    child: Material(
                      color: const Color(0xE607153E),
                      shape: const CircleBorder(),
                      child: Tooltip(
                        message: l.retry,
                        child: IconButton(
                          onPressed: () => tracker.requestOriginLocation(
                            retry: true,
                            excludedIps: exitIps,
                          ),
                          icon: Icon(
                            Icons.refresh_rounded,
                            color: p.second,
                            size: 18,
                          ),
                          iconSize: 18,
                          visualDensity: VisualDensity.compact,
                          constraints: const BoxConstraints.tightFor(
                            width: 36,
                            height: 36,
                          ),
                          padding: EdgeInsets.zero,
                          tooltip: l.retry,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Multi-Layered Neon Power Button matching the CLXY screenshot:
/// - Outer dark-glass bezel ring (192px)
/// - High-luminance Electric Cyan-to-Blue Neon Ring (158px) with flash burst
/// - Inner deep radial navy convex sphere with glowing white Power Icon
///   and "Tap to Disconnect" / "Tap to Connect" subtitle inside the circle.
class _PowerOrb extends StatefulWidget {
  const _PowerOrb({
    required this.palette,
    required this.on,
    required this.connected,
    required this.accent,
    required this.busy,
    required this.tooltip,
    required this.onPressed,
  });

  final _HomePalette palette;
  final bool on;
  final bool connected;
  final Color accent;
  final bool busy;
  final String tooltip;
  final VoidCallback onPressed;

  @override
  State<_PowerOrb> createState() => _PowerOrbState();
}

class _PowerOrbState extends State<_PowerOrb>
    with SingleTickerProviderStateMixin {
  late final AnimationController _flashController = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 750),
  );

  @override
  void didUpdateWidget(covariant _PowerOrb oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.on != oldWidget.on || widget.connected != oldWidget.connected) {
      final reduced = MediaQuery.maybeOf(context)?.disableAnimations ?? false;
      if (!reduced) {
        _flashController.forward(from: 0.0);
      }
    }
  }

  @override
  void dispose() {
    _flashController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const outerSize = 192.0;
    const neonRingSize = 158.0;
    const innerCoreSize = 136.0;
    final active = widget.on;

    return Tooltip(
      message: widget.tooltip,
      child: Semantics(
        button: true,
        label: widget.tooltip,
        child: AnimatedBuilder(
          animation: _flashController,
          builder: (context, _) {
            final flash = math.sin(_flashController.value * math.pi);
            return SizedBox.square(
              dimension: outerSize,
              child: Stack(
                alignment: Alignment.center,
                clipBehavior: Clip.none,
                children: [
                  // Flash burst shockwave ring when connecting/toggling
                  if (flash > 0.01)
                    IgnorePointer(
                      child: Container(
                        width: outerSize + flash * 36,
                        height: outerSize + flash * 36,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: ClxyMobileColors.cyanBright.withValues(
                              alpha: (1.0 - _flashController.value) * 0.85,
                            ),
                            width: 3.0 * flash,
                          ),
                        ),
                      ),
                    ),

                  // 1. Outer Dark Bezel Ring
                  Container(
                    width: outerSize,
                    height: outerSize,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: const RadialGradient(
                        center: Alignment(0, -0.5),
                        colors: [
                          Color(0xD9102D7D),
                          Color(0xF5040B2A),
                          Color(0xFA020518),
                        ],
                      ),
                      border: Border.all(
                        color: active
                            ? const Color(0x70377DFF)
                            : const Color(0x45475569),
                        width: 1.5,
                      ),
                      boxShadow: [
                        const BoxShadow(
                          color: Color(0xE6000416),
                          blurRadius: 32,
                          offset: Offset(0, 10),
                        ),
                        BoxShadow(
                          color:
                              (active
                                      ? const Color(0xFF1088FF)
                                      : const Color(0xFF00FFA3))
                                  .withValues(alpha: 0.28 + flash * 0.35),
                          blurRadius: 24 + flash * 22,
                        ),
                      ],
                    ),
                  ),

                  // 2. Electric Cyan-to-Blue Neon Ring
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 360),
                    width: neonRingSize,
                    height: neonRingSize,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: active
                            ? const [
                                Color(0xFF4AE0FF),
                                Color(0xFF1088FF),
                                Color(0xFF0046EB),
                                Color(0xFF1994FF),
                              ]
                            : const [
                                Color(0xFF38BDF8),
                                Color(0xFF1E3A8A),
                                Color(0xFF0F172A),
                              ],
                      ),
                      boxShadow: [
                        BoxShadow(
                          color:
                              (active
                                      ? const Color(0xFF1298FF)
                                      : const Color(0xFF38BDF8))
                                  .withValues(
                                    alpha:
                                        (active ? 0.82 : 0.38) + flash * 0.18,
                                  ),
                          blurRadius: (active ? 24 : 14) + flash * 20,
                        ),
                      ],
                    ),
                  ),

                  if (widget.busy)
                    SizedBox.square(
                      dimension: neonRingSize + 8,
                      child: CircularProgressIndicator(
                        strokeWidth: 2.2,
                        color: widget.accent,
                      ),
                    ),

                  // 3. Inner Deep Radial Navy Core Sphere + Power Icon + Label
                  Container(
                    width: innerCoreSize,
                    height: innerCoreSize,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: RadialGradient(
                        center: const Alignment(0, -0.25),
                        colors: active
                            ? const [
                                Color(0xFF102A82),
                                Color(0xFF071346),
                                Color(0xFF030724),
                                Color(0xFF02051B),
                              ]
                            : const [
                                Color(0xFF162447),
                                Color(0xFF091128),
                                Color(0xFF030614),
                              ],
                      ),
                    ),
                    child: Material(
                      type: MaterialType.transparency,
                      shape: const CircleBorder(),
                      clipBehavior: Clip.antiAlias,
                      child: InkWell(
                        onTap: widget.busy ? null : widget.onPressed,
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 10),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.power_settings_new_rounded,
                                size: 50,
                                color: Colors.white,
                                shadows: [
                                  Shadow(
                                    color: const Color(0xFF82D2FF)
                                        .withValues(alpha: 0.85),
                                    blurRadius: 14 + flash * 12,
                                  ),
                                ],
                              ),
                              const SizedBox(height: 4),
                              FittedBox(
                                fit: BoxFit.scaleDown,
                                child: Text(
                                  widget.tooltip,
                                  maxLines: 1,
                                  style: const TextStyle(
                                    fontSize: 11.5,
                                    fontWeight: FontWeight.w500,
                                    color: Color(0xFFBFD2F8),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

/// Card 3 in the CLXY screenshot: Dual-tone Cyan-to-Purple border card with
/// 3D Glowing Neon Cyan Database Cylinder on the left, vertical divider,
/// "Data used" ("Account Data"), the used amount followed by an infinity icon,
/// "73%" glowing pill badge on the right, and neon cyan-aqua progress bar below.
class _HomeData extends StatelessWidget {
  const _HomeData({required this.status, required this.palette, this.onTap});

  final CoreStatus status;
  final _HomePalette palette;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final p = palette;
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: const Color(0xFF8FA6D8),
      fontSize: 12.5,
    );
    final a = status.activeAccount;
    final usage = a?.usage;
    if (a == null || usage == null || usage.unlimited) {
      return ClxyDualToneCard(
        onTap: onTap,
        child: Row(
          children: [
            Icon(
              a == null
                  ? Icons.person_add_alt_rounded
                  : Icons.all_inclusive_rounded,
              color: p.muted,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Text(
                a == null
                    ? l.noActiveAccount
                    : usage == null
                    ? l.quotaNotRead
                    : l.unlimited,
                style: theme.textTheme.titleMedium?.copyWith(color: p.text),
              ),
            ),
            Icon(Icons.chevron_right_rounded, color: p.muted),
          ],
        ),
      );
    }

    final used = usage.usedBytes.clamp(0, usage.limitBytes);
    final fraction = usage.limitBytes == 0 ? 0.0 : used / usage.limitBytes;
    final percentText = ltr('${(fraction * 100).round()}%');

    return ClxyDualToneCard(
      onTap: onTap,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 11),
      child: Row(
        children: [
          // Left: 3D Glowing Neon Cyan Database Stack
          const ClxyDatabaseCylinderIcon(width: 38, height: 42),
          Container(
            width: 1,
            height: 42,
            margin: const EdgeInsets.symmetric(horizontal: 14),
            color: const Color(0x4D5577C2),
          ),

          // Right: Data Used Title, GB Values, Percentage Pill, and Progress Bar
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(l.dataUsedTitle, style: muted),
                          const SizedBox(height: 1),
                          Wrap(
                            crossAxisAlignment: WrapCrossAlignment.end,
                            spacing: 4,
                            children: [
                              Text(
                                formatBytes(used),
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.w800,
                                  fontSize: 18.5,
                                  color: ClxyMobileColors.cyan,
                                ),
                              ),
                              Text(
                                '/',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: const Color(0xFFD4E2FF),
                                  fontSize: 12.5,
                                ),
                              ),
                              Icon(
                                Icons.all_inclusive_rounded,
                                size: 17,
                                color: const Color(0xFFD4E2FF),
                                semanticLabel: l.unlimited,
                              ),
                              if (usage.resetAt != null)
                                Text(
                                  '· ${l.resets(formatDate(l, usage.resetAt!))}',
                                  style: theme.textTheme.bodySmall?.copyWith(
                                    color: const Color(0xFFD4E2FF),
                                    fontSize: 12.5,
                                  ),
                                ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    // Glowing Cyan Percentage Pill Badge (e.g. 73% / 40%)
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 3.5,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xCC071F63),
                        borderRadius: BorderRadius.circular(99),
                        border: Border.all(
                          color: const Color(0xA619C8FF),
                          width: 1.2,
                        ),
                        boxShadow: const [
                          BoxShadow(color: Color(0x3319C8FF), blurRadius: 10),
                        ],
                      ),
                      child: Text(
                        percentText,
                        style: theme.textTheme.labelMedium?.copyWith(
                          fontWeight: FontWeight.w700,
                          fontSize: 13.5,
                          color: ClxyMobileColors.cyan,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 7),
                // Neon Cyan-to-Aqua Progress Bar
                Container(
                  width: double.infinity,
                  height: 6.5,
                  decoration: BoxDecoration(
                    color: const Color(0xFF131D52),
                    borderRadius: BorderRadius.circular(99),
                  ),
                  alignment: AlignmentDirectional.centerStart,
                  child: FractionallySizedBox(
                    widthFactor: fraction.clamp(0.0, 1.0),
                    child: Container(
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(99),
                        gradient: const LinearGradient(
                          colors: [
                            Color(0xFF18B2FF),
                            Color(0xFF2CE6FF),
                            Color(0xFF52F7FF),
                          ],
                        ),
                        boxShadow: const [
                          BoxShadow(
                            color: ClxyMobileColors.cyan,
                            blurRadius: 8,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// The proxy's address on the local network, for other devices; a tap
/// copies it.
class _LanChip extends StatefulWidget {
  const _LanChip({required this.port});

  final int port;

  @override
  State<_LanChip> createState() => _LanChipState();
}

class _LanChipState extends State<_LanChip> {
  String? _ip;

  @override
  void initState() {
    super.initState();
    lanAddress().then((ip) {
      if (mounted) setState(() => _ip = ip);
    });
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final ip = _ip;
    if (ip == null) return const SizedBox.shrink();
    final address = '$ip:${widget.port}';
    return ActionChip(
      avatar: const Icon(Icons.lan_outlined, size: 18),
      label: Text(l.lanAddress(ltr(address))),
      onPressed: () {
        Clipboard.setData(ClipboardData(text: address));
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l.copied(address)),
            behavior: SnackBarBehavior.floating,
            duration: const Duration(seconds: 2),
          ),
        );
      },
    );
  }
}

/// Refusals dismissed in this session, by [Notice.key]; a newer refusal of
/// the same kind shows again.
final _dismissedNotices = <String>{};

/// Notes on requests the proxy refused in the last minutes: a port
/// Mozilla's proxy does not connect to, or UDP.
class _Notices extends StatefulWidget {
  const _Notices({required this.notices});

  final List<Notice> notices;

  @override
  State<_Notices> createState() => _NoticesState();
}

class _NoticesState extends State<_Notices> {
  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final shown = [
      for (final n in widget.notices)
        if (!_dismissedNotices.contains(n.key) &&
            (n.kind == 'port' || n.kind == 'command'))
          n,
    ];
    if (shown.isEmpty) return const SizedBox.shrink();
    return Column(
      children: [
        for (final n in shown)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Container(
              padding: const EdgeInsetsDirectional.fromSTEB(14, 10, 6, 10),
              decoration: BoxDecoration(
                color: StatusColors.busy.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: StatusColors.busy.withValues(alpha: 0.4),
                ),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Padding(
                    padding: EdgeInsets.only(top: 2),
                    child: Icon(
                      Icons.info_outline_rounded,
                      color: StatusColors.busy,
                      size: 18,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          n.kind == 'port'
                              ? l.noticePort(
                                  neutralizeProviderIdentifiers(n.detail),
                                )
                              : l.noticeCommand,
                          style: theme.textTheme.bodySmall,
                        ),
                        if (n.last != null) ...[
                          const SizedBox(height: 2),
                          Text(
                            [
                              l.noticeTimes(n.count, formatAgo(l, n.last!)),
                              if (n.hosts.isNotEmpty)
                                neutralizeProviderIdentifiers(
                                  n.hosts.join(', '),
                                ),
                            ].join(' · '),
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: l.dismiss,
                    visualDensity: VisualDensity.compact,
                    icon: const Icon(Icons.close_rounded, size: 16),
                    onPressed: () =>
                        setState(() => _dismissedNotices.add(n.key)),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

/// A newer version on GitHub: its number and a button to its page; closed,
/// it stays away until a later version.
class _UpdateNotice extends StatelessWidget {
  const _UpdateNotice({required this.settings, required this.palette});

  final AppSettings settings;
  final _HomePalette palette;

  @override
  Widget build(BuildContext context) => ValueListenableBuilder(
    valueListenable: Updates.available,
    builder: (context, release, _) {
      if (release == null || release.version == settings.dismissedUpdate) {
        return const SizedBox.shrink();
      }
      final l = AppLocalizations.of(context);
      final theme = Theme.of(context);
      final p = palette;
      return Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Material(
          color: p.connected.withValues(alpha: p.dark ? 0.16 : 0.12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
            side: BorderSide(color: p.connected.withValues(alpha: 0.4)),
          ),
          child: Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(14, 6, 6, 6),
            child: Row(
              children: [
                Icon(Icons.system_update_rounded, color: p.connected),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    l.updateAvailable(release.version),
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: p.text,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                if (release.url.isNotEmpty)
                  FilledButton.icon(
                    style: FilledButton.styleFrom(
                      backgroundColor: p.connected,
                      foregroundColor: Colors.white,
                      visualDensity: VisualDensity.compact,
                      textStyle: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    onPressed: () => openDownload(release),
                    icon: const Icon(Icons.download_rounded, size: 18),
                    label: Text(l.download),
                  ),
                IconButton(
                  tooltip: l.dismiss,
                  visualDensity: VisualDensity.compact,
                  icon: Icon(Icons.close_rounded, size: 18, color: p.muted),
                  onPressed: () =>
                      settings.update(dismissedUpdate: release.version),
                ),
              ],
            ),
          ),
        ),
      );
    },
  );
}

/// When the user connects and the active account is nearly used up with
/// no other account to take over: adding an account gives another month's
/// data. Shown once per connection, for a minute; closed, it stays away for
/// a day.
class _LowQuotaNotice extends StatefulWidget {
  const _LowQuotaNotice({
    required this.core,
    required this.status,
    required this.settings,
    required this.connected,
    required this.palette,
  });

  final CoreController core;
  final CoreStatus status;
  final AppSettings settings;
  final bool connected;
  final _HomePalette palette;

  @override
  State<_LowQuotaNotice> createState() => _LowQuotaNoticeState();
}

class _LowQuotaNoticeState extends State<_LowQuotaNotice> {
  /// Below this much left, the account is running out.
  static const _left = 5 << 30;
  static const _showFor = Duration(seconds: 60);
  static const _quiet = Duration(hours: 24);

  /// The connection it was shown for (the proxy's start), and whether its
  /// minute is over: once per connection, also when the home page is
  /// built again meanwhile.
  static DateTime? _shownFor;
  static bool _started = false;
  static bool _over = false;
  static Timer? _minute;
  static final _changed = ValueNotifier(0);

  @override
  void initState() {
    super.initState();
    _changed.addListener(_rebuild);
  }

  @override
  void dispose() {
    _changed.removeListener(_rebuild);
    super.dispose();
  }

  void _rebuild() {
    if (mounted) setState(() {});
  }

  bool get _due {
    final active = widget.status.activeAccount;
    final usage = active?.usage;
    if (!widget.connected || active == null || usage == null) return false;
    if (usage.unlimited || usage.limitBytes == 0) return false;
    if (usage.remainingBytes >= _left) return false;
    final others = widget.core.accounts?.accounts ?? const <Account>[];
    final spare = others.any(
      (a) =>
          a.id != active.id &&
          a.enabled &&
          a.status == AccountStatus.ready &&
          (a.usage?.unlimited == true ||
              (a.usage?.remainingBytes ?? 0) >= _left),
    );
    if (spare) return false;
    final closed = widget.settings.lowQuotaDismissedAt;
    return closed == null || DateTime.now().difference(closed) >= _quiet;
  }

  bool _visible() {
    if (!_due) return false;
    final connection = widget.status.proxy?.startedAt;
    if (!_started || _shownFor != connection) {
      _started = true;
      _shownFor = connection;
      _over = false;
      _minute?.cancel();
      _minute = Timer(_showFor, () {
        _over = true;
        _changed.value++;
      });
    }
    return !_over;
  }

  @override
  Widget build(BuildContext context) {
    if (!_visible()) return const SizedBox.shrink();
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final p = widget.palette;
    final usage = widget.status.activeAccount!.usage!;
    String rounded(int bytes) => formatBytes(bytes).replaceAll('.0 ', ' ');
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: StatusColors.busy.withValues(alpha: p.dark ? 0.16 : 0.12),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(18),
          side: BorderSide(color: StatusColors.busy.withValues(alpha: 0.4)),
        ),
        child: Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(14, 10, 6, 6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Padding(
                    padding: EdgeInsets.only(top: 2),
                    child: Icon(
                      Icons.data_usage_rounded,
                      color: StatusColors.busy,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      l.lowQuotaNotice(
                        rounded(usage.remainingBytes),
                        rounded(usage.limitBytes),
                      ),
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: p.text,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: l.close,
                    visualDensity: VisualDensity.compact,
                    icon: Icon(Icons.close_rounded, size: 18, color: p.muted),
                    onPressed: () {
                      widget.settings.update(
                        lowQuotaDismissedAt: DateTime.now(),
                      );
                      setState(() {});
                    },
                  ),
                ],
              ),
              Align(
                alignment: AlignmentDirectional.centerEnd,
                child: TextButton.icon(
                  onPressed: () =>
                      startAddAccount(context, widget.core, widget.settings),
                  icon: const Icon(Icons.person_add_alt_1_rounded, size: 18),
                  label: Text(l.addAccount),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
