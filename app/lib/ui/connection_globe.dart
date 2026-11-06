import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;

import '../core/models.dart';
import 'network_location.dart';

/// A country-level geographic point used by the connection globe.
class GlobeLocation {
  const GlobeLocation({
    required this.code,
    required this.name,
    required this.latitude,
    required this.longitude,
    this.ip = '',
  });

  final String code;
  final String name;
  final double latitude;
  final double longitude;
  final String ip;
  bool get valid => latitude.isFinite && longitude.isFinite;
}

class GlobePoint {
  const GlobePoint(this.x, this.y, this.visible);
  final double x;
  final double y;
  final bool visible;
}

GlobePoint projectGlobePoint(
  double latitude,
  double longitude, {
  required double centerLatitude,
  required double centerLongitude,
  required double radius,
}) {
  if (![
        latitude,
        longitude,
        centerLatitude,
        centerLongitude,
        radius,
      ].every((v) => v.isFinite) ||
      radius <= 0 ||
      latitude < -90 ||
      latitude > 90) {
    return const GlobePoint(0, 0, false);
  }
  final lat = latitude * math.pi / 180;
  final lon = _wrapLongitude(longitude) * math.pi / 180;
  final lat0 = centerLatitude * math.pi / 180;
  final lon0 = _wrapLongitude(centerLongitude) * math.pi / 180;
  final dLon = _wrapRadians(lon - lon0);
  final cosLat = math.cos(lat);
  final x = radius * cosLat * math.sin(dLon);
  final y =
      radius *
      (math.cos(lat0) * math.sin(lat) -
          math.sin(lat0) * cosLat * math.cos(dLon));
  final visible =
      math.sin(lat0) * math.sin(lat) +
          math.cos(lat0) * cosLat * math.cos(dLon) >=
      -0.01;
  return GlobePoint(x, -y, visible);
}

List<Offset> greatCircleRoute(
  GlobeLocation from,
  GlobeLocation to, {
  int steps = 32,
}) {
  if (!from.valid || !to.valid) return const [];
  final a = _unit(from.latitude, from.longitude);
  final b = _unit(to.latitude, to.longitude);
  final dot = (a.x * b.x + a.y * b.y + a.z * b.z).clamp(-1.0, 1.0);
  final angle = math.acos(dot);
  final count = math.max(2, steps);
  final points = <Offset>[];
  final sinAngle = math.sin(angle);
  // Antipodal points have no unique great circle. Pick a stable perpendicular
  // plane instead of dividing by sin(pi), which creates infinities.
  final antipodal = sinAngle.abs() < 0.000001 && dot < 0;
  final perpendicular = _Vec(-a.y, a.x, 0);
  final perpendicularLength = math.sqrt(
    perpendicular.x * perpendicular.x + perpendicular.y * perpendicular.y,
  );
  for (var i = 0; i <= count; i++) {
    final t = i / count;
    late final double x, y, z;
    if (angle.abs() < 0.000001) {
      x = a.x;
      y = a.y;
      z = a.z;
    } else if (antipodal && perpendicularLength > 0.000001) {
      final u = perpendicularLength == 0
          ? const _Vec(0, 0, 1)
          : _Vec(
              perpendicular.x / perpendicularLength,
              perpendicular.y / perpendicularLength,
              0,
            );
      final theta = math.pi * t;
      x = a.x * math.cos(theta) + u.x * math.sin(theta);
      y = a.y * math.cos(theta) + u.y * math.sin(theta);
      z = a.z * math.cos(theta) + u.z * math.sin(theta);
    } else {
      final factorA = math.sin((1 - t) * angle) / sinAngle;
      final factorB = math.sin(t * angle) / sinAngle;
      x = a.x * factorA + b.x * factorB;
      y = a.y * factorA + b.y * factorB;
      z = a.z * factorA + b.z * factorB;
    }
    final length = math.sqrt(x * x + y * y + z * z);
    if (length.isFinite && length > 0) {
      points.add(
        Offset(
          math.atan2(y / length, x / length) * 180 / math.pi,
          math.asin((z / length).clamp(-1.0, 1.0)) * 180 / math.pi,
        ),
      );
    }
  }
  return points;
}

class NetworkLocationScope extends InheritedNotifier<NetworkLocationTracker> {
  const NetworkLocationScope({
    super.key,
    required NetworkLocationTracker tracker,
    required super.child,
  }) : tracker = tracker,
       super(notifier: tracker);
  final NetworkLocationTracker tracker;
  static NetworkLocationTracker of(BuildContext context) => context
      .dependOnInheritedWidgetOfExactType<NetworkLocationScope>()!
      .tracker;
}

class ConnectionGlobe extends StatefulWidget {
  const ConnectionGlobe({
    super.key,
    required this.tracker,
    required this.exit,
    required this.connected,
    this.height = 225,
    this.compact = false,
    this.showIp = false,
  });

  final NetworkLocationTracker tracker;
  final ExitInfo? exit;
  final bool connected;
  final double height;
  final bool compact;
  final bool showIp;

  @override
  State<ConnectionGlobe> createState() => _ConnectionGlobeState();
}

class _ConnectionGlobeState extends State<ConnectionGlobe>
    with SingleTickerProviderStateMixin {
  Map<String, GlobeLocation> _countries = const {};
  List<List<GlobeLocation>> _land = const [];

  /// Controls both the 3D globe rotation from the real origin to the real
  /// connected exit, and the connection flash and laser arc:
  /// - 0.0: Disconnected -> Globe rotated squarely onto the real origin.
  /// - 0.0 -> 1.0: Connecting/Connected -> Flashes, shoots arc from Origin,
  ///   and rotates the 3D globe onto the Destination Server!
  late final AnimationController _animation = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1450),
    value: 0.0,
  );
  late final Animation<double> _curvedProgress = CurvedAnimation(
    parent: _animation,
    curve: Curves.easeInOutCubic,
  );

  String _routeKey = '';

  List<String> get _exitIps => [
    widget.exit?.ipv4 ?? '',
    widget.exit?.ipv6 ?? '',
  ].where((ip) => ip.trim().isNotEmpty).toList();

  bool get _originMatchesExit {
    final originIp = widget.tracker.originLocation?.ip.trim().toLowerCase();
    if (!widget.connected || originIp == null || originIp.isEmpty) return false;
    return _exitIps.any((ip) => ip.trim().toLowerCase() == originIp);
  }

  void _retryOrigin() =>
      widget.tracker.requestOriginLocation(retry: true, excludedIps: _exitIps);

  @override
  void initState() {
    super.initState();
    widget.tracker.addListener(_originChanged);
    _loadGeography();
  }

  @override
  void didUpdateWidget(covariant ConnectionGlobe oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.tracker != widget.tracker) {
      oldWidget.tracker.removeListener(_originChanged);
      widget.tracker.addListener(_originChanged);
      _routeKey = '';
    }
    _syncRotationAndRoute(wasConnected: oldWidget.connected);
  }

  void _originChanged() {
    if (!mounted) return;
    _syncRotationAndRoute(wasConnected: widget.connected);
    setState(() {});
  }

  GlobeLocation? get _origin {
    final value = widget.tracker.originLocation;
    if (value == null ||
        value.countryCode.isEmpty ||
        value.ip.isEmpty ||
        _originMatchesExit) {
      return null;
    }
    final code = value.countryCode.toUpperCase();
    final known = _countries[code];
    final lat = value.latitude;
    final lon = value.longitude;
    final latitude = lat != null && lat.isFinite && lat >= -90 && lat <= 90
        ? lat
        : known?.latitude;
    final longitude = lon != null && lon.isFinite && lon >= -180 && lon <= 180
        ? lon
        : known?.longitude;
    if (latitude == null || longitude == null) return null;
    return GlobeLocation(
      code: code,
      name: value.countryName.isEmpty
          ? (known?.name ?? code)
          : value.countryName,
      latitude: latitude,
      longitude: longitude,
      ip: value.ip,
    );
  }

  GlobeLocation? get _exit {
    final e = widget.exit;
    if (!widget.connected || e == null) return null;
    final code = e.countryCode.toUpperCase();
    final ip = e.ipv4.isNotEmpty ? e.ipv4 : e.ipv6;
    final known = _countries[code];
    if (code.isEmpty || ip.isEmpty || known == null) return null;
    return GlobeLocation(
      code: code,
      name: e.countryName.isEmpty ? known.name : e.countryName,
      latitude: known.latitude,
      longitude: known.longitude,
      ip: ip,
    );
  }

  String get _routeIdentity {
    final a = _origin, b = _exit;
    if (!widget.connected || a == null || b == null) return '';
    return '${a.ip}|${a.code}|${b.ip}|${b.code}';
  }

  void _syncRotationAndRoute({required bool wasConnected}) {
    final reduced =
        mounted && (MediaQuery.maybeOf(context)?.disableAnimations ?? false);
    if (!widget.connected) {
      // Disconnected: return to the real origin without an exit route.
      _routeKey = '';
      if (reduced) {
        _animation.value = 0.0;
      } else if (_animation.value > 0.0) {
        _animation.reverse();
      }
      return;
    }

    final key = _routeIdentity;
    if (key.isEmpty) {
      _routeKey = '';
      _animation.stop();
      _animation.value = 0.0;
      return;
    }
    if (key != _routeKey || !wasConnected) {
      _routeKey = key;
      if (reduced) {
        _animation.value = 1.0;
      } else {
        _animation.forward(from: 0.0);
      }
    }
  }

  Future<void> _loadGeography() async {
    try {
      final raw = await rootBundle.loadString('assets/globe-countries.json');
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final countries = <String, GlobeLocation>{};
      for (final entry in map.entries) {
        final value = entry.value as Map<String, dynamic>;
        final lat = (value['latitude'] as num?)?.toDouble();
        final lon = (value['longitude'] as num?)?.toDouble();
        if (lat != null &&
            lon != null &&
            lat.isFinite &&
            lon.isFinite &&
            lat >= -90 &&
            lat <= 90 &&
            lon >= -180 &&
            lon <= 180) {
          final code = entry.key.toUpperCase();
          countries[code] = GlobeLocation(
            code: code,
            name: '${value['name'] ?? code}',
            latitude: lat,
            longitude: lon,
          );
        }
      }
      final landRaw = await rootBundle.loadString('assets/globe-land.json');
      final geo = jsonDecode(landRaw) as Map<String, dynamic>;
      final land = <List<GlobeLocation>>[];
      for (final feature in (geo['features'] as List? ?? const [])) {
        final geometry = (feature as Map)['geometry'] as Map?;
        final coordinates = geometry?['coordinates'];
        if (geometry?['type'] == 'Polygon') {
          for (final ring in coordinates as List) {
            land.add(_ring(ring));
          }
        } else if (geometry?['type'] == 'MultiPolygon') {
          for (final polygon in coordinates as List) {
            for (final ring in polygon as List) {
              land.add(_ring(ring));
            }
          }
        }
      }
      if (mounted) {
        setState(() {
          _countries = countries;
          _land = land;
        });
      }
      if (mounted) _syncRotationAndRoute(wasConnected: widget.connected);
    } catch (_) {
      if (mounted) {
        setState(() {
          _countries = const {};
          _land = const [];
        });
      }
    }
  }

  List<GlobeLocation> _ring(dynamic ring) => [
    for (final pair in ring as List)
      if ((pair as List).length >= 2)
        GlobeLocation(
          code: '',
          name: '',
          latitude: (pair[1] as num).toDouble(),
          longitude: (pair[0] as num).toDouble(),
        ),
  ];

  @override
  void dispose() {
    widget.tracker.removeListener(_originChanged);
    _animation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final origin = _origin;
    final exit = _exit;
    final tracker = widget.tracker;
    final status = origin != null
        ? null
        : tracker.originLocationLoading
        ? 'Loading origin location…'
        : 'Origin location unavailable';
    final ipDetails = widget.showIp
        ? ' (origin IP ${origin?.ip ?? 'unavailable'}'
              '${exit == null ? '' : ', exit IP ${exit.ip}'})'
        : '';
    final description = origin != null && exit != null
        ? 'Approximate IP route: ${origin.name} to ${exit.name}$ipDetails'
        : origin != null
        ? 'Approximate starting location: ${origin.name}$ipDetails'
        : exit != null
        ? 'Destination: ${exit.name}'
              '${widget.showIp ? ', exit IP ${exit.ip}' : ''}; origin location unavailable'
        : 'Location globe';
    return AnimatedBuilder(
      animation: _curvedProgress,
      builder: (context, _) => LayoutBuilder(
        builder: (context, constraints) {
          final painter = _GlobePainter(
            countries: _land,
            origin: origin,
            exit: exit,
            connected: widget.connected,
            progress: _curvedProgress.value,
            rawProgress: _animation.value,
            compact: widget.compact,
          );
          final globe = Semantics(
            key: const ValueKey('clxy-geographic-globe'),
            image: true,
            label: description,
            child: SizedBox(
              width: double.infinity,
              child: CustomPaint(painter: painter),
            ),
          );
          if (widget.compact) {
            return ClipOval(child: SizedBox.expand(child: globe));
          }
          return Column(
            mainAxisSize: constraints.hasBoundedHeight
                ? MainAxisSize.max
                : MainAxisSize.min,
            children: [
              if (constraints.hasBoundedHeight)
                Expanded(child: globe)
              else
                SizedBox(height: widget.height, child: globe),
              if (status != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Flexible(
                        child: Text(
                          status,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Color(0xFFA2B1D0),
                            fontSize: 11,
                          ),
                        ),
                      ),
                      if (tracker.originLocationError != null ||
                          _originMatchesExit) ...[
                        const SizedBox(width: 6),
                        TextButton(
                          onPressed: _retryOrigin,
                          style: TextButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 6),
                            minimumSize: Size.zero,
                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          ),
                          child: const Text(
                            'Retry',
                            style: TextStyle(fontSize: 11),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              _LocationRows(
                origin: origin,
                exit: exit,
                loading: tracker.originLocationLoading,
                connected: widget.connected,
              ),
              const SizedBox(height: 4),
              const Text(
                'Approximate IP locations',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: Color(0xFF7E92B4), fontSize: 10),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _LocationRows extends StatelessWidget {
  const _LocationRows({
    this.origin,
    this.exit,
    required this.loading,
    required this.connected,
  });
  final GlobeLocation? origin;
  final GlobeLocation? exit;
  final bool loading;
  final bool connected;
  @override
  Widget build(BuildContext context) => Column(
    children: [
      _row('Origin', origin, Icons.home_work_outlined, loading),
      const SizedBox(height: 3),
      _row('Exit', exit, Icons.exit_to_app_rounded, false),
    ],
  );
  Widget _row(
    String label,
    GlobeLocation? location,
    IconData icon,
    bool isLoading,
  ) {
    final missing = label == 'Exit'
        ? connected
              ? 'Location unavailable'
              : 'Not connected'
        : isLoading
        ? 'Loading location'
        : 'Unavailable';
    final text = location == null
        ? '$label · $missing'
        : '$label · ${location.name}';
    return Row(
      children: [
        Icon(icon, size: 15, color: const Color(0xFFA2B1D0)),
        const SizedBox(width: 6),
        Expanded(
          flex: 3,
          child: Tooltip(
            message: text,
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Color(0xFFA2B1D0), fontSize: 11.5),
            ),
          ),
        ),
        if (location?.ip.isNotEmpty == true)
          Flexible(
            flex: 2,
            child: Tooltip(
              message: location!.ip,
              child: Text(
                location.ip,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Color(0xFFF1F5FF), fontSize: 11),
              ),
            ),
          ),
      ],
    );
  }
}

class _GlobePainter extends CustomPainter {
  _GlobePainter({
    required this.countries,
    this.origin,
    this.exit,
    required this.connected,
    required this.progress,
    required this.rawProgress,
    required this.compact,
  });

  final List<List<GlobeLocation>> countries;
  final GlobeLocation? origin;
  final GlobeLocation? exit;
  final bool connected;
  final double progress;
  final double rawProgress;
  final bool compact;

  @override
  void paint(Canvas canvas, Size size) {
    final radius = compact
        ? math.min(size.width * .46, size.height * .46)
        : math.min(size.width * .36, size.height * .48);
    final center = Offset(size.width / 2, size.height / 2);

    final a = origin;
    final b = connected ? exit : null;

    // Camera Rotation Math (Strictly from real origin & exit data):
    // - Disconnected (progress == 0.0): Centers on real origin `a` when available.
    // - Connected (progress -> 1.0): Rotates from `a` toward real exit `b`.
    final double startLat = a?.latitude ?? b?.latitude ?? 20.0;
    final double startLon = a?.longitude ?? b?.longitude ?? 0.0;
    final double endLat = b?.latitude ?? startLat;
    final double endLon = b?.longitude ?? startLon;

    final double midLat = lerpDouble(startLat, endLat, progress)!;
    final double midLon = _lerpLongitude(startLon, endLon, progress);

    final sphereRect = Rect.fromCircle(center: center, radius: radius);

    // 1. Outer Atmospheric Neon Glow Halo
    canvas.drawCircle(
      center,
      radius * 1.15,
      Paint()
        ..shader = RadialGradient(
          colors: [
            const Color(0xFF1F9BFF).withValues(alpha: 0.32),
            const Color(0xFF0638A8).withValues(alpha: 0.14),
            Colors.transparent,
          ],
          stops: const [0.65, 0.85, 1.0],
        ).createShader(Rect.fromCircle(center: center, radius: radius * 1.15)),
    );

    // 2. Connection Flash Burst when connecting (rawProgress 0.05..0.85)
    if (connected &&
        a != null &&
        b != null &&
        rawProgress > 0.01 &&
        rawProgress < 0.98) {
      final flashWave = math.sin(rawProgress * math.pi);
      canvas.drawCircle(
        center,
        radius * (0.96 + rawProgress * 0.18),
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3.2 * flashWave
          ..color = const Color(0xFF52F7FF).withValues(alpha: 0.85 * flashWave)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 7),
      );
    }

    // 3. 3D Cosmic Ocean Sphere Body
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..shader = const RadialGradient(
          center: Alignment(-0.18, -0.58),
          radius: 1.08,
          colors: [
            Color(0xFF3CA8FF),
            Color(0xFF0A42B8),
            Color(0xFF041A62),
            Color(0xFF02092B),
          ],
          stops: [0.0, 0.28, 0.68, 1.0],
        ).createShader(sphereRect),
    );

    // 4. Graticule Grid (Parallels & Meridians)
    final grid = Paint()
      ..color = const Color(0xFF4AC7FF).withValues(alpha: 0.16)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 0.65;
    for (var lat = -60.0; lat <= 60; lat += 30) {
      _line(canvas, center, radius, midLat, midLon, lat, null, grid);
    }
    for (var lon = -150.0; lon <= 180; lon += 30) {
      _line(canvas, center, radius, midLat, midLon, null, lon, grid);
    }

    // 5. Continents & Land Polygons with Illuminated Coastlines
    final landFill = Paint()
      ..color = const Color(0xFF1A58B8).withValues(alpha: 0.68);
    final landStroke = Paint()
      ..color = const Color(0xFF4CE0FF).withValues(alpha: 0.38)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 0.75;

    for (final ring in countries) {
      final path = Path();
      var started = false;
      for (final p in ring) {
        final q = projectGlobePoint(
          p.latitude,
          p.longitude,
          centerLatitude: midLat,
          centerLongitude: midLon,
          radius: radius,
        );
        if (!q.visible) {
          started = false;
          continue;
        }
        final point = center + Offset(q.x, q.y);
        if (!started) {
          path.moveTo(point.dx, point.dy);
          started = true;
        } else {
          path.lineTo(point.dx, point.dy);
        }
      }
      canvas.drawPath(path, landFill);
      canvas.drawPath(path, landStroke);
    }

    // 6. Top Polar Horizon Specular Highlight (Matches screenshot's bright top horizon)
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            const Color(0xFFC8F4FF).withValues(alpha: 0.55),
            const Color(0xFF28A8FF).withValues(alpha: 0.14),
            Colors.transparent,
          ],
          stops: const [0.0, 0.22, 0.52],
        ).createShader(sphereRect),
    );

    // 7. Crisp Glowing Sphere Rim
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xFFD8F6FF),
            Color(0xFF28B8FF),
            Color(0xFF0A3898),
          ],
        ).createShader(sphereRect)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.6,
    );

    // 8. Pins and the route require both live endpoints and a connection.
    if (!connected || a == null || b == null || progress <= 0.01) {
      return;
    }

    // 9. Connected / Connecting: Shoot Glowing Neon Great-Circle Laser Arc
    final route = greatCircleRoute(a, b, steps: 44);
    final take = (route.length * progress.clamp(0.0, 1.0)).ceil();
    final path = Path();
    var started = false;
    Offset? tip;
    Offset? previous;
    for (final p in route.take(math.max(1, take))) {
      final q = projectGlobePoint(
        p.dy,
        p.dx,
        centerLatitude: midLat,
        centerLongitude: midLon,
        radius: radius,
      );
      if (!q.visible) {
        started = false;
        continue;
      }
      // Elevate mid-arc slightly for a 3D orbital trajectory feel
      final point = center + Offset(q.x, q.y);
      if (!started) {
        path.moveTo(point.dx, point.dy);
        started = true;
      } else {
        path.lineTo(point.dx, point.dy);
      }
      previous = tip;
      tip = point;
    }

    // Outer neon cyan bloom along route
    canvas.drawPath(
      path,
      Paint()
        ..color = const Color(0xFF00E5FF).withValues(alpha: 0.65)
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeWidth = 4.2
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5),
    );
    // Bright white-cyan laser core
    canvas.drawPath(
      path,
      Paint()
        ..color = const Color(0xFFE8FCFF)
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeWidth = 2.0,
    );

    // Draw the real origin and destination pins.
    _drawPinMarker(
      canvas,
      center,
      radius,
      midLat,
      midLon,
      a,
      isOrigin: true,
      highlightScale: 0.96,
    );
    _drawPinMarker(
      canvas,
      center,
      radius,
      midLat,
      midLon,
      b,
      isOrigin: false,
      highlightScale: (0.7 + 0.35 * progress).clamp(0.0, 1.05),
    );

    if (tip != null && progress > 0 && progress < 0.99) {
      // Glowing laser head while shooting across the globe
      canvas.drawCircle(
        tip,
        6.0,
        Paint()
          ..color = const Color(0xFF52F7FF)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6),
      );
      canvas.drawCircle(tip, 2.8, Paint()..color = Colors.white);
      final direction = previous == null ? Offset.zero : tip - previous;
      final angle = direction.distance > 0.001
          ? math.atan2(direction.dy, direction.dx)
          : 0.0;
      final p1 =
          tip - Offset(math.cos(angle - .55) * 8, math.sin(angle - .55) * 8);
      final p2 =
          tip - Offset(math.cos(angle + .55) * 8, math.sin(angle + .55) * 8);
      canvas.drawPath(
        Path()
          ..moveTo(tip.dx, tip.dy)
          ..lineTo(p1.dx, p1.dy)
          ..lineTo(p2.dx, p2.dy)
          ..close(),
        Paint()..color = const Color(0xFF52F7FF),
      );
    }
  }

  void _drawPinMarker(
    Canvas c,
    Offset center,
    double radius,
    double clat,
    double clon,
    GlobeLocation p, {
    required bool isOrigin,
    required double highlightScale,
  }) {
    final q = projectGlobePoint(
      p.latitude,
      p.longitude,
      centerLatitude: clat,
      centerLongitude: clon,
      radius: radius,
    );
    if (!q.visible) return;
    final tip = center + Offset(q.x, q.y);
    final glowColor = isOrigin
        ? const Color(0xFF00FFA3)
        : const Color(0xFF28D7FF);

    c.save();
    c.translate(tip.dx, tip.dy);
    c.scale(highlightScale);

    // Surface radar ring
    c.drawOval(
      Rect.fromCenter(center: Offset.zero, width: 16, height: 7),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.3
        ..color = glowColor.withValues(alpha: 0.8),
    );
    c.drawCircle(
      Offset.zero,
      3.2,
      Paint()
        ..color = glowColor
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 4),
    );

    // 3D Teardrop Pin Body
    final pinPath = Path()
      ..moveTo(0, 0)
      ..cubicTo(-4.5, -7, -11, -12, -11, -19.5)
      ..arcToPoint(
        const Offset(11, -19.5),
        radius: const Radius.circular(11),
        clockwise: true,
      )
      ..cubicTo(11, -12, 4.5, -7, 0, 0)
      ..close();

    c.drawPath(
      pinPath,
      Paint()
        ..color = glowColor.withValues(alpha: 0.55)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5),
    );
    c.drawPath(
      pinPath,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [const Color(0xFF4AE0FF), const Color(0xFF0055FF), glowColor],
        ).createShader(const Rect.fromLTWH(-11, -31, 22, 31)),
    );
    c.drawPath(
      pinPath,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.0
        ..color = Colors.white.withValues(alpha: 0.85),
    );

    // Flag disc inside pin head
    const headCenter = Offset(0, -19.5);
    final headRect = Rect.fromCircle(center: headCenter, radius: 7.8);
    c.save();
    c.clipPath(Path()..addOval(headRect));
    _paintMiniFlag(c, headRect, p.code.toUpperCase());
    c.restore();

    c.restore();
  }

  void _paintMiniFlag(Canvas c, Rect rect, String code) {
    if (code == 'IR') {
      final h = rect.height / 3;
      c.drawRect(
        Rect.fromLTWH(rect.left, rect.top, rect.width, h),
        Paint()..color = const Color(0xFF16A34A),
      );
      c.drawRect(
        Rect.fromLTWH(rect.left, rect.top + h, rect.width, h),
        Paint()..color = Colors.white,
      );
      c.drawRect(
        Rect.fromLTWH(rect.left, rect.top + 2 * h, rect.width, h),
        Paint()..color = const Color(0xFFDC2626),
      );
      c.drawCircle(rect.center, 1.5, Paint()..color = const Color(0xFFDC2626));
    } else if (code == 'DE') {
      final h = rect.height / 3;
      c.drawRect(
        Rect.fromLTWH(rect.left, rect.top, rect.width, h),
        Paint()..color = const Color(0xFF111827),
      );
      c.drawRect(
        Rect.fromLTWH(rect.left, rect.top + h, rect.width, h),
        Paint()..color = const Color(0xFFDC2626),
      );
      c.drawRect(
        Rect.fromLTWH(rect.left, rect.top + 2 * h, rect.width, h),
        Paint()..color = const Color(0xFFFACC15),
      );
    } else if (code == 'NL') {
      final h = rect.height / 3;
      c.drawRect(
        Rect.fromLTWH(rect.left, rect.top, rect.width, h),
        Paint()..color = const Color(0xFFBE123C),
      );
      c.drawRect(
        Rect.fromLTWH(rect.left, rect.top + h, rect.width, h),
        Paint()..color = Colors.white,
      );
      c.drawRect(
        Rect.fromLTWH(rect.left, rect.top + 2 * h, rect.width, h),
        Paint()..color = const Color(0xFF1D4ED8),
      );
    } else if (code == 'US') {
      // US stripes and canton only for a real US endpoint.
      c.drawRect(rect, Paint()..color = Colors.white);
      final sh = rect.height / 7;
      for (var i = 0; i < 7; i += 2) {
        c.drawRect(
          Rect.fromLTWH(rect.left, rect.top + i * sh, rect.width, sh),
          Paint()..color = const Color(0xFFE11D48),
        );
      }
      c.drawRect(
        Rect.fromLTWH(
          rect.left,
          rect.top,
          rect.width * 0.52,
          rect.height * 0.54,
        ),
        Paint()..color = const Color(0xFF1D4ED8),
      );
    }
  }

  void _line(
    Canvas canvas,
    Offset center,
    double radius,
    double clat,
    double clon,
    double? lat,
    double? lon,
    Paint paint,
  ) {
    final path = Path();
    var started = false;
    for (var i = 0; i <= 36; i++) {
      final value = -180 + i * 10.0;
      final q = projectGlobePoint(
        lat ?? value,
        lon ?? value,
        centerLatitude: clat,
        centerLongitude: clon,
        radius: radius,
      );
      if (!q.visible) {
        started = false;
        continue;
      }
      final point = center + Offset(q.x, q.y);
      if (!started) {
        path.moveTo(point.dx, point.dy);
        started = true;
      } else {
        path.lineTo(point.dx, point.dy);
      }
    }
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant _GlobePainter old) =>
      old.progress != progress ||
      old.rawProgress != rawProgress ||
      old.connected != connected ||
      old.origin != origin ||
      old.exit != exit ||
      old.compact != compact ||
      old.countries != countries;
}

class _Vec {
  const _Vec(this.x, this.y, this.z);
  final double x, y, z;
}

_Vec _unit(double lat, double lon) {
  final p = lat * math.pi / 180, l = lon * math.pi / 180;
  return _Vec(
    math.cos(p) * math.cos(l),
    math.cos(p) * math.sin(l),
    math.sin(p),
  );
}

double _wrapLongitude(double value) => ((value + 180) % 360 + 360) % 360 - 180;
double _wrapRadians(double value) =>
    ((value + math.pi) % (2 * math.pi) + 2 * math.pi) % (2 * math.pi) - math.pi;
double _lerpLongitude(double a, double b, double t) =>
    _wrapLongitude(a + _wrapLongitude(b - a) * t);
