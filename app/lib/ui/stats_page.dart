import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/core_controller.dart';
import '../l10n/app_localizations.dart';
import 'format.dart';
import 'mobile_visuals.dart';

/// Traffic figures and the latest connection test reported by the core.
class StatsPage extends StatefulWidget {
  const StatsPage({super.key, required this.core});

  final CoreController core;

  @override
  State<StatsPage> createState() => _StatsPageState();
}

class _StatsPageState extends State<StatsPage> {
  static const _sampleInterval = Duration(seconds: 1);
  static const _maxSamples = 36;

  final List<_RateSample> _history = [];
  DateTime? _lastSampleAt;

  @override
  void initState() {
    super.initState();
    widget.core.addListener(_coreChanged);
    _captureSample(force: true);
  }

  @override
  void dispose() {
    widget.core.removeListener(_coreChanged);
    super.dispose();
  }

  void _coreChanged() {
    if (!mounted) return;
    _captureSample();
    setState(() {});
  }

  void _captureSample({bool force = false}) {
    final traffic = widget.core.status?.traffic;
    if (traffic == null) return;
    final now = DateTime.now();
    final last = _lastSampleAt;
    if (!force && last != null && now.difference(last) < _sampleInterval) {
      return;
    }
    _lastSampleAt = now;
    _history.add(_RateSample(traffic.downRate, traffic.upRate));
    if (_history.length > _maxSamples) _history.removeAt(0);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = widget.core.status;
    final traffic = status?.traffic;
    final total = traffic == null ? null : traffic.down + traffic.up;
    final test = status?.lastTest;
    final text = const Color(0xFFF1F5FF);
    final muted = const Color(0xFFA2B1D0);

    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 12, 18, 24),
      children: [
        ClxyMobilePageHeading(title: l.statsTitle, subtitle: l.statsSubtitle),
        const SizedBox(height: 18),
        Row(
          children: [
            Expanded(
              child: _TrafficCard(
                label: l.download,
                value: traffic == null ? '—' : formatBytes(traffic.down),
                icon: Icons.download_rounded,
                accent: const Color(0xFF36DDF4),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _TrafficCard(
                label: l.upload,
                value: traffic == null ? '—' : formatBytes(traffic.up),
                icon: Icons.upload_rounded,
                accent: const Color(0xFFA184FF),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        _SummaryCard(
          label: l.totalTraffic,
          value: total == null ? '—' : formatBytes(total),
        ),
        const SizedBox(height: 14),
        _StatsPanel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                l.statsLiveSpeed,
                style: theme.textTheme.titleMedium?.copyWith(
                  color: text,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                l.statsHistoryNote,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelSmall?.copyWith(color: muted),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: _RateValue(
                      label: l.download,
                      value: traffic == null
                          ? '—'
                          : formatRate(traffic.downRate),
                      color: const Color(0xFF36DDF4),
                      alignment: CrossAxisAlignment.start,
                    ),
                  ),
                  Expanded(
                    child: _RateValue(
                      label: l.upload,
                      value: traffic == null ? '—' : formatRate(traffic.upRate),
                      color: const Color(0xFFA184FF),
                      alignment: CrossAxisAlignment.end,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              SizedBox(
                height: 152,
                child: _RateChart(samples: List.unmodifiable(_history)),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        _StatsPanel(
          child: Row(
            children: [
              Icon(
                Icons.speed_rounded,
                color: const Color(0xFF9A8BFF),
                size: 26,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l.statsLastTest,
                      style: theme.textTheme.titleSmall?.copyWith(
                        color: text,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    if (test?.at != null) ...[
                      const SizedBox(height: 3),
                      Text(
                        l.testedAgo(formatAgo(l, test!.at!)),
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: muted,
                        ),
                      ),
                    ] else ...[
                      const SizedBox(height: 3),
                      Text(
                        l.statsAfterTest,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: muted,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              Text(
                test == null || test.totalMs <= 0
                    ? '—'
                    : ltr('${test.totalMs} ms'),
                textDirection: TextDirection.ltr,
                style: theme.textTheme.titleMedium?.copyWith(
                  color: text,
                  fontWeight: FontWeight.w700,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _TrafficCard extends StatelessWidget {
  const _TrafficCard({
    required this.label,
    required this.value,
    required this.icon,
    required this.accent,
  });

  final String label;
  final String value;
  final IconData icon;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return _StatsPanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Icon(icon, color: accent, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: const Color(0xFFA2B1D0),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textDirection: TextDirection.ltr,
            textAlign: TextAlign.start,
            style: theme.textTheme.titleLarge?.copyWith(
              color: const Color(0xFFF1F5FF),
              fontWeight: FontWeight.w800,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return _StatsPanel(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 17),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: const Color(0xFFA2B1D0),
              ),
            ),
          ),
          Text(
            value,
            textDirection: TextDirection.ltr,
            style: theme.textTheme.titleMedium?.copyWith(
              color: const Color(0xFFF1F5FF),
              fontWeight: FontWeight.w700,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    );
  }
}

class _RateValue extends StatelessWidget {
  const _RateValue({
    required this.label,
    required this.value,
    required this.color,
    required this.alignment,
  });

  final String label;
  final String value;
  final Color color;
  final CrossAxisAlignment alignment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: alignment,
      children: [
        Text(label, style: theme.textTheme.labelSmall?.copyWith(color: color)),
        Text(
          value,
          textDirection: TextDirection.ltr,
          style: theme.textTheme.titleSmall?.copyWith(
            color: color,
            fontWeight: FontWeight.w700,
            fontFeatures: const [FontFeature.tabularFigures()],
          ),
        ),
      ],
    );
  }
}

class _StatsPanel extends StatelessWidget {
  const _StatsPanel({
    required this.child,
    this.padding = const EdgeInsets.all(17),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) =>
      ClxyGlassSurface(padding: padding, radius: 22, child: child);
}

class _RateSample {
  const _RateSample(this.down, this.up);

  final int down;
  final int up;
}

class _RateChart extends StatelessWidget {
  const _RateChart({required this.samples});

  final List<_RateSample> samples;

  @override
  Widget build(BuildContext context) {
    final useful =
        samples.length >= 2 &&
        samples.any((sample) => sample.down > 0 || sample.up > 0);
    if (!useful) {
      return Container(
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xB80B1F3B), Color(0xB8070D1B)],
          ),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: ClxyMobileColors.line.withValues(alpha: .6),
          ),
        ),
        child: Stack(
          fit: StackFit.expand,
          children: [
            const CustomPaint(painter: _EmptyRateGrid()),
            Center(
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(
                      Icons.auto_awesome,
                      color: Color(0xFF65DFF2),
                      size: 22,
                    ),
                    const SizedBox(width: 10),
                    Flexible(
                      child: Text(
                        AppLocalizations.of(context).statsHistoryNote,
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodySmall
                            ?.copyWith(color: ClxyMobileColors.muted),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    }
    return Container(
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xB80B1F3B), Color(0xB8070D1B)],
        ),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: ClxyMobileColors.line.withValues(alpha: .6)),
      ),
      child: CustomPaint(
        painter: _RateChartPainter(samples),
        size: Size.infinite,
      ),
    );
  }
}

class _EmptyRateGrid extends CustomPainter {
  const _EmptyRateGrid();

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = const Color(0x199FB7E0)
      ..strokeWidth = 1;
    for (var i = 1; i < 4; i++) {
      final y = size.height * i / 4;
      canvas.drawLine(Offset(12, y), Offset(size.width - 12, y), paint);
    }
  }

  @override
  bool shouldRepaint(_EmptyRateGrid oldDelegate) => false;
}

class _RateChartPainter extends CustomPainter {
  const _RateChartPainter(this.samples);

  final List<_RateSample> samples;

  @override
  void paint(Canvas canvas, Size size) {
    const horizontalInset = 4.0;
    const verticalInset = 8.0;
    final width = size.width - horizontalInset * 2;
    final height = size.height - verticalInset * 2;
    final grid = Paint()
      ..color = const Color(0xFF26364D)
      ..strokeWidth = 1;
    for (var i = 0; i < 4; i++) {
      final y = verticalInset + height * i / 3;
      canvas.drawLine(
        Offset(horizontalInset, y),
        Offset(size.width - horizontalInset, y),
        grid,
      );
    }

    final maximum = samples
        .map((sample) => math.max(sample.down, sample.up))
        .fold<int>(0, math.max)
        .toDouble();
    if (maximum <= 0) return;

    Path pathFor(int Function(_RateSample) valueOf) {
      final path = Path();
      for (var i = 0; i < samples.length; i++) {
        final x = horizontalInset + width * i / (samples.length - 1);
        final fraction = (valueOf(samples[i]) / maximum).clamp(0.0, 1.0);
        final y = verticalInset + height * (1 - fraction);
        if (i == 0) {
          path.moveTo(x, y);
        } else {
          path.lineTo(x, y);
        }
      }
      return path;
    }

    final downPaint = Paint()
      ..color = const Color(0xFF36DDF4)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.5
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final upPaint = Paint()
      ..color = const Color(0xFFA184FF)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.2
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    canvas.drawPath(pathFor((sample) => sample.down), downPaint);
    canvas.drawPath(pathFor((sample) => sample.up), upPaint);
  }

  @override
  bool shouldRepaint(_RateChartPainter oldDelegate) =>
      !identical(samples, oldDelegate.samples);
}
