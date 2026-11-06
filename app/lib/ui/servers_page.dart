import 'dart:async';

import 'package:flutter/material.dart';

import '../app_settings.dart';
import '../core/core_api.dart';
import '../core/core_controller.dart';
import '../core/models.dart';
import '../l10n/app_localizations.dart';
import 'format.dart';
import 'mobile_visuals.dart';
import 'widgets.dart';

/// The server list, one choice in one list: Recommended and Fastest, which
/// the app picks, then the locations with their real delay. A tap on a row
/// chooses it (`proxy.country`: empty for Mozilla's recommended location,
/// `fastest` for the lowest real delay, otherwise a country).
class ServersPage extends StatefulWidget {
  const ServersPage({super.key, required this.core, required this.settings});

  final CoreController core;
  final AppSettings settings;

  @override
  State<ServersPage> createState() => _ServersPageState();
}

/// How old measured delays are before the page measures them again when
/// it opens.
const _pingStale = Duration(minutes: 10);

const _nightText = ClxyMobileColors.text;
const _nightMuted = ClxyMobileColors.muted;
const _nightLine = ClxyMobileColors.lineStrong;
const _nightBlue = ClxyMobileColors.blue;
const _nightViolet = ClxyMobileColors.violet;
const _nightGreen = ClxyMobileColors.green;

class _ServersPageState extends State<ServersPage> {
  List<Location>? _locations;
  DateTime? _pingedAt;
  String _country = '';
  bool _testing = false;

  /// The choice being saved ('' recommended, 'fastest', or a code).
  String? _busy;
  String? _summary;

  CoreController get _core => widget.core;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final list = await _core.call((api) => api.locations());
      final config = await _core.settings();
      if (!mounted) return;
      setState(() {
        _locations = list.locations;
        _pingedAt = list.pingedAt;
        if (config['proxy'] case {'country': final String country}) {
          _country = country;
        }
      });
      // Delays measured a while ago, or never: measured again at once (the
      // core does it also while the proxy is off).
      final at = _pingedAt;
      if (at == null || DateTime.now().difference(at) > _pingStale) {
        unawaited(_test());
      }
    } on CoreApiException catch (e) {
      if (mounted) showError(context, e.message);
    } on CoreUnavailable {
      // The shell shows it.
    }
  }

  Future<void> _choose(String country) async {
    if (_busy != null || _country.toLowerCase() == country.toLowerCase()) {
      return;
    }
    setState(() => _busy = country);
    final ok = await runShowingErrors(
      context,
      () => _core.setLocation(country),
    );
    if (!mounted) return;
    setState(() {
      _busy = null;
      if (ok) _country = country;
    });
  }

  Future<void> _test() async {
    final l = AppLocalizations.of(context);
    final codes = [
      for (final loc in _locations ?? const <Location>[]) loc.code,
    ];
    setState(() {
      _testing = true;
      _summary = null;
    });
    try {
      final result = await _core.call((api) => api.pingLocations(codes));
      final tested = {for (final loc in result.locations) loc.code: loc};
      if (!mounted) return;
      setState(() {
        _locations = [
          for (final loc in _locations ?? const <Location>[])
            tested[loc.code] ?? loc,
        ];
        _pingedAt = result.pingedAt ?? DateTime.now();
        final answered = result.locations.where((loc) => loc.answered).length;
        _summary = l.delaySummary(answered, result.locations.length);
      });
    } on CoreApiException catch (e) {
      if (mounted) showError(context, e.message);
    } on CoreUnavailable catch (e) {
      if (mounted) showError(context, describeProblem(l, e));
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  /// Every country, without Mozilla's recommended location (the choice
  /// above), fastest first, then by name in the app's language.
  List<Location> _countries(AppLocalizations l) {
    final list = [
      for (final loc in _locations ?? const <Location>[])
        if (!isRecommendedPlace(loc.code)) loc,
    ];
    list.sort((a, b) {
      final da = a.answered ? a.pingMs : 1 << 30;
      final db = b.answered ? b.pingMs : 1 << 30;
      if (da != db) return da.compareTo(db);
      return nameSortKey(
        l.localeName,
        locationName(l, a.code, a.name),
      ).compareTo(nameSortKey(l.localeName, locationName(l, b.code, b.name)));
    });
    return list;
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final dark = theme.brightness == Brightness.dark;
    final phoneRoute = OwnTopBar.of(context);
    final muted = dark ? _nightMuted : scheme.onSurfaceVariant;
    final chosen = _country.toLowerCase();
    final countries = _countries(l);
    final recommended = (_locations ?? const <Location>[])
        .where((loc) => isRecommendedPlace(loc.code))
        .firstOrNull;
    final delayLine =
        _summary ??
        (_pingedAt == null ? null : l.testedAgo(formatAgo(l, _pingedAt!)));

    return Stack(
      fit: StackFit.expand,
      children: [
        if (phoneRoute) ...[
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
          Positioned.fill(
            child: IgnorePointer(
              child: ColoredBox(
                color: dark ? const Color(0xC8050B16) : const Color(0xEAF4F7FC),
              ),
            ),
          ),
          if (dark)
            Positioned(
              top: -92,
              right: -78,
              child: IgnorePointer(
                child: Container(
                  width: 248,
                  height: 248,
                  decoration: const BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [Color(0x443786FF), Colors.transparent],
                    ),
                  ),
                ),
              ),
            ),
        ],
        ListView(
          padding: phoneRoute
              ? const EdgeInsets.fromLTRB(18, 7, 18, 22)
              : pagePadding(context),
          children: [
            if (phoneRoute)
              ClxyMobilePageHeading(
                title: l.navServers,
                subtitle: l.statsSubtitle,
              )
            else
              PageTitle(l.navServers),
            _SectionHeader(
              title: l.autoSection,
              compact: true,
              action: Icon(
                Icons.alt_route_rounded,
                size: 15,
                color: dark ? _nightBlue : scheme.primary,
              ),
            ),
            Row(
              children: [
                Expanded(
                  child: _ModeCard(
                    title: l.modeRecommended,
                    subtitle: l.modeRecommendedHint,
                    icon: Icons.auto_awesome_rounded,
                    accent: dark ? _nightBlue : scheme.primary,
                    selected: chosen == '',
                    busy: _busy == '',
                    dark: dark,
                    footer: recommended == null
                        ? null
                        : _Delay(
                            location: recommended,
                            testing: _testing,
                            dark: dark,
                            badge: true,
                          ),
                    onTap: () => _choose(''),
                  ),
                ),
                const SizedBox(width: 9),
                Expanded(
                  child: _ModeCard(
                    title: l.modeFastest,
                    subtitle: l.modeFastestHint,
                    icon: Icons.bolt_rounded,
                    accent: dark ? _nightViolet : scheme.tertiary,
                    selected: chosen == 'fastest',
                    busy: _busy == 'fastest',
                    dark: dark,
                    onTap: () => _choose('fastest'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            _SectionHeader(
              title: l.locationsSection,
              action: OutlinedButton.icon(
                onPressed: !_testing && _locations != null ? _test : null,
                style: OutlinedButton.styleFrom(
                  foregroundColor: dark
                      ? const Color(0xFFB5BEFF)
                      : scheme.primary,
                  backgroundColor: dark
                      ? _nightViolet.withValues(alpha: 0.12)
                      : scheme.primary.withValues(alpha: 0.06),
                  side: BorderSide(
                    color: (dark ? _nightViolet : scheme.primary).withValues(
                      alpha: 0.34,
                    ),
                  ),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 9,
                    vertical: 5,
                  ),
                  minimumSize: const Size(0, 30),
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  textStyle: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                icon: _testing
                    ? const SizedBox.square(
                        dimension: 14,
                        child: CircularProgressIndicator(strokeWidth: 1.8),
                      )
                    : const Icon(Icons.speed_rounded, size: 15),
                label: Text(_testing ? l.testingDelay : l.testDelay),
              ),
            ),
            if (delayLine != null)
              Padding(
                padding: const EdgeInsetsDirectional.fromSTEB(3, 0, 3, 8),
                child: Row(
                  children: [
                    Container(
                      width: 5,
                      height: 5,
                      decoration: BoxDecoration(
                        color: _testing
                            ? (dark
                                  ? const Color(0xFFFFB547)
                                  : StatusColors.busy)
                            : (dark ? _nightGreen : StatusColors.good),
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 7),
                    Expanded(
                      child: Text(
                        delayLine,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: muted,
                          fontSize: 10,
                        ),
                      ),
                    ),
                  ],
                ),
              )
            else
              const SizedBox(height: 4),
            if (_locations == null)
              const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (countries.isEmpty)
              _ChoiceCard(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(18),
                    child: Text(l.noServers, style: TextStyle(color: muted)),
                  ),
                ],
              )
            else
              Column(
                children: [
                  for (final loc in countries)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _ChoiceCard(
                        children: [
                          _ChoiceRow(
                            leading: CountryFlag(code: loc.code, width: 26),
                            title: locationName(l, loc.code, loc.name),
                            delay: _Delay(
                              location: loc,
                              testing: _testing,
                              dark: dark,
                            ),
                            selected: chosen == loc.code.toLowerCase(),
                            busy:
                                _busy?.toLowerCase() == loc.code.toLowerCase(),
                            dark: dark,
                            onTap: () => _choose(loc.code),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
          ],
        ),
      ],
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({
    required this.title,
    this.action,
    this.compact = false,
  });

  final String title;
  final Widget? action;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        2,
        compact ? 3 : 12,
        2,
        compact ? 8 : 7,
      ),
      child: SizedBox(
        height: compact ? 16 : 27,
        child: Row(
          children: [
            Expanded(
              child: Text(
                title,
                style: compact
                    ? theme.textTheme.labelSmall?.copyWith(
                        color: dark ? _nightMuted : theme.colorScheme.primary,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.2,
                      )
                    : theme.textTheme.titleMedium?.copyWith(
                        color: dark ? _nightText : theme.colorScheme.onSurface,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.2,
                      ),
              ),
            ),
            ?action,
          ],
        ),
      ),
    );
  }
}

class _ModeCard extends StatelessWidget {
  const _ModeCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.accent,
    required this.selected,
    required this.busy,
    required this.dark,
    required this.onTap,
    this.footer,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final Color accent;
  final bool selected;
  final bool busy;
  final bool dark;
  final VoidCallback onTap;
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = dark ? _nightText : scheme.onSurface;
    final muted = dark ? _nightMuted : scheme.onSurfaceVariant;
    final border = selected
        ? accent.withValues(alpha: 0.78)
        : (dark ? _nightLine : scheme.outlineVariant.withValues(alpha: 0.7));
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(20),
          child: Ink(
            height: 112,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: border),
              gradient: dark
                  ? LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: selected
                          ? const [Color(0xF20D2648), Color(0xF0081528)]
                          : const [Color(0xE80D1B31), Color(0xE807111F)],
                    )
                  : LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: selected
                          ? [accent.withValues(alpha: 0.11), scheme.surface]
                          : [scheme.surface, scheme.surfaceContainerLowest],
                    ),
              boxShadow: dark
                  ? const [
                      BoxShadow(
                        color: Color(0x24000000),
                        blurRadius: 18,
                        offset: Offset(0, 7),
                      ),
                    ]
                  : null,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 24,
                      height: 24,
                      decoration: BoxDecoration(
                        color: accent.withValues(alpha: dark ? 0.16 : 0.11),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: accent.withValues(alpha: 0.22),
                        ),
                      ),
                      child: Icon(icon, size: 14, color: accent),
                    ),
                    const Spacer(),
                    _SelectionMark(
                      selected: selected,
                      busy: busy,
                      accent: accent,
                      dark: dark,
                      size: 17,
                    ),
                  ],
                ),
                const SizedBox(height: 5),
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: text,
                    fontSize: 13,
                    height: 1.05,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.15,
                  ),
                ),
                const SizedBox(height: 3),
                Expanded(
                  child: Text(
                    subtitle,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: muted,
                      fontSize: 10,
                      height: 1.2,
                      fontWeight: FontWeight.w400,
                    ),
                  ),
                ),
                if (footer != null) ...[
                  const SizedBox(height: 3),
                  Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: footer!,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The locations form one restrained, outlined surface with fine row rules.
class _ChoiceCard extends StatelessWidget {
  const _ChoiceCard({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    return Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        gradient: dark
            ? const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xF2131E35), Color(0xF2091020)],
              )
            : null,
        color: dark ? null : theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: dark
              ? _nightLine.withValues(alpha: 0.82)
              : theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
        ),
      ),
      child: Column(
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0)
              Divider(
                height: 1,
                thickness: 1,
                color: dark
                    ? _nightLine.withValues(alpha: 0.65)
                    : theme.colorScheme.outlineVariant.withValues(alpha: 0.4),
              ),
            children[i],
          ],
        ],
      ),
    );
  }
}

/// One location row: flag, live name and delay, and a tappable selection mark.
class _ChoiceRow extends StatelessWidget {
  const _ChoiceRow({
    required this.leading,
    required this.title,
    required this.delay,
    required this.selected,
    required this.busy,
    required this.dark,
    required this.onTap,
  });

  final Widget leading;
  final String title;
  final Widget delay;
  final bool selected;
  final bool busy;
  final bool dark;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final text = dark ? _nightText : scheme.onSurface;
    final accent = dark ? _nightBlue : scheme.primary;
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: selected
            ? accent.withValues(alpha: dark ? 0.08 : 0.07)
            : Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(8, 11, 7, 11),
            child: Row(
              children: [
                leading,
                const SizedBox(width: 9),
                Expanded(
                  child: Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: text,
                      fontSize: 12.5,
                      fontWeight: selected ? FontWeight.w700 : FontWeight.w600,
                      letterSpacing: -0.1,
                    ),
                  ),
                ),
                const SizedBox(width: 7),
                SizedBox(
                  width: 62,
                  child: Align(
                    alignment: AlignmentDirectional.centerEnd,
                    child: delay,
                  ),
                ),
                const SizedBox(width: 8),
                _SelectionMark(
                  selected: selected,
                  busy: busy,
                  accent: accent,
                  dark: dark,
                  size: 18,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SelectionMark extends StatelessWidget {
  const _SelectionMark({
    required this.selected,
    required this.busy,
    required this.accent,
    required this.dark,
    required this.size,
  });

  final bool selected;
  final bool busy;
  final Color accent;
  final bool dark;
  final double size;

  @override
  Widget build(BuildContext context) {
    if (busy) {
      return SizedBox.square(
        dimension: size,
        child: Padding(
          padding: const EdgeInsets.all(2),
          child: CircularProgressIndicator(strokeWidth: 1.8, color: accent),
        ),
      );
    }
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(
          color: selected
              ? accent
              : (dark
                    ? const Color(0xFF63728B)
                    : Theme.of(context).colorScheme.outline),
          width: selected ? 1.8 : 1.3,
        ),
        color: selected ? accent.withValues(alpha: 0.12) : Colors.transparent,
      ),
      alignment: Alignment.center,
      child: selected
          ? Container(
              width: size * 0.42,
              height: size * 0.42,
              decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
            )
          : null,
    );
  }
}

/// A location's real delay; no fabricated values are shown. While testing,
/// a spinner replaces the previous result. A silent timeout stays neutral.
class _Delay extends StatelessWidget {
  const _Delay({
    required this.location,
    required this.testing,
    required this.dark,
    this.badge = false,
  });

  final Location location;
  final bool testing;
  final bool dark;
  final bool badge;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final loc = location;
    final color = testing
        ? theme.colorScheme.onSurfaceVariant
        : loc.answered
        ? dark
              ? _nightGreen
              : delayColor(loc.pingMs)
        : theme.colorScheme.onSurfaceVariant;
    final text = testing
        ? ''
        : loc.answered
        ? l.milliseconds(loc.pingMs)
        : loc.pingError.isNotEmpty
        ? l.noAnswer
        : '—';
    return Container(
      padding: badge
          ? const EdgeInsets.symmetric(horizontal: 7, vertical: 4)
          : EdgeInsets.zero,
      decoration: badge
          ? BoxDecoration(
              color: color.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(999),
            )
          : null,
      child: testing
          ? SizedBox.square(
              dimension: 12,
              child: CircularProgressIndicator(
                strokeWidth: 1.5,
                color: dark ? _nightMuted : color,
              ),
            )
          : Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.labelMedium?.copyWith(
                color: color,
                fontSize: badge ? 10 : 10.5,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.05,
              ),
            ),
    );
  }
}
