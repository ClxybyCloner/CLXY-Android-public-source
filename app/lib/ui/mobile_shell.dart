import 'dart:io';

import 'package:flutter/material.dart';

import '../app_settings.dart';
import '../core/android_core.dart';
import '../core/core_controller.dart';
import '../l10n/app_localizations.dart';
import 'about_page.dart';
import 'accounts_page.dart';
import 'logs_page.dart';
import 'overview_page.dart';
import 'proxy_profile_copy.dart';
import 'proxy_profile_page.dart';
import 'servers_page.dart';
import 'settings_page.dart';
import 'stats_page.dart';
import 'widgets.dart';
import 'mobile_visuals.dart';

/// The phone layout: Home, Servers, Statistics, and More with 1:1 CLXY
/// cosmic neon header, floating spotlight bottom dock, and full-screen animated routes.
class MobileShell extends StatefulWidget {
  const MobileShell({
    super.key,
    required this.core,
    required this.settings,
    required this.banner,
    this.noCore,
  });

  final CoreController core;
  final AppSettings settings;

  /// Shown above the page when the core stopped answering.
  final Widget? banner;

  /// Shown in place of the pages until the core first answers.
  final Widget? noCore;

  @override
  State<MobileShell> createState() => _MobileShellState();
}

class _MobileShellState extends State<MobileShell> {
  int _tab = 0;
  int _lastUpdateOpenRequest = 0;
  bool _aboutOpenQueued = false;

  @override
  void initState() {
    super.initState();
    if (Platform.isAndroid) {
      AndroidCore.updateOpenRequests.addListener(_updateOpenRequested);
      _lastUpdateOpenRequest = AndroidCore.updateOpenRequests.value;
      if (_lastUpdateOpenRequest > 0) _queueAboutPage();
    }
  }

  @override
  void dispose() {
    if (Platform.isAndroid) {
      AndroidCore.updateOpenRequests.removeListener(_updateOpenRequested);
    }
    super.dispose();
  }

  void _updateOpenRequested() {
    final request = AndroidCore.updateOpenRequests.value;
    if (request <= _lastUpdateOpenRequest) return;
    _lastUpdateOpenRequest = request;
    _queueAboutPage();
  }

  void _queueAboutPage() {
    if (_aboutOpenQueued) return;
    _aboutOpenQueued = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _aboutOpenQueued = false;
      if (!mounted) return;
      final l = AppLocalizations.of(context);
      _push(l.navAbout, () => AboutPage(core: widget.core));
    });
  }

  /// Opens [page] as a full-screen cosmic glassmorphic route under [title]
  /// with a smooth slide + fade + scale transition.
  void _push(
    String title,
    Widget Function() page, {
    PreferredSizeWidget? appBar,
  }) {
    Navigator.of(context).push(
      PageRouteBuilder<void>(
        transitionDuration: const Duration(milliseconds: 320),
        reverseTransitionDuration: const Duration(milliseconds: 260),
        pageBuilder: (context, animation, secondaryAnimation) =>
            ListenableBuilder(
              listenable: widget.core,
              builder: (context, _) => Scaffold(
                backgroundColor: ClxyMobileColors.background,
                appBar:
                    appBar ??
                    AppBar(
                      backgroundColor: const Color(0xD9050C24),
                      surfaceTintColor: Colors.transparent,
                      title: Text(
                        title,
                        style: const TextStyle(
                          color: ClxyMobileColors.text,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                body: Stack(
                  fit: StackFit.expand,
                  children: [
                    Positioned.fill(
                      child: IgnorePointer(
                        child: Image.asset(
                          'assets/dashboard-mobile-wallpaper-extra-blur.jpg',
                          fit: BoxFit.cover,
                          filterQuality: FilterQuality.medium,
                          excludeFromSemantics: true,
                        ),
                      ),
                    ),
                    const Positioned.fill(
                      child: IgnorePointer(
                        child: ColoredBox(color: Color(0xDC020514)),
                      ),
                    ),
                    SafeArea(
                      top: false,
                      child: OwnTopBar(child: _readable(page())),
                    ),
                  ],
                ),
              ),
            ),
        transitionsBuilder: (context, animation, secondaryAnimation, child) {
          final curved = CurvedAnimation(
            parent: animation,
            curve: Curves.easeOutCubic,
            reverseCurve: Curves.easeInCubic,
          );
          return FadeTransition(
            opacity: curved,
            child: SlideTransition(
              position: Tween<Offset>(
                begin: const Offset(0.0, 0.04),
                end: Offset.zero,
              ).animate(curved),
              child: ScaleTransition(
                scale: Tween<double>(begin: 0.97, end: 1.0).animate(curved),
                child: child,
              ),
            ),
          );
        },
      ),
    );
  }

  /// Pages stay no wider than a line can be read on a tablet.
  static Widget _readable(Widget page) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 720),
      child: page,
    ),
  );

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final core = widget.core;
    final settings = widget.settings;

    void openServers() => setState(() => _tab = 1);
    void openStats() => setState(() => _tab = 2);
    void openAccounts() => _push(
      l.navAccounts,
      () => AccountsPage(core: core, settings: settings),
    );
    void openGeneralSettings() => _push(
      l.sectionGeneral,
      () => SettingsPage(
        core: core,
        settings: settings,
        section: SettingsSection.general,
      ),
    );
    void openConnection() => _push(
      l.sectionConnection,
      () => SettingsPage(
        core: core,
        settings: settings,
        section: SettingsSection.connection,
      ),
    );

    final page =
        widget.noCore ??
        switch (_tab) {
          0 => OverviewPage(
            key: const ValueKey('clxy-tab-home'),
            core: core,
            settings: settings,
            onOpenServers: openServers,
            onOpenStats: openStats,
            onOpenAccounts: openAccounts,
            onOpenConnection: openConnection,
            phone: true,
          ),
          1 => ServersPage(
            key: const ValueKey('clxy-tab-servers'),
            core: core,
            settings: settings,
          ),
          2 => StatsPage(key: const ValueKey('clxy-tab-stats'), core: core),
          _ => _SettingsList(
            key: const ValueKey('clxy-tab-more'),
            onOpenAccounts: openAccounts,
            push: _push,
            core: core,
            settings: settings,
          ),
        };
    final showWallpaper = widget.noCore == null;

    return PopScope(
      canPop: _tab == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) setState(() => _tab = 0);
      },
      child: Scaffold(
        extendBody: true,
        backgroundColor: ClxyMobileColors.background,
        body: Stack(
          fit: StackFit.expand,
          children: [
            if (showWallpaper)
              Positioned.fill(
                child: IgnorePointer(
                  child: Image.asset(
                    key: const ValueKey('clxy-mobile-dashboard-wallpaper'),
                    'assets/dashboard-mobile-wallpaper-extra-blur.jpg',
                    fit: BoxFit.cover,
                    filterQuality: FilterQuality.medium,
                    excludeFromSemantics: true,
                  ),
                ),
              ),
            if (showWallpaper)
              const Positioned.fill(
                child: IgnorePointer(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: RadialGradient(
                        center: Alignment(0, -0.45),
                        radius: 1.1,
                        colors: [
                          Color(0xCC06123C),
                          Color(0xE0020518),
                          Color(0xF2010314),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            SafeArea(
              child: Column(
                children: [
                  _MobileBrandHeader(
                    onTapCrown: openAccounts,
                    onTapSettings: openGeneralSettings,
                  ),
                  ?widget.banner,
                  Expanded(
                    child: AnimatedSwitcher(
                      duration: const Duration(milliseconds: 280),
                      switchInCurve: Curves.easeOutCubic,
                      switchOutCurve: Curves.easeInCubic,
                      transitionBuilder: (child, animation) => FadeTransition(
                        opacity: animation,
                        child: SlideTransition(
                          position: Tween<Offset>(
                            begin: const Offset(0.0, 0.025),
                            end: Offset.zero,
                          ).animate(animation),
                          child: child,
                        ),
                      ),
                      child: _tab == 0 ? page : _readable(page),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        bottomNavigationBar: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
            child: _MobileBottomBar(
              selectedIndex: _tab,
              onSelected: (index) => setState(() => _tab = index),
            ),
          ),
        ),
      ),
    );
  }
}

/// 1:1 CLXY Top Header:
/// - Left: Glowing 3D Neon Shield + Lightning Bolt + "CLXY" + "CLONER XRAY"
/// - Right: Golden VIP Crown Circular Button & Slate-Blue Settings Gear Button
class _MobileBrandHeader extends StatelessWidget {
  const _MobileBrandHeader({
    required this.onTapCrown,
    required this.onTapSettings,
  });

  final VoidCallback onTapCrown;
  final VoidCallback onTapSettings;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 6, 18, 8),
      child: Directionality(
        textDirection: TextDirection.ltr,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Flexible(
              child: FittedBox(
                fit: BoxFit.scaleDown,
                alignment: Alignment.centerLeft,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const AppLogo(
                      key: ValueKey('clxy-mobile-brand-mark'),
                      size: 48,
                    ),
                    const SizedBox(width: 11),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          'CLXY',
                          style: theme.textTheme.titleLarge?.copyWith(
                            color: const Color(0xFFF5F9FF),
                            fontSize: 23,
                            fontWeight: FontWeight.w900,
                            letterSpacing: 2.2,
                            height: 1.05,
                            shadows: const [
                              Shadow(color: Color(0x9938B6FF), blurRadius: 12),
                            ],
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'CLONER XRAY',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: const Color(0xFF83AEE8),
                            fontSize: 10.5,
                            fontWeight: FontWeight.w500,
                            letterSpacing: 1.6,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(width: 8),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                ClxyHeaderActionButton(isCrown: true, onTap: onTapCrown),
                const SizedBox(width: 10),
                ClxyHeaderActionButton(isCrown: false, onTap: onTapSettings),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// 1:1 CLXY Floating Bottom Navigation Dock with 3D Neon Cyan Dome Spotlight.
class _MobileBottomBar extends StatelessWidget {
  const _MobileBottomBar({
    required this.selectedIndex,
    required this.onSelected,
  });

  final int selectedIndex;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final destinations = <(IconData, IconData, String)>[
      (Icons.home_outlined, Icons.home_rounded, l.navHome),
      (Icons.public_outlined, Icons.public_rounded, l.navServers),
      (Icons.bar_chart_outlined, Icons.bar_chart_rounded, l.navStats),
      (Icons.more_horiz_rounded, Icons.more_horiz_rounded, l.navMore),
    ];
    return Container(
      height: 74,
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xEB0E1C4E), Color(0xF5070D2A)],
        ),
        borderRadius: BorderRadius.circular(37),
        border: Border.all(color: const Color(0x6B3A66CD), width: 1.2),
        boxShadow: const [
          BoxShadow(
            color: Color(0xD900061E),
            blurRadius: 26,
            offset: Offset(0, -4),
          ),
          BoxShadow(color: Color(0x221088FF), blurRadius: 22),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Row(
        children: [
          for (var i = 0; i < destinations.length; i++)
            Expanded(
              child: Semantics(
                button: true,
                selected: selectedIndex == i,
                label: destinations[i].$3,
                child: InkWell(
                  borderRadius: BorderRadius.circular(36),
                  onTap: () => onSelected(i),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 240),
                    curve: Curves.easeOutCubic,
                    decoration: selectedIndex == i
                        ? BoxDecoration(
                            borderRadius: BorderRadius.circular(36),
                            gradient: const RadialGradient(
                              center: Alignment(0, -0.95),
                              radius: 1.05,
                              colors: [
                                Color(0xA641C8FF),
                                Color(0x591052EB),
                                Color(0x14081C6E),
                              ],
                            ),
                            border: Border.all(
                              color: const Color(0x884AE0FF),
                              width: 1.1,
                            ),
                            boxShadow: const [
                              BoxShadow(
                                color: Color(0x401487FF),
                                blurRadius: 18,
                              ),
                            ],
                          )
                        : const BoxDecoration(),
                    child: Stack(
                      alignment: Alignment.center,
                      children: [
                        if (selectedIndex == i)
                          Positioned(
                            top: 0,
                            left: 18,
                            right: 18,
                            child: Container(
                              height: 2.5,
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(99),
                                gradient: const LinearGradient(
                                  colors: [
                                    Colors.transparent,
                                    Color(0xFF8CEEFF),
                                    Colors.white,
                                    Color(0xFF8CEEFF),
                                    Colors.transparent,
                                  ],
                                ),
                              ),
                            ),
                          ),
                        Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              selectedIndex == i
                                  ? destinations[i].$2
                                  : destinations[i].$1,
                              size: 22,
                              color: selectedIndex == i
                                  ? const Color(0xFF42E3FF)
                                  : const Color(0xFF9AB0DC),
                              shadows: selectedIndex == i
                                  ? const [
                                      Shadow(
                                        color: Color(0xAA38D9FF),
                                        blurRadius: 10,
                                      ),
                                    ]
                                  : null,
                            ),
                            const SizedBox(height: 3),
                            FittedBox(
                              fit: BoxFit.scaleDown,
                              child: Text(
                                destinations[i].$3,
                                maxLines: 1,
                                style: Theme.of(context).textTheme.labelSmall
                                    ?.copyWith(
                                      color: selectedIndex == i
                                          ? Colors.white
                                          : const Color(0xFF869CC7),
                                      fontSize: 12,
                                      fontWeight: selectedIndex == i
                                          ? FontWeight.w600
                                          : FontWeight.w400,
                                    ),
                              ),
                            ),
                          ],
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
  }
}

/// The More tab keeps all existing settings and utility routes available.
class _SettingsList extends StatelessWidget {
  const _SettingsList({
    super.key,
    required this.onOpenAccounts,
    required this.push,
    required this.core,
    required this.settings,
  });

  final VoidCallback onOpenAccounts;
  final void Function(String title, Widget Function() page) push;
  final CoreController core;
  final AppSettings settings;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final profileCopy = ProxyProfileCopy.of(context);

    Widget group(String title, List<Widget> tiles) => Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(4, 18, 4, 8),
          child: Text(
            title,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: ClxyMobileColors.muted,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        ClxyGlassSurface(
          child: Column(
            children: [
              for (var i = 0; i < tiles.length; i++) ...[
                if (i > 0)
                  const Divider(
                    height: 1,
                    indent: 56,
                    color: ClxyMobileColors.line,
                  ),
                tiles[i],
              ],
            ],
          ),
        ),
      ],
    );

    Widget tile(
      IconData icon,
      String title,
      VoidCallback onTap, {
      String? subtitle,
      Color accent = ClxyMobileColors.cyan,
      bool compact = true,
    }) => ClxyMobileMenuRow(
      icon: icon,
      title: title,
      subtitle: subtitle,
      accent: accent,
      compact: compact,
      onTap: onTap,
    );

    Widget section(
      SettingsSection section,
      IconData icon,
      String name, {
      String? subtitle,
      Color accent = ClxyMobileColors.cyan,
    }) => tile(
      icon,
      name,
      () => push(
        name,
        () => SettingsPage(core: core, settings: settings, section: section),
      ),
      subtitle: subtitle,
      accent: accent,
    );

    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 12, 18, 24),
      children: [
        ClxyMobilePageHeading(
          title: l.navMore,
          subtitle: l.settingsTitle,
          eyebrow: 'CLXY',
        ),
        ClxyGlassSurface(
          child: tile(
            Icons.people_outline_rounded,
            l.navAccounts,
            onOpenAccounts,
            compact: false,
            accent: ClxyMobileColors.cyan,
          ),
        ),
        const SizedBox(height: 14),
        group(l.settingsTitle, [
          section(
            SettingsSection.general,
            Icons.tune_rounded,
            l.sectionGeneral,
            subtitle: l.language,
          ),
          section(
            SettingsSection.routing,
            Icons.alt_route_rounded,
            l.sectionRouting,
            subtitle: l.routingDirect,
            accent: ClxyMobileColors.violet,
          ),
          section(
            SettingsSection.dns,
            Icons.dns_rounded,
            Icons.dns_rounded == Icons.dns_rounded
                ? l.sectionDns
                : l.sectionDns,
            subtitle: l.dnsPreset,
            accent: ClxyMobileColors.green,
          ),
          section(
            SettingsSection.connection,
            Icons.vpn_lock_outlined,
            l.sectionConnection,
            subtitle: l.transport,
          ),
          section(
            SettingsSection.accounts,
            Icons.swap_horiz_rounded,
            l.sectionAccounts,
          ),
          if (!Platform.isAndroid)
            section(
              SettingsSection.advanced,
              Icons.build_outlined,
              l.sectionAdvanced,
            ),
        ]),
        group(l.groupApp, [
          if (Platform.isAndroid)
            tile(
              Icons.devices_other_rounded,
              profileCopy.title,
              () => push(
                profileCopy.title,
                () => ProxyProfilePage(core: core, settings: settings),
              ),
              accent: ClxyMobileColors.green,
            ),
          tile(
            Icons.receipt_long_outlined,
            l.navLogs,
            () => push(l.navLogs, () => LogsPage(core: core)),
            accent: ClxyMobileColors.amber,
          ),
          tile(
            Icons.info_outline_rounded,
            l.navAbout,
            () => push(l.navAbout, () => AboutPage(core: core)),
            accent: ClxyMobileColors.violet,
          ),
        ]),
      ],
    );
  }
}
