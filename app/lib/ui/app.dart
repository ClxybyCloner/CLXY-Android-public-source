import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import '../app_settings.dart';
import '../core/core_api.dart';
import '../core/core_controller.dart';
import 'network_location.dart';
import '../l10n/app_localizations.dart';
import 'about_page.dart';
import 'accounts_page.dart';
import 'app_zoom.dart';
import 'logs_page.dart';
import 'mobile_shell.dart';
import 'overview_page.dart';
import 'port_dialog.dart';
import 'servers_page.dart';
import 'settings_page.dart';
import 'welcome_page.dart';
import 'widgets.dart';
import 'connection_globe.dart';

/// Keep the edge-to-edge system bars consistent with CLXY's dark surfaces.
const clxyDarkSystemUiOverlayStyle = SystemUiOverlayStyle(
  statusBarColor: Colors.transparent,
  statusBarBrightness: Brightness.dark,
  statusBarIconBrightness: Brightness.light,
  systemNavigationBarColor: Colors.transparent,
  systemNavigationBarDividerColor: Colors.transparent,
  systemNavigationBarIconBrightness: Brightness.light,
  systemStatusBarContrastEnforced: false,
  systemNavigationBarContrastEnforced: false,
);

ThemeData _theme(Locale locale) {
  final scheme = ColorScheme.fromSeed(
    seedColor: const Color(0xFF5B5BD6),
    brightness: Brightness.dark,
  );
  // Vazirmatn for Persian and Arabic; the system font (Segoe UI)
  // otherwise. Styles set here name it too, or they would fall back to the
  // system font.
  final font = languageFont(locale.languageCode);
  return ThemeData(
    colorScheme: scheme,
    fontFamily: font,
    scaffoldBackgroundColor: const Color(0xFF111218),
    cardTheme: CardThemeData(
      elevation: 0,
      color: const Color(0xFF1A1B23),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: scheme.outlineVariant.withValues(alpha: 0.4)),
      ),
    ),
    navigationRailTheme: NavigationRailThemeData(
      backgroundColor: const Color(0xFF15161D),
      // The menu's names semi-bold, the open page's bold.
      selectedLabelTextStyle: TextStyle(
        fontFamily: font,
        fontSize: 15.5,
        fontWeight: FontWeight.w700,
        color: scheme.onSurface,
      ),
      unselectedLabelTextStyle: TextStyle(
        fontFamily: font,
        fontSize: 15.5,
        fontWeight: FontWeight.w600,
        color: scheme.onSurfaceVariant,
      ),
      selectedIconTheme: IconThemeData(
        size: 24,
        color: scheme.onSecondaryContainer,
      ),
      unselectedIconTheme: IconThemeData(
        size: 24,
        color: scheme.onSurfaceVariant,
      ),
    ),
  );
}

class VpnApp extends StatefulWidget {
  const VpnApp({
    super.key,
    required this.core,
    required this.settings,
    this.navigatorKey,
  });

  final CoreController core;
  final AppSettings settings;
  final GlobalKey<NavigatorState>? navigatorKey;

  @override
  State<VpnApp> createState() => _VpnAppState();
}

class _VpnAppState extends State<VpnApp> {
  late final NetworkLocationTracker tracker = NetworkLocationTracker();

  @override
  void initState() {
    super.initState();
    tracker.requestOriginLocation();
  }

  @override
  void dispose() {
    tracker.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.settings,
    builder: (context, _) => MaterialApp(
      navigatorKey: widget.navigatorKey,
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(),
      themeMode: ThemeMode.dark,
      locale: widget.settings.locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localeListResolutionCallback: (locales, _) =>
          AppSettings.localeOf(AppSettings.languageForList(locales)),
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      // The theme depends on the resolved language for its font.
      builder: (context, child) {
        final locale = Localizations.localeOf(context);
        return AnnotatedRegion<SystemUiOverlayStyle>(
          value: clxyDarkSystemUiOverlayStyle,
          child: MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(platformBrightness: Brightness.dark),
            child: Theme(
              data: _theme(locale),
              child: AppZoom(child: child!),
            ),
          ),
        );
      },
      home: NetworkLocationScope(
        tracker: tracker,
        child: Shell(core: widget.core, settings: widget.settings),
      ),
    ),
  );
}

class Shell extends StatefulWidget {
  const Shell({super.key, required this.core, required this.settings});

  final CoreController core;
  final AppSettings settings;

  @override
  State<Shell> createState() => _ShellState();
}

enum _Page { overview, servers, accounts, settings, logs, about }

class _ShellState extends State<Shell> {
  _Page _page = _Page.overview;

  /// The settings section the home page opened (Connection), until another
  /// page is chosen.
  SettingsSection? _settingsOpening;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.core,
    builder: (context, _) {
      final l = AppLocalizations.of(context);
      final core = widget.core;
      final status = core.status;
      final needsAttention = (core.accounts?.accounts ?? const [])
          .where((a) => a.enabled && a.needsSignIn)
          .length;
      final starting =
          status == null &&
          (core.unavailable == null ||
              core.unavailable?.problem == CoreProblem.starting);
      if (widget.settings.firstRun && (status != null || starting)) {
        return WelcomePage(core: core, settings: widget.settings);
      }
      final pages = [
        _Page.overview,
        _Page.servers,
        _Page.accounts,
        _Page.settings,
        _Page.logs,
        _Page.about,
      ];
      final page = pages.contains(_page) ? _page : _Page.overview;
      Widget accountsIcon(IconData icon) => Badge(
        isLabelVisible: needsAttention > 0,
        label: Text('$needsAttention'),
        child: Icon(icon),
      );
      // Each page's icons and name, for the side menu and the bottom bar.
      final items = [
        for (final page in pages)
          switch (page) {
            _Page.overview => (
              const Icon(Icons.home_outlined),
              const Icon(Icons.home_rounded),
              l.navOverview,
            ),
            _Page.servers => (
              const Icon(Icons.language_outlined),
              const Icon(Icons.language_rounded),
              l.navServers,
            ),
            _Page.accounts => (
              accountsIcon(Icons.person_outline_rounded),
              accountsIcon(Icons.person_rounded),
              l.navAccounts,
            ),
            _Page.settings => (
              const Icon(Icons.settings_outlined),
              const Icon(Icons.settings_rounded),
              l.navSettings,
            ),
            _Page.logs => (
              const Icon(Icons.article_outlined),
              const Icon(Icons.article_rounded),
              l.navLogs,
            ),
            _Page.about => (
              const Icon(Icons.info_outline_rounded),
              const Icon(Icons.info_rounded),
              l.navAbout,
            ),
          },
      ];
      final content = Column(
        children: [
          if (core.unavailable != null && status != null) const _LostBanner(),
          Expanded(
            child: status == null
                ? _NoCore(core: core)
                : switch (page) {
                    // Its cards open the pages beside it.
                    _Page.overview => OverviewPage(
                      core: core,
                      settings: widget.settings,
                      onOpenServers: () =>
                          setState(() => _page = _Page.servers),
                      onOpenAccounts: () =>
                          setState(() => _page = _Page.accounts),
                      onOpenSettings: () => setState(() {
                        _page = _Page.settings;
                        _settingsOpening = null;
                      }),
                      onOpenLogs: () => setState(() => _page = _Page.logs),
                      onOpenConnection: () => setState(() {
                        _page = _Page.settings;
                        _settingsOpening = SettingsSection.connection;
                      }),
                    ),
                    _Page.servers => ServersPage(
                      core: core,
                      settings: widget.settings,
                    ),
                    _Page.accounts => AccountsPage(
                      core: core,
                      settings: widget.settings,
                    ),
                    _Page.settings => SettingsPage(
                      key: ValueKey(_settingsOpening),
                      core: core,
                      settings: widget.settings,
                      opening: _settingsOpening,
                    ),
                    _Page.logs => LogsPage(core: core),
                    _Page.about => AboutPage(core: core),
                  },
          ),
        ],
      );
      void select(int i) => setState(() {
        _page = pages[i];
        _settingsOpening = null;
      });
      // Android (a phone, or a tablet in either orientation) and a narrow
      // window: the phone layout, Home and Settings. The computer layout
      // offers computer-only choices, such as Windows' system proxy.
      if (Platform.isAndroid || MediaQuery.sizeOf(context).width < 640) {
        return MobileShell(
          core: core,
          settings: widget.settings,
          banner: core.unavailable != null && status != null
              ? const _LostBanner()
              : null,
          noCore: status == null ? _NoCore(core: core) : null,
        );
      }
      return Scaffold(
        body: Row(
          children: [
            SizedBox(
              width: 254,
              child: _DesktopSidebar(
                title: l.appTitle,
                items: items,
                selectedIndex: pages.indexOf(page),
                onSelect: select,
                footer: l.unofficialNote,
              ),
            ),
            // A maximized window: the pages stay as wide as reads well; the
            // home page, centered by itself, keeps its colors to the edges.
            Expanded(
              child: page == _Page.overview || status == null
                  ? content
                  : Align(
                      alignment: Alignment.topCenter,
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 1000),
                        child: content,
                      ),
                    ),
            ),
          ],
        ),
      );
    },
  );
}

class _LostBanner extends StatelessWidget {
  const _LostBanner();

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    color: StatusColors.bad.withValues(alpha: 0.14),
    padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 10),
    child: Row(
      children: [
        const Icon(Icons.link_off_rounded, size: 18, color: StatusColors.bad),
        const SizedBox(width: 10),
        Expanded(child: Text(AppLocalizations.of(context).coreLost)),
      ],
    ),
  );
}

class _NoCore extends StatelessWidget {
  const _NoCore({required this.core});

  final CoreController core;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final problem = core.unavailable;
    final portTaken =
        problem != null &&
        (problem.problem == CoreProblem.portInUse ||
            problem.problem == CoreProblem.refused);
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 460),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (problem == null || problem.problem == CoreProblem.starting) ...[
              const CircularProgressIndicator(),
              const SizedBox(height: 16),
              Text(l.startingCore, style: theme.textTheme.titleMedium),
            ] else ...[
              Icon(
                Icons.power_off_rounded,
                size: 48,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(height: 16),
              Text(l.cantReachCore, style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(
                describeProblem(l, problem),
                textAlign: TextAlign.center,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 20),
              Wrap(
                spacing: 12,
                alignment: WrapAlignment.center,
                children: [
                  if (portTaken && core.moveStoppedCore != null)
                    FilledButton.icon(
                      onPressed: () async {
                        final port = await showPortDialog(
                          context,
                          taken: problem.detail,
                        );
                        if (port != null && context.mounted) {
                          await runShowingErrors(
                            context,
                            () => core.setAppPort(port),
                          );
                        }
                      },
                      icon: const Icon(Icons.settings_ethernet_rounded),
                      label: Text(l.changePort),
                    ),
                  FilledButton.tonalIcon(
                    onPressed: core.retry,
                    icon: const Icon(Icons.refresh_rounded),
                    label: Text(l.retry),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _DesktopSidebar extends StatelessWidget {
  const _DesktopSidebar({
    required this.title,
    required this.items,
    required this.selectedIndex,
    required this.onSelect,
    required this.footer,
  });

  final String title;
  final List<(Widget, Widget, String)> items;
  final int selectedIndex;
  final ValueChanged<int> onSelect;
  final String footer;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: const BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF091629), Color(0xFF07111F), Color(0xFF081526)],
      ),
      border: Border(right: BorderSide(color: Color(0xFF17243A))),
    ),
    child: Stack(
      children: [
        Positioned(
          left: 0,
          right: 0,
          bottom: 213,
          height: 240,
          child: IgnorePointer(
            child: Image.asset(
              'assets/dashboard-sidebar-mountains.png',
              fit: BoxFit.fill,
              errorBuilder: (context, error, stackTrace) =>
                  const CustomPaint(painter: _SidebarMountainsPainter()),
            ),
          ),
        ),
        Positioned.fill(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsetsDirectional.fromSTEB(25, 22, 16, 43),
                child: Semantics(
                  label: title,
                  child: Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: Image.asset(
                      'assets/dashboard-sidebar-brand.png',
                      width: 198,
                      height: 72,
                      excludeFromSemantics: true,
                      filterQuality: FilterQuality.high,
                    ),
                  ),
                ),
              ),
              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsetsDirectional.fromSTEB(15, 2, 9, 12),
                  itemCount: items.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 12),
                  itemBuilder: (context, index) => _DesktopNavItem(
                    icon: items[index].$1,
                    selectedIcon: items[index].$2,
                    label: items[index].$3,
                    selected: index == selectedIndex,
                    onTap: () => onSelect(index),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(35, 12, 16, 59),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Freedom\non your terms.',
                      style: TextStyle(
                        color: Color(0xFFF2F5FF),
                        fontSize: 18,
                        height: 1.18,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Container(
                      width: 29,
                      height: 2,
                      decoration: BoxDecoration(
                        color: const Color(0xFF2189FF),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(height: 29),
                    Row(
                      children: [
                        const AppLogo(size: 29),
                        const SizedBox(width: 13),
                        Expanded(
                          child: Text(
                            title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: Color(0xFFD0DAF0),
                              fontSize: 14,
                              fontWeight: FontWeight.w400,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 5),
                    Padding(
                      padding: const EdgeInsetsDirectional.only(start: 37),
                      child: Text(
                        footer,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Color(0xFF7E8BA8),
                          fontSize: 10.5,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _DesktopNavItem extends StatelessWidget {
  const _DesktopNavItem({
    required this.icon,
    required this.selectedIcon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final Widget icon;
  final Widget selectedIcon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    selected: selected,
    child: Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 140),
          height: 61,
          padding: const EdgeInsetsDirectional.fromSTEB(22, 0, 12, 0),
          decoration: BoxDecoration(
            gradient: selected
                ? const LinearGradient(
                    begin: Alignment.centerLeft,
                    end: Alignment.centerRight,
                    colors: [Color(0xFF176FEC), Color(0xFF1157C6)],
                  )
                : null,
            color: selected ? null : Colors.transparent,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: selected ? const Color(0xFF2689FF) : Colors.transparent,
            ),
            boxShadow: selected
                ? const [
                    BoxShadow(
                      color: Color(0x30116BEB),
                      blurRadius: 13,
                      offset: Offset(0, 4),
                    ),
                  ]
                : null,
          ),
          child: Row(
            children: [
              IconTheme.merge(
                data: IconThemeData(
                  size: 28,
                  color: selected ? Colors.white : const Color(0xFFAFBCDA),
                ),
                child: selected ? selectedIcon : icon,
              ),
              const SizedBox(width: 20),
              Expanded(
                child: Text(
                  label,
                  style: TextStyle(
                    color: selected ? Colors.white : const Color(0xFFB4C1DF),
                    fontSize: 20,
                    fontWeight: selected ? FontWeight.w500 : FontWeight.w400,
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

class _SidebarMountainsPainter extends CustomPainter {
  const _SidebarMountainsPainter();

  @override
  void paint(Canvas canvas, Size size) {
    void ridge(List<Offset> points, Color color) {
      final path = Path()..moveTo(0, size.height);
      for (final point in points) {
        path.lineTo(point.dx * size.width, point.dy * size.height);
      }
      path
        ..lineTo(size.width, size.height)
        ..close();
      canvas.drawPath(path, Paint()..color = color);
    }

    ridge(const [
      Offset(0, .86),
      Offset(.12, .73),
      Offset(.22, .79),
      Offset(.40, .54),
      Offset(.51, .64),
      Offset(.62, .38),
      Offset(.73, .67),
      Offset(.88, .57),
      Offset(1, .72),
    ], const Color(0xFF0D347A));
    ridge(const [
      Offset(0, .92),
      Offset(.15, .82),
      Offset(.31, .75),
      Offset(.44, .88),
      Offset(.61, .61),
      Offset(.76, .78),
      Offset(.90, .70),
      Offset(1, .82),
    ], const Color(0xFF0A2558));
    ridge(const [
      Offset(0, .97),
      Offset(.19, .87),
      Offset(.36, .91),
      Offset(.52, .81),
      Offset(.67, .91),
      Offset(.82, .82),
      Offset(1, .91),
    ], const Color(0xFF071A3B));
  }

  @override
  bool shouldRepaint(covariant _SidebarMountainsPainter oldDelegate) => false;
}
