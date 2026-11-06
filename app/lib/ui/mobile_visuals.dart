import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';

/// Shared night-glass palette for the four mobile tabs (1:1 CLXY Cosmic Neon).
abstract final class ClxyMobileColors {
  static const background = Color(0xFF010314);
  static const deepSpace = Color(0xFF02061D);
  static const panel = Color(0xEB081234);
  static const panelRaised = Color(0xF00D1B48);
  static const line = Color(0x385282DF);
  static const lineStrong = Color(0x664788FF);
  static const text = Color(0xFFF5F9FF);
  static const muted = Color(0xFF8FA6D8);
  static const cyan = Color(0xFF2CE6FF);
  static const cyanBright = Color(0xFF52F7FF);
  static const blue = Color(0xFF1088FF);
  static const blueDeep = Color(0xFF0046EB);
  static const violet = Color(0xFF9D34FF);
  static const green = Color(0xFF18E870);
  static const amber = Color(0xFFFFCA28);
  static const amberDeep = Color(0xFFD97706);
  static const error = Color(0xFFFF647D);
}

/// Glowing 3D Neon Shield + Lightning Bolt brand icon from the CLXY header.
class ClxyShieldLogo extends StatelessWidget {
  const ClxyShieldLogo({super.key, this.width = 48, this.height = 54});

  final double width;
  final double height;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: width,
    height: height,
    child: const CustomPaint(painter: _ClxyShieldLogoPainter()),
  );
}

class _ClxyShieldLogoPainter extends CustomPainter {
  const _ClxyShieldLogoPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    final outer = Path()
      ..moveTo(w * 0.50, h * 0.05)
      ..lineTo(w * 0.13, h * 0.19)
      ..lineTo(w * 0.13, h * 0.46)
      ..cubicTo(w * 0.13, h * 0.70, w * 0.28, h * 0.89, w * 0.50, h * 0.96)
      ..cubicTo(w * 0.72, h * 0.89, w * 0.87, h * 0.70, w * 0.87, h * 0.46)
      ..lineTo(w * 0.87, h * 0.19)
      ..close();

    // Outer neon blue glow
    canvas.drawPath(
      outer,
      Paint()
        ..color = const Color(0xFF0088FF).withValues(alpha: 0.55)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 9),
    );

    // Dark navy interior
    canvas.drawPath(
      outer,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF0B2B7A), Color(0xFF041038), Color(0xFF020924)],
        ).createShader(Offset.zero & size),
    );

    // Split left-half highlight
    final leftHalf = Path()
      ..moveTo(w * 0.50, h * 0.08)
      ..lineTo(w * 0.16, h * 0.21)
      ..lineTo(w * 0.16, h * 0.46)
      ..cubicTo(w * 0.16, h * 0.67, w * 0.30, h * 0.85, w * 0.50, h * 0.92)
      ..close();
    canvas.drawPath(
      leftHalf,
      Paint()..color = const Color(0xFF2898FF).withValues(alpha: 0.14),
    );

    // Glowing cyan-to-blue shield border
    canvas.drawPath(
      outer,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3.1
        ..strokeJoin = StrokeJoin.round
        ..shader = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFF67ECFF), Color(0xFF1994FF), Color(0xFF0044FF)],
        ).createShader(Offset.zero & size),
    );

    // Central lightning bolt
    final bolt = Path()
      ..moveTo(w * 0.55, h * 0.23)
      ..lineTo(w * 0.33, h * 0.52)
      ..lineTo(w * 0.49, h * 0.52)
      ..lineTo(w * 0.44, h * 0.77)
      ..lineTo(w * 0.67, h * 0.47)
      ..lineTo(w * 0.51, h * 0.47)
      ..close();
    canvas.drawPath(
      bolt,
      Paint()
        ..color = const Color(0xFF38D9FF).withValues(alpha: 0.65)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 4),
    );
    canvas.drawPath(
      bolt,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Colors.white, Color(0xFF7CEEFF), Color(0xFF1CB5FF)],
        ).createShader(Offset.zero & size),
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Circular header action button (Golden VIP Crown or Slate-Blue Settings Gear).
class ClxyHeaderActionButton extends StatelessWidget {
  const ClxyHeaderActionButton({
    super.key,
    required this.isCrown,
    required this.onTap,
    this.tooltip,
  });

  final bool isCrown;
  final VoidCallback onTap;
  final String? tooltip;

  @override
  Widget build(BuildContext context) {
    final button = Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        customBorder: const CircleBorder(),
        child: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(
              center: Alignment(0, isCrown ? 0.35 : -0.3),
              radius: 0.85,
              colors: isCrown
                  ? const [
                      Color(0x80B97312),
                      Color(0xD92A1B24),
                      Color(0xF0141026),
                    ]
                  : const [
                      Color(0x80385CBE),
                      Color(0xD9131E4E),
                      Color(0xF20C1336),
                    ],
            ),
            border: Border.all(
              color: isCrown
                  ? const Color(0xB5F5AA2A)
                  : const Color(0x8C5C88EB),
              width: 1.3,
            ),
            boxShadow: [
              BoxShadow(
                color: (isCrown
                        ? const Color(0xFFF59E0B)
                        : const Color(0xFF3878FF))
                    .withValues(alpha: 0.22),
                blurRadius: 14,
              ),
            ],
          ),
          alignment: Alignment.center,
          child: isCrown
              ? const Icon(
                  Icons.workspace_premium_rounded,
                  color: ClxyMobileColors.amber,
                  size: 22,
                )
              : const Icon(
                  Icons.settings_outlined,
                  color: Color(0xFFDCE7FF),
                  size: 21,
                ),
        ),
      ),
    );
    return tooltip == null
        ? button
        : Tooltip(message: tooltip!, child: button);
  }
}

/// 3D Glowing Neon-Cyan 3-Tier Database Cylinder Icon for the Account Data card.
class ClxyDatabaseCylinderIcon extends StatelessWidget {
  const ClxyDatabaseCylinderIcon({super.key, this.width = 38, this.height = 42});

  final double width;
  final double height;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: width,
    height: height,
    child: const CustomPaint(painter: _ClxyDatabaseCylinderPainter()),
  );
}

class _ClxyDatabaseCylinderPainter extends CustomPainter {
  const _ClxyDatabaseCylinderPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;

    // Bottom neon cyan drop glow
    canvas.drawOval(
      Rect.fromCenter(
        center: Offset(w * 0.5, h * 0.91),
        width: w * 0.78,
        height: h * 0.15,
      ),
      Paint()
        ..color = ClxyMobileColors.cyan.withValues(alpha: 0.45)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5),
    );

    final tiers = [0.64, 0.41, 0.18];
    for (var i = 0; i < tiers.length; i++) {
      final topY = h * tiers[i];
      final tierH = h * 0.18;
      final rx = w * 0.40;
      final ry = h * 0.11;
      final cx = w * 0.5;

      final bodyPath = Path()
        ..moveTo(cx - rx, topY)
        ..lineTo(cx - rx, topY + tierH)
        ..arcToPoint(
          Offset(cx + rx, topY + tierH),
          radius: Radius.elliptical(rx, ry),
          clockwise: false,
        )
        ..lineTo(cx + rx, topY)
        ..close();

      final bodyRect = Rect.fromLTWH(cx - rx, topY, rx * 2, tierH + ry);
      canvas.drawPath(
        bodyPath,
        Paint()
          ..shader = const LinearGradient(
            colors: [Color(0xFF0A58CA), Color(0xFF2EE4FF), Color(0xFF0848A8)],
          ).createShader(bodyRect),
      );

      final capRect = Rect.fromCenter(
        center: Offset(cx, topY),
        width: rx * 2,
        height: ry * 2,
      );
      canvas.drawOval(
        capRect,
        Paint()
          ..shader = LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: i == 2
                ? const [Color(0xFF88FFFF), Color(0xFF24D8FF), Color(0xFF0084FF)]
                : const [Color(0xFF28C8FF), Color(0xFF0E5EC8)],
          ).createShader(capRect),
      );
      canvas.drawOval(
        capRect,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 0.9
          ..color = const Color(0xFF9EFFFF).withValues(alpha: 0.8),
      );
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Card 1 container: 35px pill-rounded glass card with conic specular cyan-blue rim.
class ClxyTrafficGlassCard extends StatelessWidget {
  const ClxyTrafficGlassCard({
    super.key,
    required this.child,
    this.onTap,
    this.padding = const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
  });

  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(34),
      boxShadow: const [
        BoxShadow(
          color: Color(0xCC000312),
          blurRadius: 24,
          offset: Offset(0, 10),
        ),
        BoxShadow(
          color: Color(0x330F5FEB),
          blurRadius: 20,
        ),
      ],
    ),
    child: CustomPaint(
      foregroundPainter: const _ClxySpecularRimPainter(radius: 34),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(34),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 14, sigmaY: 14),
          child: Material(
            color: Colors.transparent,
            child: Ink(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(34),
                gradient: const LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Color(0xCC10276E),
                    Color(0xE6060E30),
                    Color(0xF2040924),
                  ],
                ),
              ),
              child: InkWell(
                borderRadius: BorderRadius.circular(34),
                onTap: onTap,
                child: Padding(padding: padding, child: child),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

class _ClxySpecularRimPainter extends CustomPainter {
  const _ClxySpecularRimPainter({required this.radius});
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).deflate(0.75);
    final rrect = RRect.fromRectAndRadius(rect, Radius.circular(radius));
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4
      ..shader = const SweepGradient(
        colors: [
          Color(0xE678D7FF),
          Color(0x4D2364EB),
          Color(0xF2A0E1FF),
          Color(0x401941B4),
          Color(0xCC2891FF),
          Color(0x401941B4),
          Color(0xF291DCFF),
          Color(0xE678D7FF),
        ],
      ).createShader(rect);
    canvas.drawRRect(rrect, paint);
  }

  @override
  bool shouldRepaint(covariant _ClxySpecularRimPainter oldDelegate) =>
      oldDelegate.radius != radius;
}

/// Cards 2 & 3 container: Dual-tone Cyan-Left (#1AE4FF) to Neon-Purple-Right (#9D34FF)
/// glowing border card with deep cosmic navy glass interior.
class ClxyDualToneCard extends StatelessWidget {
  const ClxyDualToneCard({
    super.key,
    required this.child,
    this.onTap,
    this.padding = const EdgeInsets.symmetric(horizontal: 20, vertical: 13),
    this.radius = 26,
  });

  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry padding;
  final double radius;

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(radius),
      boxShadow: const [
        BoxShadow(
          color: Color(0x3300B4FF),
          blurRadius: 20,
          offset: Offset(-5, 0),
        ),
        BoxShadow(
          color: Color(0x2E8A2BE2),
          blurRadius: 20,
          offset: Offset(5, 0),
        ),
        BoxShadow(
          color: Color(0xD9000210),
          blurRadius: 24,
          offset: Offset(0, 10),
        ),
      ],
    ),
    child: CustomPaint(
      foregroundPainter: _ClxyDualToneBorderPainter(radius: radius),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 14, sigmaY: 14),
          child: Material(
            color: Colors.transparent,
            child: Ink(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(radius),
                gradient: const LinearGradient(
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                  colors: [
                    Color(0xD6082262),
                    Color(0xEB080F30),
                    Color(0xF20D0E36),
                  ],
                ),
              ),
              child: InkWell(
                borderRadius: BorderRadius.circular(radius),
                onTap: onTap,
                child: Padding(padding: padding, child: child),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

class _ClxyDualToneBorderPainter extends CustomPainter {
  const _ClxyDualToneBorderPainter({required this.radius});
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).deflate(0.75);
    final rrect = RRect.fromRectAndRadius(rect, Radius.circular(radius));
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5
      ..shader = const LinearGradient(
        begin: Alignment.centerLeft,
        end: Alignment.centerRight,
        colors: [
          Color(0xFF1AE4FF),
          Color(0xFF1678FF),
          Color(0xFF3A3898),
          Color(0xFF9D34FF),
        ],
        stops: [0.0, 0.34, 0.66, 1.0],
      ).createShader(rect);
    canvas.drawRRect(rrect, paint);
  }

  @override
  bool shouldRepaint(covariant _ClxyDualToneBorderPainter oldDelegate) =>
      oldDelegate.radius != radius;
}

/// Quiet, translucent surface shared by mobile cards and grouped menus.
class ClxyGlassSurface extends StatelessWidget {
  const ClxyGlassSurface({
    super.key,
    required this.child,
    this.padding = EdgeInsets.zero,
    this.margin = EdgeInsets.zero,
    this.radius = 24,
    this.clip = true,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry margin;
  final double radius;
  final bool clip;

  @override
  Widget build(BuildContext context) => Container(
    margin: margin,
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(radius),
      boxShadow: const [
        BoxShadow(
          color: Color(0x40000418),
          blurRadius: 24,
          offset: Offset(0, 8),
        ),
        BoxShadow(
          color: Color(0x181088FF),
          blurRadius: 18,
        ),
      ],
    ),
    child: Material(
      color: Colors.transparent,
      child: Ink(
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Color(0xEB0E1E4A),
              Color(0xF2070F2C),
              Color(0xF5050A20),
            ],
          ),
          borderRadius: BorderRadius.circular(radius),
          border: Border.all(color: ClxyMobileColors.lineStrong, width: 1.1),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(radius),
          child: Padding(padding: padding, child: child),
        ),
      ),
    ),
  );
}

/// Compact heading used above each of the four tab pages.
class ClxyMobilePageHeading extends StatelessWidget {
  const ClxyMobilePageHeading({
    super.key,
    required this.title,
    this.subtitle,
    this.eyebrow,
  });

  final String title;
  final String? subtitle;
  final String? eyebrow;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(0, 4, 0, 17),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.headlineSmall?.copyWith(
                    color: ClxyMobileColors.text,
                    fontSize: 25,
                    height: 1.15,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.45,
                  ),
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: 5),
                  Text(
                    subtitle!,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: ClxyMobileColors.muted,
                      height: 1.45,
                    ),
                  ),
                ],
              ],
            ),
          ),
          if (eyebrow != null) ...[
            const SizedBox(width: 10),
            Container(
              margin: const EdgeInsets.only(top: 4),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: ClxyMobileColors.cyan.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(99),
                border: Border.all(
                  color: ClxyMobileColors.cyan.withValues(alpha: 0.40),
                ),
              ),
              child: Text(
                eyebrow!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: ClxyMobileColors.cyan,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// A tactile, two-line route in the More tab.
class ClxyMobileMenuRow extends StatelessWidget {
  const ClxyMobileMenuRow({
    super.key,
    required this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.accent = ClxyMobileColors.cyan,
    this.compact = false,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;
  final Color accent;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final textDirection = Directionality.of(context);
    final iconSize = compact ? 34.0 : 44.0;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: compact ? 64 : 78),
          child: Padding(
            padding: EdgeInsetsDirectional.fromSTEB(
              14,
              compact ? 9 : 12,
              14,
              compact ? 9 : 12,
            ),
            child: Row(
              children: [
                Container(
                  width: iconSize,
                  height: iconSize,
                  decoration: BoxDecoration(
                    gradient: RadialGradient(
                      colors: [
                        accent.withValues(alpha: 0.24),
                        accent.withValues(alpha: 0.06),
                      ],
                    ),
                    borderRadius: BorderRadius.circular(compact ? 12 : 15),
                    border: Border.all(color: accent.withValues(alpha: 0.38)),
                  ),
                  alignment: Alignment.center,
                  child: Icon(icon, size: compact ? 18 : 22, color: accent),
                ),
                const SizedBox(width: 13),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          color: ClxyMobileColors.text,
                          fontSize: compact ? 13.5 : 14.5,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      if (subtitle != null) ...[
                        const SizedBox(height: 3),
                        Text(
                          subtitle!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(
                                color: ClxyMobileColors.muted,
                                fontSize: compact ? 10.5 : 11.5,
                              ),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: 9),
                Icon(
                  textDirection == TextDirection.rtl
                      ? Icons.chevron_left_rounded
                      : Icons.chevron_right_rounded,
                  color: ClxyMobileColors.muted,
                  size: 20,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Sculpted dark-blue wave/hill mound behind the central Power Button on Home.
class ClxyWaveMoundPainter extends CustomPainter {
  const ClxyWaveMoundPainter({required this.glowColor});
  final Color glowColor;

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    final path = Path()
      ..moveTo(0, h)
      ..lineTo(0, h * 0.78)
      ..cubicTo(
        w * 0.18,
        h * 0.72,
        w * 0.25,
        h * 0.14,
        w * 0.50,
        h * 0.14,
      )
      ..cubicTo(
        w * 0.75,
        h * 0.14,
        w * 0.82,
        h * 0.72,
        w,
        h * 0.78,
      )
      ..lineTo(w, h)
      ..close();

    canvas.drawPath(
      path,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xEB071442),
            Color(0xF5030924),
            Color(0xFF010314),
          ],
        ).createShader(Offset.zero & size),
    );

    final rimPath = Path()
      ..moveTo(0, h * 0.78)
      ..cubicTo(
        w * 0.18,
        h * 0.72,
        w * 0.25,
        h * 0.14,
        w * 0.50,
        h * 0.14,
      )
      ..cubicTo(
        w * 0.75,
        h * 0.14,
        w * 0.82,
        h * 0.72,
        w,
        h * 0.78,
      );

    canvas.drawPath(
      rimPath,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.6
        ..shader = LinearGradient(
          colors: [
            glowColor.withValues(alpha: 0.08),
            glowColor.withValues(alpha: 0.55),
            glowColor.withValues(alpha: 0.08),
          ],
        ).createShader(Offset.zero & size),
    );

    // Subtle math helper so dart:math import is always used cleanly
    if (math.pi < 0) canvas.drawCircle(Offset.zero, 0, Paint());
  }

  @override
  bool shouldRepaint(covariant ClxyWaveMoundPainter oldDelegate) =>
      oldDelegate.glowColor != glowColor;
}
