import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/core/models.dart';
import 'package:clxy/ui/connection_globe.dart';
import 'package:clxy/ui/network_location.dart';

const _globeKey = ValueKey<String>('clxy-geographic-globe');

final _exit = ExitInfo.fromJson({
  'ipv4': '203.0.113.19',
  'ipv6': '',
  'country_code': 'NL',
  'country_name': 'Netherlands',
  'city': '',
  'network': '',
});

class _Fixture {
  _Fixture({required this.loader});

  final Future<List<int>> Function(Uri) loader;
  late final NetworkLocationTracker tracker = NetworkLocationTracker(
    service: NetworkLocationService(
      providerUrls: const ['https://fixture.invalid'],
      responseLoader: loader,
    ),
  )..requestOriginLocation();

  void dispose() => tracker.dispose();
}

_Fixture _fixture(Future<List<int>> Function(Uri) loader) =>
    _Fixture(loader: loader);

Future<void> _settle(WidgetTester tester) async {
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 1)),
  );
  await tester.pumpAndSettle(const Duration(milliseconds: 50));
  await tester.pump(const Duration(milliseconds: 50));
}

Widget _harness({
  required NetworkLocationTracker tracker,
  required bool connected,
  ExitInfo? exit,
}) => MaterialApp(
  home: Scaffold(
    body: SizedBox(
      width: 230,
      height: 273,
      child: ConnectionGlobe(
        tracker: tracker,
        exit: exit ?? _exit,
        connected: connected,
      ),
    ),
  ),
);

String _semanticsLabel(WidgetTester tester) {
  return tester.widget<Semantics>(find.byKey(_globeKey)).properties.label ?? '';
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    await rootBundle.loadString('assets/globe-countries.json');
    await rootBundle.loadString('assets/globe-land.json');
  });

  testWidgets('loads the origin and exposes the connected route semantics', (
    tester,
  ) async {
    final pending = Completer<List<int>>();
    final fixture = _fixture((_) => pending.future);
    addTearDown(fixture.dispose);

    await tester.binding.setSurfaceSize(const Size(230, 273));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      _harness(tracker: fixture.tracker, connected: true),
    );
    await tester.pump(const Duration(milliseconds: 50));
    expect(_semanticsLabel(tester), 'Location globe');

    pending.complete(
      utf8.encode(
        jsonEncode({
          'ip': '198.51.100.10',
          'country_code': 'US',
          'country_name': 'United States',
        }),
      ),
    );
    await _settle(tester);

    expect(
      _semanticsLabel(tester),
      'Approximate IP route: United States to Netherlands',
    );
    expect(find.text('Origin · United States'), findsOneWidget);
    expect(find.text('Exit · Netherlands'), findsOneWidget);
  });

  testWidgets('compact globe fills the circular button and keeps real IPs', (
    tester,
  ) async {
    final fixture = _fixture(
      (_) async => utf8.encode(
        jsonEncode({
          'ip': '198.51.100.10',
          'country_code': 'US',
          'country_name': 'United States',
        }),
      ),
    );
    addTearDown(fixture.dispose);
    await tester.binding.setSurfaceSize(const Size(250, 250));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox.square(
              dimension: 218,
              child: ConnectionGlobe(
                tracker: fixture.tracker,
                exit: _exit,
                connected: true,
                compact: true,
                showIp: true,
              ),
            ),
          ),
        ),
      ),
    );
    await _settle(tester);

    expect(tester.getSize(find.byKey(_globeKey)), const Size(218, 218));
    final label = _semanticsLabel(tester);
    expect(
      label,
      contains('Approximate IP route: United States to Netherlands'),
    );
    expect(label, contains('origin IP 198.51.100.10'));
    expect(label, contains('exit IP 203.0.113.19'));
    expect(find.text('Origin · United States'), findsNothing);
  });

  testWidgets(
    'removing connected state hides the exit route but retains origin',
    (tester) async {
      final fixture = _fixture(
        (_) async => utf8.encode(
          jsonEncode({
            'ip': '198.51.100.10',
            'country_code': 'US',
            'country_name': 'United States',
          }),
        ),
      );
      addTearDown(fixture.dispose);

      await tester.binding.setSurfaceSize(const Size(230, 273));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        _harness(tracker: fixture.tracker, connected: true),
      );
      await _settle(tester);
      expect(
        _semanticsLabel(tester),
        'Approximate IP route: United States to Netherlands',
      );

      await tester.pumpWidget(
        _harness(tracker: fixture.tracker, connected: false),
      );
      await _settle(tester);

      expect(
        _semanticsLabel(tester),
        'Approximate starting location: United States',
      );
      expect(find.text('Origin · United States'), findsOneWidget);
      expect(find.text('Exit · Not connected'), findsOneWidget);
    },
  );

  testWidgets(
    'uses country centroid fallback and preserves a long IPv6 tooltip',
    (tester) async {
      final longIpv6 = '2001:0db8:0000:0000:0000:ff00:0042:8329';
      final fixture = _fixture(
        (_) async => utf8.encode(
          jsonEncode({
            'ip': longIpv6,
            'country_code': 'US',
            'country_name': 'United States',
          }),
        ),
      );
      addTearDown(fixture.dispose);

      await tester.binding.setSurfaceSize(const Size(230, 273));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        _harness(tracker: fixture.tracker, connected: true),
      );
      await _settle(tester);

      expect(
        _semanticsLabel(tester),
        'Approximate IP route: United States to Netherlands',
      );
      expect(find.byType(OverflowBar), findsNothing);
      final tooltips = tester.widgetList<Tooltip>(find.byType(Tooltip));
      expect(tooltips.any((tooltip) => tooltip.message == longIpv6), isTrue);
    },
  );

  testWidgets('reports lookup failure and retries successfully', (
    tester,
  ) async {
    var attempts = 0;
    final fixture = _fixture((_) async {
      attempts++;
      if (attempts == 1) throw StateError('fixture failure');
      return utf8.encode(
        jsonEncode({
          'ip': '198.51.100.10',
          'country_code': 'US',
          'country_name': 'United States',
        }),
      );
    });
    addTearDown(fixture.dispose);

    await tester.binding.setSurfaceSize(const Size(230, 273));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      _harness(tracker: fixture.tracker, connected: true),
    );
    await _settle(tester);

    expect(find.text('Origin location unavailable'), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
    expect(
      _semanticsLabel(tester),
      'Destination: Netherlands; origin location unavailable',
    );

    await tester.tap(find.text('Retry'));
    await _settle(tester);

    expect(attempts, 2);
    expect(find.text('Origin · United States'), findsOneWidget);
    expect(
      _semanticsLabel(tester),
      'Approximate IP route: United States to Netherlands',
    );
  });

  testWidgets('disposing while the origin request is pending is clean', (
    tester,
  ) async {
    final pending = Completer<List<int>>();
    final fixture = _fixture((_) => pending.future);
    await tester.binding.setSurfaceSize(const Size(230, 273));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      _harness(tracker: fixture.tracker, connected: true),
    );
    await tester.pump(const Duration(milliseconds: 50));

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 50));
    pending.complete(
      utf8.encode(
        jsonEncode({
          'ip': '198.51.100.10',
          'country_code': 'US',
          'country_name': 'United States',
        }),
      ),
    );
    await tester.pump(const Duration(milliseconds: 100));
    fixture.dispose();
  });
}
