import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/ui/network_location.dart';

const _fixture = '{"ip":"198.51.100.10","country_code":"US"}';
NetworkLocationService _service(String body, {int maxBytes = 65536}) =>
    NetworkLocationService(
      providerUrls: const ['https://fixture.invalid/geoip'],
      maxResponseBytes: maxBytes,
      responseLoader: (_) async => utf8.encode(body),
    );

void main() {
  test('parses JSON metadata and keeps coordinates optional', () async {
    final service = _service(
      jsonEncode({
        'ip': '198.51.100.10',
        'country_code': 'US',
        'country_name': 'United States',
        'latitude': 37.09,
        'longitude': -95.71,
      }),
    );
    final location = await service.lookup();
    expect(location.ip, '198.51.100.10');
    expect(location.countryCode, 'US');
    expect(location.latitude, 37.09);
    expect(location.longitude, -95.71);
  });

  test('accepts the actual ip.sb country-name field', () async {
    final location = await _service(
      '{"ip":"198.51.100.10","country_code":"us","country":"United States"}',
    ).lookup();
    expect(location.countryName, 'United States');
    expect(location.countryCode, 'US');
    expect(location.latitude, isNull);
  });

  test('falls back to Cloudflare trace format', () async {
    final service = NetworkLocationService(
      providerUrls: const ['https://bad.invalid', 'https://trace.invalid'],
      responseLoader: (uri) async {
        if (uri.host == 'bad.invalid') throw StateError('down');
        return utf8.encode('ip=198.51.100.10\nloc=US\n');
      },
    );
    expect((await service.lookup()).countryCode, 'US');
  });

  test(
    'rejects private, unspecified, multicast, and mapped private IPs',
    () async {
      for (final ip in [
        '192.168.1.2',
        '10.0.0.1',
        '172.16.0.1',
        '127.0.0.1',
        '0.0.0.0',
        '255.255.255.255',
        '224.0.0.1',
        '::',
        '::1',
        'fc00::1',
        'ff02::1',
        '::ffff:192.168.1.2',
      ]) {
        await expectLater(
          _service(jsonEncode({'ip': ip, 'country_code': 'US'})).lookup(),
          throwsA(isA<StateError>()),
          reason: ip,
        );
      }
    },
  );

  test('rejects invalid ISO country codes and coordinates', () async {
    for (final body in [
      '{"ip":"198.51.100.10","country_code":"USA"}',
      '{"ip":"198.51.100.10","country_code":"1S"}',
      '{"ip":"198.51.100.10","country_code":"US","latitude":91}',
      '{"ip":"198.51.100.10","country_code":"US","longitude":181}',
      '{"ip":"198.51.100.10","country_code":"US","latitude":1e999}',
    ]) {
      await expectLater(_service(body).lookup(), throwsA(isA<StateError>()));
    }
  });

  test('rejects oversized responses', () async {
    await expectLater(
      _service(_fixture, maxBytes: 8).lookup(),
      throwsA(isA<StateError>()),
    );
  });

  test('rejects non-HTTPS providers before making a request', () async {
    var calls = 0;
    final service = NetworkLocationService(
      providerUrls: const ['http://fixture.invalid'],
      responseLoader: (_) async {
        calls++;
        return utf8.encode(_fixture);
      },
    );
    await expectLater(service.lookup(), throwsA(isA<StateError>()));
    expect(calls, 0);
  });

  test('bounds stalled provider responses', () async {
    final response = Completer<List<int>>();
    final service = NetworkLocationService(
      providerUrls: const ['https://fixture.invalid'],
      timeout: const Duration(milliseconds: 20),
      responseLoader: (_) => response.future,
    );
    await expectLater(service.lookup(), throwsA(isA<StateError>()));
    response.complete(utf8.encode(_fixture));
  });

  test('explicitly bypasses the HTTP proxy and closes its client', () async {
    final client = _RecordingClient();
    final service = NetworkLocationService(
      providerUrls: const ['https://fixture.invalid'],
      clientFactory: () => client,
      responseLoader: (_) async => utf8.encode(_fixture),
    );
    await service.lookup();
    expect(client.proxyFor?.call(Uri.parse('https://example.org')), 'DIRECT');
    expect(client.closedForcefully, isTrue);
  });

  test('tracker freezes the first successful startup location', () async {
    var calls = 0;
    final service = NetworkLocationService(
      providerUrls: const ['https://fixture.invalid'],
      responseLoader: (_) async {
        calls++;
        if (calls == 1) throw StateError('temporary');
        return utf8.encode(_fixture);
      },
    );
    final tracker = NetworkLocationTracker(service: service);
    tracker.requestOriginLocation();
    await Future<void>.delayed(Duration.zero);
    expect(tracker.originLocation, isNull);
    expect(tracker.originLocationError, isNotNull);
    tracker.requestOriginLocation();
    expect(calls, 1);
    tracker.requestOriginLocation(retry: true);
    await Future<void>.delayed(Duration.zero);
    await Future<void>.delayed(Duration.zero);
    expect(calls, 2);
    expect(tracker.originLocation?.countryCode, 'US');
    tracker.requestOriginLocation(retry: true);
    expect(calls, 2);
    tracker.dispose();
  });

  test('skips providers that return the known exit IP', () async {
    var calls = 0;
    final service = NetworkLocationService(
      providerUrls: const [
        'https://exit.invalid/geoip',
        'https://origin.invalid/geoip',
      ],
      responseLoader: (uri) async {
        calls++;
        return utf8.encode(
          jsonEncode({
            'ip': uri.host == 'exit.invalid' ? '203.0.113.19' : '198.51.100.10',
            'country_code': uri.host == 'exit.invalid' ? 'NL' : 'US',
          }),
        );
      },
    );

    final location = await service.lookup(excludedIps: ['203.0.113.19']);
    expect(location.ip, '198.51.100.10');
    expect(location.countryCode, 'US');
    expect(calls, 2);
  });

  test(
    'retry discards a cached IP when it matches the connected exit',
    () async {
      var calls = 0;
      final tracker = NetworkLocationTracker(
        service: NetworkLocationService(
          providerUrls: const ['https://fixture.invalid'],
          responseLoader: (_) async {
            calls++;
            return utf8.encode(_fixture);
          },
        ),
      );
      tracker.requestOriginLocation();
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      expect(tracker.originLocation?.ip, '198.51.100.10');

      tracker.requestOriginLocation(
        retry: true,
        excludedIps: const ['198.51.100.10'],
      );
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);

      expect(calls, 2);
      expect(tracker.originLocation, isNull);
      expect(tracker.originLocationError, 'Origin location unavailable');
      tracker.dispose();
    },
  );

  test(
    'suppresses duplicate in-flight requests and post-dispose notifications',
    () async {
      final response = Completer<List<int>>();
      var calls = 0;
      var notifications = 0;
      final tracker = NetworkLocationTracker(
        service: NetworkLocationService(
          providerUrls: const ['https://fixture.invalid'],
          responseLoader: (_) {
            calls++;
            return response.future;
          },
        ),
      )..addListener(() => notifications++);
      tracker.requestOriginLocation();
      tracker.requestOriginLocation(retry: true);
      expect(calls, 1);
      expect(notifications, 1);
      tracker.dispose();
      response.complete(utf8.encode(_fixture));
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      expect(notifications, 1);
      expect(tracker.originLocation, isNull);
    },
  );
}

class _RecordingClient extends Fake implements HttpClient {
  String Function(Uri)? proxyFor;
  bool closedForcefully = false;
  @override
  set connectionTimeout(Duration? value) {}
  @override
  set findProxy(String Function(Uri)? value) => proxyFor = value;
  @override
  void close({bool force = false}) => closedForcefully = force;
}
