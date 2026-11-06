import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/ui/connection_globe.dart';

void main() {
  const us = GlobeLocation(
    code: 'US',
    name: 'United States',
    latitude: 37.09,
    longitude: -95.71,
    ip: '198.51.100.1',
  );
  const nl = GlobeLocation(
    code: 'NL',
    name: 'Netherlands',
    latitude: 52.13,
    longitude: 5.29,
    ip: '203.0.113.4',
  );

  test('orthographic projection stays finite at antimeridian and poles', () {
    for (final point in [
      projectGlobePoint(
        90,
        180,
        centerLatitude: 0,
        centerLongitude: 180,
        radius: 100,
      ),
      projectGlobePoint(
        -90,
        -180,
        centerLatitude: 89,
        centerLongitude: -179,
        radius: 100,
      ),
      projectGlobePoint(
        0,
        0,
        centerLatitude: 0,
        centerLongitude: 180,
        radius: 100,
      ),
    ]) {
      expect(point.x.isFinite, isTrue);
      expect(point.y.isFinite, isTrue);
    }
  });

  test(
    'great-circle route is finite for same, antipodal and wrapped points',
    () {
      for (final route in [
        greatCircleRoute(us, us),
        greatCircleRoute(us, nl),
        greatCircleRoute(
          const GlobeLocation(
            code: 'A',
            name: 'A',
            latitude: 0,
            longitude: 179,
          ),
          const GlobeLocation(
            code: 'B',
            name: 'B',
            latitude: 0,
            longitude: -179,
          ),
        ),
        greatCircleRoute(
          const GlobeLocation(code: 'A', name: 'A', latitude: 0, longitude: 0),
          const GlobeLocation(
            code: 'B',
            name: 'B',
            latitude: 0,
            longitude: 180,
          ),
        ),
      ]) {
        expect(route, isNotEmpty);
        expect(route.every((p) => p.dx.isFinite && p.dy.isFinite), isTrue);
      }
    },
  );

  test('invalid locations produce no route', () {
    const invalid = GlobeLocation(
      code: '',
      name: '',
      latitude: double.nan,
      longitude: 0,
    );
    expect(greatCircleRoute(invalid, nl), isEmpty);
  });
}
