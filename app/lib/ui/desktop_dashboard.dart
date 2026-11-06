part of 'overview_page.dart';

abstract final class _DashboardColors {
  static const background = Color(0xFF050B16);
  static const panel = Color(0xFF07111F);
  static const inset = Color(0xFF091321);
  static const border = Color(0xFF1D2A40);
  static const text = Color(0xFFF1F5FF);
  static const muted = Color(0xFFA2B1D0);
  static const blue = Color(0xFF2189FF);
  static const blueDeep = Color(0xFF1456C5);
  static const green = Color(0xFF20D9A0);
  static const purple = Color(0xFF7775FF);
  static const orange = Color(0xFFEF9D56);
}

/// A desktop-first, three-column dashboard. Its measurements, neon rings,
/// mountain silhouette and data cards follow the supplied CLXY reference;
/// all connection and traffic values still come from the live core state.
class _DesktopDashboard extends StatelessWidget {
  const _DesktopDashboard({
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

  bool get _running => status.state.phase == Phase.running;
  bool get _confirming => _running && !status.confirmedBy(core.lastTest);
  bool get _connected => _running && !_confirming;
  bool get _on => status.state.phase != Phase.off;

  Color _accent(_HomePalette palette) => switch (status.state.phase) {
    Phase.error => palette.error,
    Phase.running when _confirming => palette.busy,
    Phase.running => _DashboardColors.green,
    Phase.starting => palette.busy,
    _ => palette.idle,
  };

  Future<void> _power(bool on) async {
    await core.setOn(on);
    if (on) unawaited(core.runTest().catchError((_) {}));
  }

  Future<void> _setMode(String mode) => core.setSystemProxy(mode);

  @override
  Widget build(BuildContext context) {
    final base = Theme.of(context);
    final night = midnightTheme(base);
    final darkTheme = night.copyWith(
      scaffoldBackgroundColor: _DashboardColors.background,
      colorScheme: night.colorScheme.copyWith(
        primary: _DashboardColors.blue,
        secondary: _DashboardColors.green,
        surface: _DashboardColors.panel,
      ),
    );
    return Theme(
      data: darkTheme,
      child: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF071121), _DashboardColors.background],
          ),
        ),
        child: Stack(
          children: [
            const Positioned.fill(
              child: CustomPaint(painter: _DashboardAmbientPainter()),
            ),
            Positioned.fill(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(28, 31, 27, 28),
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 1480),
                    child: LayoutBuilder(
                      builder: (context, bounds) {
                        final main = _mainColumn(context);
                        final rail = _statusColumn(context);
                        if (bounds.maxWidth < 870) {
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [main, const SizedBox(height: 18), rail],
                          );
                        }
                        final railWidth = math.min(
                          334.0,
                          math.max(286.0, bounds.maxWidth * 0.317),
                        );
                        return Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(child: main),
                            const SizedBox(width: 20),
                            SizedBox(width: railWidth, child: rail),
                          ],
                        );
                      },
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _mainColumn(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      _heroCard(context),
      const SizedBox(height: 22),
      _quickAccess(context),
    ],
  );

  Widget _statusColumn(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      _connectionCard(context),
      const SizedBox(height: 18),
      _liveTrafficCard(context),
      const SizedBox(height: 18),
      _totalTrafficCard(context),
    ],
  );

  Widget _heroCard(BuildContext context) {
    final l = AppLocalizations.of(context);
    final p = _HomePalette.night;
    final proxy = status.proxy;
    final look = PhaseLook.of(l, status);
    final connected = _connected;
    final on = _on;
    final accent = _accent(p);
    final place = homeLocation(l, status, connected: connected);
    final systemProxy = status.systemProxySet;
    final mismatch = exitMismatchText(l, status);
    final ip = !showIp
        ? ''
        : status.exit?.ipv4.isNotEmpty == true
        ? status.exit!.ipv4
        : status.exit?.ipv6 ?? '';
    final failing =
        _confirming &&
        ((proxy?.wakeError.isNotEmpty ?? false) || core.lastTest?.ok == false);
    final failure = failing ? failureText(l, proxy, core.lastTest) : null;
    final gaveUp = !on && status.connectFailure.isNotEmpty;
    final stateLabel = _confirming
        ? l.stateConnecting
        : connected
        ? l.stateConnected
        : look.label;

    return Container(
      decoration: BoxDecoration(
        color: _DashboardColors.panel,
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: _DashboardColors.border),
        boxShadow: const [
          BoxShadow(
            color: Color(0x30000000),
            blurRadius: 24,
            offset: Offset(0, 10),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        children: [
          Positioned(
            left: 0,
            right: 0,
            top: 0,
            height: 420,
            child: Stack(
              fit: StackFit.expand,
              children: [
                const DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Color(0xFF0B1730),
                        Color(0xFF071323),
                        Color(0xFF071323),
                      ],
                      stops: [0, 0.57, 1],
                    ),
                  ),
                ),
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  height: 330,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      Opacity(
                        opacity: 0.88,
                        child: Image.asset(
                          'assets/dashboard-mountains.png',
                          fit: BoxFit.cover,
                          alignment: Alignment.topCenter,
                          filterQuality: FilterQuality.high,
                          errorBuilder: (context, error, stackTrace) =>
                              const SizedBox.expand(
                                child: CustomPaint(
                                  painter: _DashboardMountainsPainter(),
                                ),
                              ),
                        ),
                      ),
                      const Positioned(
                        left: 0,
                        right: 0,
                        top: 0,
                        height: 64,
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [Color(0xFF09162B), Color(0x0009162B)],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const Positioned.fill(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Color(0x00050B16),
                          Color(0x00050B16),
                          Color(0xFF050B16),
                        ],
                        stops: [0, 0.68, 1],
                      ),
                    ),
                  ),
                ),
                // Decorative pixels cropped from the user-supplied CLXY
                // reference. Its two status labels were removed; Flutter
                // renders those below from live state, never from the PNG.
                if (connected)
                  Positioned.fill(
                    child: IgnorePointer(
                      child: Image.asset(
                        'assets/dashboard-reference-hero.png',
                        fit: BoxFit.fill,
                        filterQuality: FilterQuality.high,
                        excludeFromSemantics: true,
                      ),
                    ),
                  ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 24, 16, 21),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Opacity(
                  opacity: connected ? 0 : 1,
                  alwaysIncludeSemantics: true,
                  child: _heroHeader(context),
                ),
                const SizedBox(height: 8),
                SizedBox(
                  height: 301,
                  child: Stack(
                    children: [
                      if (!connected)
                        Positioned(
                          top: 5,
                          right: 42,
                          child: Transform.rotate(
                            angle: -0.08,
                            child: Column(
                              children: [
                                const Text(
                                  'Just\nOne Click',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                    color: Color(0xFFD8E4FF),
                                    fontSize: 20,
                                    height: 1.12,
                                    fontStyle: FontStyle.italic,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                                Container(
                                  margin: const EdgeInsets.only(top: 26),
                                  width: 88,
                                  height: 34,
                                  child: Stack(
                                    children: [
                                      Positioned(
                                        left: 0,
                                        right: 0,
                                        top: 0,
                                        height: 20,
                                        child: CustomPaint(
                                          painter: _DashboardSwooshPainter(
                                            _DashboardColors.blue,
                                          ),
                                        ),
                                      ),
                                      const Positioned(
                                        right: 15,
                                        top: 13,
                                        child: Text(
                                          '!',
                                          style: TextStyle(
                                            color: _DashboardColors.blue,
                                            fontSize: 20,
                                            height: 1,
                                            fontStyle: FontStyle.italic,
                                            fontWeight: FontWeight.w500,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      Positioned(
                        top: 7,
                        left: 0,
                        right: 0,
                        child: Center(
                          child: _DashboardPowerOrb(
                            on: on,
                            connected: connected,
                            busy: core.switchingProxy,
                            label: stateLabel,
                            accent: accent,
                            tooltip: on ? l.tapToDisconnect : l.tapToConnect,
                            onPressed: () => status.totalAccounts == 0
                                ? _openAccounts(
                                    context,
                                    core,
                                    settings,
                                    onOpenAccounts,
                                  )
                                : runShowingErrors(context, () => _power(!on)),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Container(
                      width: 9,
                      height: 9,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: connected ? _DashboardColors.green : accent,
                        boxShadow: [
                          BoxShadow(
                            color: (connected ? _DashboardColors.green : accent)
                                .withValues(alpha: 0.45),
                            blurRadius: 10,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 9),
                    Flexible(
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        child: Text(
                          connected ? l.proxyServiceActive : stateLabel,
                          style: Theme.of(context).textTheme.titleSmall
                              ?.copyWith(
                                color: connected
                                    ? _DashboardColors.green
                                    : accent,
                                fontSize: 20,
                                fontWeight: FontWeight.w600,
                              ),
                        ),
                      ),
                    ),
                  ],
                ),
                if (ip.isNotEmpty) ...[
                  const SizedBox(height: 9),
                  Wrap(
                    alignment: WrapAlignment.center,
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _HomePill(
                        palette: p,
                        child: Text(
                          '${l.publicIp} ${ltr(ip)}',
                          style: Theme.of(context).textTheme.labelLarge
                              ?.copyWith(color: p.muted),
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 33),
                _connectionDetails(context, place),
                const SizedBox(height: 23),
                Row(
                  children: [
                    Expanded(
                      child: Tooltip(
                        message: _running && proxy != null
                            ? '${l.proxyServiceActive} · ${proxy.listen}'
                            : '',
                        child: _ModeTile(
                          icon: Icons.link_rounded,
                          title: l.modeProxy,
                          selected: !systemProxy,
                          accent: _DashboardColors.green,
                          onTap: core.switchingProxy
                              ? null
                              : () => runShowingErrors(
                                  context,
                                  () => _setMode('clear'),
                                ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _ModeTile(
                        icon: Icons.desktop_windows_rounded,
                        title: l.systemProxy,
                        selected: systemProxy,
                        accent: _DashboardColors.blue,
                        onTap: core.switchingProxy
                            ? null
                            : () => runShowingErrors(
                                context,
                                () => _setMode('set'),
                              ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 26),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(
                      Icons.info_rounded,
                      size: 20,
                      color: Color(0xFF91A8E8),
                    ),
                    const SizedBox(width: 9),
                    Expanded(
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        alignment: AlignmentDirectional.centerStart,
                        child: Text(
                          switch ((
                            systemProxy,
                            defaultTargetPlatform == TargetPlatform.windows,
                          )) {
                            (true, true) => l.modeSystemProxyHint,
                            (true, false) => l.modeSystemProxyHintOther,
                            (false, true) => l.modeProxyHint,
                            (false, false) => l.modeProxyHintOther,
                          },
                          maxLines: 1,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(
                                color: _DashboardColors.muted,
                                fontSize: 16,
                                height: 1.35,
                              ),
                        ),
                      ),
                    ),
                  ],
                ),
                if (failing || (status.noNetwork && on) || gaveUp) ...[
                  const SizedBox(height: 12),
                  _ProblemBox(
                    text: status.noNetwork && on
                        ? l.noNetwork
                        : failure?.text ??
                              failureCodeText(l, status.connectFailure),
                    color: p.error,
                    icon: Icons.error_outline_rounded,
                    tooltip: status.noNetwork ? '' : failure?.detail ?? '',
                  ),
                ] else if (!on || look.hint.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Text(
                    !on ? l.tapToConnect : look.hint,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall
                        ?.copyWith(color: _DashboardColors.muted),
                  ),
                ],
                if (failure?.code == Failure.unreachable &&
                    onOpenConnection != null)
                  _ConnectionSettingsButton(onPressed: onOpenConnection!),
                if (isLocationRefused(failure?.code ?? '') ||
                    (gaveUp && isLocationRefused(status.connectFailure)))
                  _RecommendedLocationButton(core: core),
                if (connected && mismatch != null)
                  _ExitMismatchNote(text: mismatch),
                if (status.systemProxySet && status.systemProxyTakenBy != null)
                  _SystemProxyTakenNote(
                    core: core,
                    by: status.systemProxyTakenBy!,
                  ),
                if (status.state.proxyPortBlocked) ...[
                  const SizedBox(height: 12),
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
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _heroHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsetsDirectional.only(start: 18),
      child: Row(
        children: [
          Container(
            width: 31,
            height: 31,
            decoration: BoxDecoration(
              color: _DashboardColors.blueDeep,
              borderRadius: BorderRadius.circular(9),
              boxShadow: const [
                BoxShadow(color: Color(0x502189FF), blurRadius: 12),
              ],
            ),
            alignment: Alignment.center,
            child: const Icon(
              Icons.bolt_rounded,
              size: 22,
              color: Colors.white,
            ),
          ),
          const SizedBox(width: 10),
          Text(
            'CLXY',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: _DashboardColors.text,
              fontSize: 22,
              fontWeight: FontWeight.w800,
              letterSpacing: 0.25,
            ),
          ),
          const SizedBox(width: 7),
          Text(
            'Cloner Xray',
            style: Theme.of(context).textTheme.bodySmall
                ?.copyWith(color: _DashboardColors.muted, fontSize: 15),
          ),
          const Spacer(),
        ],
      ),
    );
  }

  Widget _connectionDetails(BuildContext context, HomePlace place) {
    final l = AppLocalizations.of(context);
    final proxy = status.proxy;
    final transport = _transportName(l, proxy?.transport ?? '');
    final port = proxy == null
        ? '—'
        : '${portOf(proxy.listen) ?? proxy.listen}';
    return Container(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 16, 16, 16),
      decoration: BoxDecoration(
        color: const Color(0xD9091525),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: _DashboardColors.border),
      ),
      child: Row(
        children: [
          Expanded(
            flex: 5,
            child: _DetailCell(
              label: l.homeLocation,
              value: place.name,
              leading: Container(
                width: 48,
                height: 48,
                alignment: Alignment.center,
                decoration: const BoxDecoration(
                  color: Color(0xFF124E9D),
                  shape: BoxShape.circle,
                ),
                child: CountryFlag(
                  key: const ValueKey('clxy-dashboard-location-flag'),
                  code: place.flag,
                  width: 34,
                ),
              ),
              onTap: onOpenServers,
            ),
          ),
          const _DetailDivider(),
          Expanded(
            flex: 4,
            child: _DetailCell(
              label: _protocolLabel(context, l),
              value: transport,
              leading: const _DetailIcon(
                icon: Icons.dns_rounded,
                background: Color(0xFF1C2E60),
                foreground: Color(0xFFB7C8FF),
              ),
            ),
          ),
          const _DetailDivider(),
          Expanded(
            flex: 3,
            child: _DetailCell(
              label: l.portLabel,
              value: port,
              leading: const _DetailIcon(
                icon: Icons.account_tree_rounded,
                background: Color(0xFF292764),
                foreground: Color(0xFFC1B8FF),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _quickAccess(BuildContext context) {
    final l = AppLocalizations.of(context);
    final settingsAction = onOpenSettings ?? onOpenConnection;
    return _DashboardPanel(
      padding: const EdgeInsets.fromLTRB(17, 17, 17, 15),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsetsDirectional.only(start: 12),
            child: Row(
              children: [
                const Icon(
                  Icons.bolt_rounded,
                  color: Color(0xFF9A8BFF),
                  size: 26,
                ),
                const SizedBox(width: 13),
                Text(
                  'Quick Access',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    color: _DashboardColors.text,
                    fontSize: 24,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          LayoutBuilder(
            builder: (context, bounds) {
              final items = [
                _QuickLinkData(
                  icon: Icons.language_rounded,
                  title: l.navServers,
                  subtitle: 'Choose the best server',
                  color: _DashboardColors.blue,
                  onTap: onOpenServers,
                ),
                _QuickLinkData(
                  icon: Icons.person_rounded,
                  title: l.navAccounts,
                  subtitle: 'Manage your accounts',
                  color: _DashboardColors.purple,
                  onTap: onOpenAccounts,
                ),
                _QuickLinkData(
                  icon: Icons.settings_rounded,
                  title: l.navSettings,
                  subtitle: 'Customize your connection',
                  color: _DashboardColors.green,
                  onTap: settingsAction,
                ),
                _QuickLinkData(
                  icon: Icons.receipt_long_rounded,
                  title: l.navLogs,
                  subtitle: 'View connection logs',
                  color: _DashboardColors.orange,
                  onTap: onOpenLogs,
                ),
              ];
              if (bounds.maxWidth < 490) {
                return Column(
                  children: [
                    for (var i = 0; i < items.length; i++) ...[
                      if (i > 0) const SizedBox(height: 10),
                      _QuickLink(data: items[i]),
                    ],
                  ],
                );
              }
              return Column(
                children: [
                  Row(
                    children: [
                      Expanded(child: _QuickLink(data: items[0])),
                      const SizedBox(width: 12),
                      Expanded(child: _QuickLink(data: items[1])),
                    ],
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(child: _QuickLink(data: items[2])),
                      const SizedBox(width: 12),
                      Expanded(child: _QuickLink(data: items[3])),
                    ],
                  ),
                ],
              );
            },
          ),
          const SizedBox(height: 8),
        ],
      ),
    );
  }

  Widget _connectionCard(BuildContext context) {
    final l = AppLocalizations.of(context);
    final look = PhaseLook.of(l, status);
    final label = _confirming
        ? l.stateConnecting
        : _connected
        ? l.stateConnected
        : look.label;
    final proxy = status.proxy;
    final uptime = _connected && proxy?.startedAt != null
        ? formatDuration(l, DateTime.now().difference(proxy!.startedAt!))
        : '—';
    final showDelay = _connected && core.lastTest != null;
    final delayText = core.lastTest?.ok == true
        ? l.milliseconds(core.lastTest!.responseMs)
        : l.testFailed;

    return _DashboardPanel(
      padding: const EdgeInsets.fromLTRB(14, 22, 14, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const Icon(
                Icons.signal_cellular_alt_rounded,
                color: _DashboardColors.green,
                size: 25,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Connection Status',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: _DashboardColors.text,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              Tooltip(
                message: showDelay
                    ? '$delayText · ${l.realDelay} · ${l.testConnection}'
                    : label,
                child: InkResponse(
                  onTap: !showDelay || core.testing
                      ? null
                      : () => runShowingErrors(context, core.runTest),
                  radius: 18,
                  child: SizedBox.square(
                    dimension: 20,
                    child: Center(
                      child: Container(
                        width: 12,
                        height: 12,
                        decoration: BoxDecoration(
                          color: _connected
                              ? _DashboardColors.green
                              : _accent(_HomePalette.night),
                          shape: BoxShape.circle,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Padding(
            padding: const EdgeInsetsDirectional.only(start: 35),
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                color: _connected
                    ? _DashboardColors.green
                    : _accent(_HomePalette.night),
                fontSize: 28,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          const SizedBox(height: 24),
          Container(
            height: 365,
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 13),
            decoration: BoxDecoration(
              color: _DashboardColors.inset,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: _DashboardColors.border.withValues(alpha: 0.75),
              ),
            ),
            child: Column(
              children: [
                Expanded(
                  child: ConnectionGlobe(
                    tracker: NetworkLocationScope.of(context),
                    exit: status.exit,
                    connected: _connected,
                  ),
                ),
                const Divider(height: 16, color: _DashboardColors.border),
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: _TinyMetric(label: 'Uptime', value: uptime),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _liveTrafficCard(BuildContext context) {
    final l = AppLocalizations.of(context);
    final traffic = status.traffic;
    return SizedBox(
      height: 289,
      child: _DashboardPanel(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(
                  Icons.show_chart_rounded,
                  color: _DashboardColors.text,
                  size: 26,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    l.liveTraffic,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: _DashboardColors.text,
                      fontSize: 22,
                      height: 1.2,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 18),
            const Divider(
              height: 1,
              thickness: 1,
              color: _DashboardColors.border,
            ),
            const SizedBox(height: 20),
            SizedBox(
              height: 56,
              child: Row(
                children: [
                  Expanded(
                    child: _TrafficMetric(
                      icon: Icons.north_rounded,
                      color: _DashboardColors.green,
                      label: l.download,
                      value: traffic == null
                          ? '—'
                          : formatRate(traffic.downRate),
                    ),
                  ),
                  const _TinyDivider(),
                  Expanded(
                    child: _TrafficMetric(
                      icon: Icons.south_rounded,
                      color: _DashboardColors.purple,
                      label: 'Upload',
                      value: traffic == null ? '—' : formatRate(traffic.upRate),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 26),
            SizedBox(height: 100, child: _TrafficSparkline(core: core)),
          ],
        ),
      ),
    );
  }

  Widget _totalTrafficCard(BuildContext context) {
    final l = AppLocalizations.of(context);
    final traffic = status.traffic;
    final sum = traffic == null ? 0 : traffic.up + traffic.down;
    final downShare = sum == 0 ? 0 : (traffic!.down * 1000 / sum).round();
    return SizedBox(
      height: 166,
      child: _DashboardPanel(
        padding: const EdgeInsets.fromLTRB(15, 20, 15, 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(
                  Icons.storage_rounded,
                  color: Color(0xFFB9CAFF),
                  size: 26,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    l.totalTraffic,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: _DashboardColors.text,
                      fontSize: 20,
                      height: 1.2,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Divider(
                height: 1,
                thickness: 1,
                color: _DashboardColors.border,
              ),
            ),
            Row(
              children: [
                Expanded(
                  child: _TotalTrafficMetric(
                    icon: Icons.north_rounded,
                    color: _DashboardColors.green,
                    value: traffic == null ? '—' : formatBytes(traffic.down),
                  ),
                ),
                const _TinyDivider(),
                Expanded(
                  child: _TotalTrafficMetric(
                    icon: Icons.south_rounded,
                    color: _DashboardColors.purple,
                    value: traffic == null ? '—' : formatBytes(traffic.up),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: Container(
                width: double.infinity,
                height: 9,
                decoration: BoxDecoration(
                  color: const Color(0xFF1C2C43),
                  borderRadius: BorderRadius.circular(6),
                  gradient: traffic == null || sum == 0
                      ? null
                      : LinearGradient(
                          begin: Alignment.centerLeft,
                          end: Alignment.centerRight,
                          colors: const [
                            _DashboardColors.green,
                            _DashboardColors.green,
                            _DashboardColors.purple,
                            _DashboardColors.purple,
                          ],
                          stops: [0, downShare / 1000, downShare / 1000, 1],
                        ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

String _transportName(AppLocalizations l, String transport) =>
    switch (transport.trim().toLowerCase()) {
      'h1' || 'http/1.1' || 'http/1' => l.transportH1,
      'h3' || 'http/3' || 'http/3 (quic)' => l.transportH3,
      'h2' || 'http/2' || 'http/2 (tcp)' => l.transportH2,
      '' => '—',
      _ => transport,
    };

String _protocolLabel(BuildContext context, AppLocalizations l) =>
    Localizations.localeOf(context).languageCode == 'en'
    ? 'Protocol'
    : l.transport;

class _DashboardPanel extends StatelessWidget {
  const _DashboardPanel({
    required this.child,
    this.padding = const EdgeInsets.all(16),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) => Container(
    padding: padding,
    decoration: BoxDecoration(
      color: _DashboardColors.panel,
      borderRadius: BorderRadius.circular(15),
      border: Border.all(color: _DashboardColors.border),
      boxShadow: const [
        BoxShadow(
          color: Color(0x26000000),
          blurRadius: 18,
          offset: Offset(0, 7),
        ),
      ],
    ),
    child: child,
  );
}

class _DashboardPowerOrb extends StatelessWidget {
  const _DashboardPowerOrb({
    required this.on,
    required this.connected,
    required this.busy,
    required this.label,
    required this.accent,
    required this.tooltip,
    required this.onPressed,
  });

  final bool on;
  final bool connected;
  final bool busy;
  final String label;
  final Color accent;
  final String tooltip;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => Tooltip(
    message: tooltip,
    child: SizedBox.square(
      dimension: 294,
      child: Stack(
        alignment: Alignment.center,
        children: [
          if (!connected)
            Container(
              width: 280,
              height: 280,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: _DashboardColors.blue.withValues(alpha: 0.78),
                  width: 5,
                ),
                boxShadow: [
                  BoxShadow(
                    color: _DashboardColors.blue.withValues(alpha: 0.20),
                    blurRadius: 25,
                  ),
                ],
              ),
            ),
          if (!connected)
            Container(
              width: 245,
              height: 245,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: accent.withValues(alpha: 0.38),
                  width: 1.5,
                ),
              ),
            ),
          SizedBox.square(
            dimension: connected ? 210 : 224,
            child: Material(
              color: Colors.transparent,
              shape: const CircleBorder(),
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: busy ? null : onPressed,
                customBorder: const CircleBorder(),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 260),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: connected
                        ? null
                        : RadialGradient(
                            center: const Alignment(-0.3, -0.4),
                            radius: 1.1,
                            colors: on
                                ? [
                                    const Color(0xFF123B54),
                                    const Color(0xFF071A29),
                                  ]
                                : [
                                    const Color(0xFF1D2854),
                                    const Color(0xFF10172F),
                                  ],
                          ),
                    border: connected
                        ? null
                        : Border.all(
                            color: accent.withValues(alpha: 0.95),
                            width: 7,
                          ),
                    boxShadow: connected
                        ? []
                        : [
                            BoxShadow(
                              color: accent.withValues(alpha: on ? 0.34 : 0.18),
                              blurRadius: 34,
                              spreadRadius: 1,
                            ),
                          ],
                  ),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Opacity(
                        opacity: connected ? 0 : 1,
                        child: Icon(
                          Icons.power_settings_new_rounded,
                          size: connected ? 84 : 58,
                          color: on ? Colors.white : _DashboardColors.muted,
                        ),
                      ),
                      const SizedBox(height: 7),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 14),
                        child: Text(
                          label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.titleSmall
                              ?.copyWith(
                                fontSize: connected ? 20 : 18,
                                color: on ? accent : _DashboardColors.text,
                                fontWeight: FontWeight.w600,
                              ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          if (busy)
            const SizedBox.square(
              dimension: 280,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: _DashboardColors.blue,
              ),
            ),
        ],
      ),
    ),
  );
}

class _ModeTile extends StatelessWidget {
  const _ModeTile({
    required this.icon,
    required this.title,
    required this.selected,
    required this.accent,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final bool selected;
  final Color accent;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: selected ? const Color(0xFF0D1E38) : const Color(0xFF121D32),
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(13),
      side: BorderSide(
        color: selected ? accent : _DashboardColors.border,
        width: selected ? 1.5 : 1,
      ),
    ),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: SizedBox(
        height: 67,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 24,
              color: selected ? accent : _DashboardColors.muted,
            ),
            const SizedBox(width: 10),
            Flexible(
              child: Text(
                title,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: _DashboardColors.text,
                  fontSize: 18,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _DetailCell extends StatelessWidget {
  const _DetailCell({
    required this.label,
    required this.value,
    required this.leading,
    this.onTap,
  });

  final String label;
  final String value;
  final Widget leading;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.transparent,
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(9),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
        child: Row(
          children: [
            leading,
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelSmall
                        ?.copyWith(color: _DashboardColors.muted, fontSize: 13),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    value,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: _DashboardColors.text,
                      fontSize: 19,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            if (onTap != null)
              const Icon(
                Icons.chevron_right_rounded,
                size: 19,
                color: _DashboardColors.muted,
              ),
          ],
        ),
      ),
    ),
  );
}

class _DetailIcon extends StatelessWidget {
  const _DetailIcon({
    required this.icon,
    required this.background,
    required this.foreground,
  });

  final IconData icon;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
    width: 48,
    height: 48,
    alignment: Alignment.center,
    decoration: BoxDecoration(color: background, shape: BoxShape.circle),
    child: Icon(icon, size: 24, color: foreground),
  );
}

class _DetailDivider extends StatelessWidget {
  const _DetailDivider();

  @override
  Widget build(BuildContext context) => Container(
    width: 1,
    height: 71,
    margin: const EdgeInsets.symmetric(horizontal: 8),
    color: _DashboardColors.border,
  );
}

class _TinyDivider extends StatelessWidget {
  const _TinyDivider();

  @override
  Widget build(BuildContext context) => Container(
    width: 1,
    height: 36,
    margin: const EdgeInsets.symmetric(horizontal: 10),
    color: _DashboardColors.border,
  );
}

class _TinyMetric extends StatelessWidget {
  const _TinyMetric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.labelSmall
            ?.copyWith(color: _DashboardColors.muted, fontSize: 14),
      ),
      const SizedBox(height: 5),
      Text(
        value,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: _DashboardColors.text,
          fontSize: 18,
          fontWeight: FontWeight.w600,
          fontFeatures: const [FontFeature.tabularFigures()],
        ),
      ),
    ],
  );
}

class _TotalTrafficMetric extends StatelessWidget {
  const _TotalTrafficMetric({
    required this.icon,
    required this.color,
    required this.value,
  });

  final IconData icon;
  final Color color;
  final String value;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Icon(icon, size: 24, color: color),
      const SizedBox(width: 7),
      Flexible(
        child: Text(
          value,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: _DashboardColors.text,
            fontSize: 20,
            fontWeight: FontWeight.w500,
            fontFeatures: const [FontFeature.tabularFigures()],
          ),
        ),
      ),
    ],
  );
}

class _TrafficMetric extends StatelessWidget {
  const _TrafficMetric({
    required this.icon,
    required this.color,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final Color color;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Row(
        children: [
          Icon(icon, size: 24, color: color),
          const SizedBox(width: 7),
          Expanded(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelMedium
                  ?.copyWith(color: _DashboardColors.muted, fontSize: 13.5),
            ),
          ),
        ],
      ),
      const SizedBox(height: 2),
      Text(
        value,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.titleMedium?.copyWith(
          color: color,
          fontSize: 20,
          fontWeight: FontWeight.w500,
          fontFeatures: const [FontFeature.tabularFigures()],
        ),
      ),
    ],
  );
}

class _QuickLinkData {
  const _QuickLinkData({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.color,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final Color color;
  final VoidCallback? onTap;
}

class _QuickLink extends StatelessWidget {
  const _QuickLink({required this.data});

  final _QuickLinkData data;

  @override
  Widget build(BuildContext context) => Material(
    color: const Color(0xFF0A1525),
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(13),
      side: const BorderSide(color: _DashboardColors.border),
    ),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: data.onTap,
      child: SizedBox(
        height: 104,
        child: Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(16, 11, 9, 11),
          child: Row(
            children: [
              Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: data.color.withValues(alpha: 0.18),
                  borderRadius: BorderRadius.circular(13),
                  border: Border.all(color: data.color.withValues(alpha: 0.22)),
                ),
                alignment: Alignment.center,
                child: Icon(data.icon, color: data.color, size: 32),
              ),
              const SizedBox(width: 18),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      data.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        color: _DashboardColors.text,
                        fontSize: 18,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      data.subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: _DashboardColors.muted,
                        fontSize: 14.5,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 5),
              const Icon(
                Icons.chevron_right_rounded,
                color: _DashboardColors.muted,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _TrafficSparkline extends StatefulWidget {
  const _TrafficSparkline({required this.core});

  final CoreController core;

  @override
  State<_TrafficSparkline> createState() => _TrafficSparklineState();
}

class _TrafficSparklineState extends State<_TrafficSparkline> {
  final List<({double down, double up})> _history = [];

  @override
  void initState() {
    super.initState();
    widget.core.addListener(_observe);
    _record(widget.core.status?.traffic);
  }

  @override
  void didUpdateWidget(covariant _TrafficSparkline oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.core != widget.core) {
      oldWidget.core.removeListener(_observe);
      widget.core.addListener(_observe);
      _record(widget.core.status?.traffic);
    }
  }

  void _observe() {
    final traffic = widget.core.status?.traffic;
    if (traffic == null) return;
    if (_history.isNotEmpty &&
        _history.last.down == traffic.downRate.toDouble() &&
        _history.last.up == traffic.upRate.toDouble()) {
      return;
    }
    _record(traffic);
    if (mounted) setState(() {});
  }

  void _record(Traffic? traffic) {
    if (traffic == null) return;
    _history.add((
      down: traffic.downRate.toDouble(),
      up: traffic.upRate.toDouble(),
    ));
    if (_history.length > 28) _history.removeAt(0);
  }

  @override
  void dispose() {
    widget.core.removeListener(_observe);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => CustomPaint(
    painter: _TrafficSparklinePainter(List.of(_history)),
    child: const SizedBox.expand(),
  );
}

class _TrafficSparklinePainter extends CustomPainter {
  const _TrafficSparklinePainter(this.samples);

  final List<({double down, double up})> samples;

  @override
  void paint(Canvas canvas, Size size) {
    final baseline = Paint()
      ..color = const Color(0xFF14243A)
      ..strokeWidth = 1;
    canvas.drawLine(
      Offset(0, size.height - 1),
      Offset(size.width, size.height - 1),
      baseline,
    );
    if (samples.length < 2 || size.isEmpty) return;
    var maximum = 1.0;
    for (final sample in samples) {
      if (sample.down > maximum) maximum = sample.down;
      if (sample.up > maximum) maximum = sample.up;
    }
    _drawSeries(
      canvas,
      size,
      [for (final sample in samples) sample.down],
      _DashboardColors.green,
      maximum,
    );
    _drawSeries(
      canvas,
      size,
      [for (final sample in samples) sample.up],
      _DashboardColors.blue,
      maximum,
    );
  }

  void _drawSeries(
    Canvas canvas,
    Size size,
    List<double> values,
    Color color,
    double maximum,
  ) {
    final points = [
      for (var i = 0; i < values.length; i++)
        Offset(
          i * size.width / (values.length - 1),
          size.height - 4 - (values[i] / maximum) * (size.height - 12),
        ),
    ];
    final path = Path()..moveTo(points.first.dx, points.first.dy);
    for (var i = 1; i < points.length; i++) {
      final previous = points[i - 1];
      final current = points[i];
      final midpoint = Offset(
        (previous.dx + current.dx) / 2,
        (previous.dy + current.dy) / 2,
      );
      path.quadraticBezierTo(
        previous.dx,
        previous.dy,
        midpoint.dx,
        midpoint.dy,
      );
    }
    path.lineTo(points.last.dx, points.last.dy);
    final area = Path.from(path)
      ..lineTo(points.last.dx, size.height)
      ..lineTo(points.first.dx, size.height)
      ..close();
    canvas.drawPath(
      area,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            color.withValues(alpha: 0.22),
            color.withValues(alpha: 0.015),
          ],
        ).createShader(Offset.zero & size),
    );
    canvas.drawPath(
      path,
      Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(covariant _TrafficSparklinePainter oldDelegate) =>
      !listEquals(oldDelegate.samples, samples);
}

class _DashboardSwooshPainter extends CustomPainter {
  const _DashboardSwooshPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final stroke = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round;
    final path = Path()
      ..moveTo(size.width * 0.03, size.height * 0.8)
      ..cubicTo(
        size.width * 0.22,
        size.height * 0.37,
        size.width * 0.62,
        size.height * 0.34,
        size.width * 0.97,
        size.height * 0.08,
      );
    canvas.drawPath(path, stroke);
  }

  @override
  bool shouldRepaint(covariant _DashboardSwooshPainter oldDelegate) =>
      oldDelegate.color != color;
}

class _DashboardAmbientPainter extends CustomPainter {
  const _DashboardAmbientPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final blue = Paint()
      ..shader =
          const RadialGradient(
            colors: [Color(0x1E147CF2), Color(0x000A2A58)],
          ).createShader(
            Rect.fromCircle(center: Offset(size.width * 0.74, 50), radius: 380),
          );
    canvas.drawCircle(Offset(size.width * 0.74, 50), 380, blue);
    final violet = Paint()
      ..shader =
          const RadialGradient(colors: [Color(0x121D2FFF), Color(0x000A2A58)])
              .createShader(
                Rect.fromCircle(
                  center: Offset(size.width * 0.22, size.height * 0.8),
                  radius: 330,
                ),
              );
    canvas.drawCircle(
      Offset(size.width * 0.22, size.height * 0.8),
      330,
      violet,
    );
  }

  @override
  bool shouldRepaint(covariant _DashboardAmbientPainter oldDelegate) => false;
}

class _DashboardMountainsPainter extends CustomPainter {
  const _DashboardMountainsPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final sky = Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [Color(0xFF081731), Color(0xFF071323), Color(0x00071323)],
        stops: [0, 0.55, 1],
      ).createShader(Offset.zero & size);
    canvas.drawRect(Offset.zero & size, sky);

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
      Offset(0, .63),
      Offset(.07, .53),
      Offset(.15, .58),
      Offset(.24, .43),
      Offset(.32, .59),
      Offset(.42, .51),
      Offset(.51, .65),
      Offset(.62, .47),
      Offset(.72, .58),
      Offset(.81, .40),
      Offset(.91, .56),
      Offset(1, .45),
    ], const Color(0xFF102A5A));
    ridge(const [
      Offset(0, .72),
      Offset(.10, .64),
      Offset(.20, .68),
      Offset(.32, .58),
      Offset(.43, .68),
      Offset(.55, .60),
      Offset(.66, .69),
      Offset(.78, .59),
      Offset(.90, .66),
      Offset(1, .58),
    ], const Color(0xFF0B2047));
    ridge(const [
      Offset(0, .82),
      Offset(.09, .76),
      Offset(.17, .79),
      Offset(.29, .72),
      Offset(.38, .81),
      Offset(.50, .72),
      Offset(.60, .82),
      Offset(.72, .74),
      Offset(.81, .82),
      Offset(.92, .75),
      Offset(1, .81),
    ], const Color(0xFF08162D));
  }

  @override
  bool shouldRepaint(covariant _DashboardMountainsPainter oldDelegate) => false;
}
