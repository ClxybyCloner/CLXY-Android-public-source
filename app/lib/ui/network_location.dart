import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

/// Country-level approximation of the network visible at app startup.
/// Coordinates, when present, are provider metadata and are not GPS.
class NetworkLocation {
  NetworkLocation.fromJson(Map<String, Object?> json)
    : ip = json['ip'] is String ? json['ip'] as String : '',
      countryCode = json['country_code'] is String
          ? json['country_code'] as String
          : '',
      countryName = json['country_name'] is String
          ? json['country_name'] as String
          : '',
      latitude = json['latitude'] is num
          ? (json['latitude'] as num).toDouble()
          : null,
      longitude = json['longitude'] is num
          ? (json['longitude'] as num).toDouble()
          : null;

  final String ip;
  final String countryCode;
  final String countryName;
  final double? latitude;
  final double? longitude;
}

String _normalizedIp(String value) => value.trim().toLowerCase();

typedef OriginHttpClientFactory = HttpClient Function();
typedef OriginResponseLoader = Future<List<int>> Function(Uri uri);

/// Performs one direct, authenticated-by-TLS public-IP lookup. It deliberately
/// does not use the CLXY proxy or the operating-system HTTP proxy settings.
class NetworkLocationService {
  NetworkLocationService({
    this.providerUrls = const <String>[
      'https://api.ip.sb/geoip',
      'https://www.cloudflare.com/cdn-cgi/trace',
      'https://ipapi.co/json/',
      'https://ipwho.is/',
    ],
    OriginHttpClientFactory? clientFactory,
    this.responseLoader,
    this.timeout = const Duration(seconds: 5),
    this.maxResponseBytes = 64 * 1024,
  }) : _clientFactory = clientFactory ?? _defaultClient;

  final List<String> providerUrls;
  final Duration timeout;
  final int maxResponseBytes;
  final OriginResponseLoader? responseLoader;
  final OriginHttpClientFactory _clientFactory;

  Future<NetworkLocation> lookup({
    Iterable<String> excludedIps = const [],
  }) async {
    final excluded = excludedIps
        .map(_normalizedIp)
        .where((ip) => ip.isNotEmpty)
        .toSet();
    Object? lastError;
    for (final raw in providerUrls) {
      try {
        final result = await _lookupProvider(raw);
        if (excluded.contains(_normalizedIp(result.ip))) {
          throw const FormatException(
            'origin lookup matched the connected exit IP',
          );
        }
        return result;
      } on Object catch (error) {
        lastError = error;
      }
    }
    throw StateError(
      lastError == null
          ? 'origin location unavailable'
          : 'origin location unavailable',
    );
  }

  Future<NetworkLocation> _lookupProvider(String raw) async {
    final uri = Uri.tryParse(raw);
    if (uri == null ||
        uri.scheme.toLowerCase() != 'https' ||
        uri.host.isEmpty) {
      throw const FormatException('origin provider must use HTTPS');
    }
    final client = _clientFactory()
      ..connectionTimeout = timeout
      ..findProxy = (_) => 'DIRECT';
    try {
      if (responseLoader != null) {
        final bytes = await responseLoader!(uri).timeout(timeout);
        if (bytes.length > maxResponseBytes) {
          throw const FormatException('origin provider response too large');
        }
        return _parse(bytes);
      }
      final request = await client.getUrl(uri).timeout(timeout);
      request.followRedirects = false;
      request.headers
        ..set(HttpHeaders.acceptHeader, 'application/json, text/plain')
        ..set(HttpHeaders.userAgentHeader, 'clxy-origin-check/1');
      final response = await request.close().timeout(timeout);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw HttpException(
          'origin provider returned HTTP ${response.statusCode}',
        );
      }
      final bytes = await response
          .fold<List<int>>(<int>[], (buffer, chunk) {
            if (buffer.length + chunk.length > maxResponseBytes) {
              throw const FormatException('origin provider response too large');
            }
            return buffer..addAll(chunk);
          })
          .timeout(timeout);
      return _parse(bytes);
    } finally {
      client.close(force: true);
    }
  }

  NetworkLocation _parse(List<int> bytes) {
    final text = utf8.decode(bytes);
    Map<String, Object?>? json;
    try {
      final decoded = jsonDecode(text);
      if (decoded is Map<String, Object?>) json = decoded;
    } on FormatException {
      json = null;
    }
    final fields = <String, String>{};
    if (json == null) {
      for (final line in text.split('\n')) {
        final pair = line.trim().split('=');
        if (pair.length >= 2) {
          fields[pair.first.trim().toLowerCase()] = pair
              .sublist(1)
              .join('=')
              .trim();
        }
      }
      json = <String, Object?>{
        'ip': fields['ip'],
        'country_code': fields['loc'],
      };
    }
    final location = NetworkLocation.fromJson(json);
    final ip = InternetAddress.tryParse(location.ip);
    final code = location.countryCode.toUpperCase();
    if (ip == null || _isNonPublic(ip)) {
      throw const FormatException(
        'origin provider returned an invalid public IP',
      );
    }
    if (code.length != 2 ||
        code.codeUnitAt(0) < 65 ||
        code.codeUnitAt(0) > 90 ||
        code.codeUnitAt(1) < 65 ||
        code.codeUnitAt(1) > 90) {
      throw const FormatException(
        'origin provider returned an invalid country code',
      );
    }
    final lat = location.latitude;
    final lon = location.longitude;
    if ((lat != null && (lat < -90 || lat > 90)) ||
        (lon != null && (lon < -180 || lon > 180))) {
      throw const FormatException(
        'origin provider returned invalid coordinates',
      );
    }
    return NetworkLocation.fromJson({
      'ip': location.ip,
      'country_code': code,
      'country_name': location.countryName.isNotEmpty
          ? location.countryName
          : json['country'] is String
          ? json['country'] as String
          : '',
      'latitude': ?lat,
      'longitude': ?lon,
    });
  }

  bool _isNonPublic(InternetAddress address) {
    if (address.isLoopback || address.isLinkLocal || address.isMulticast) {
      return true;
    }
    final bytes = address.rawAddress;
    if (address.type == InternetAddressType.IPv4) {
      final a = bytes[0], b = bytes[1];
      return a == 0 ||
          a >= 224 ||
          a == 10 ||
          (a == 172 && b >= 16 && b <= 31) ||
          (a == 192 && b == 168) ||
          (a == 100 && b >= 64 && b <= 127) ||
          (a == 169 && b == 254);
    }
    if (bytes.every((b) => b == 0)) return true;
    if (bytes.take(10).every((b) => b == 0) &&
        bytes[10] == 0xff &&
        bytes[11] == 0xff) {
      return _isNonPublic(InternetAddress.fromRawAddress(bytes.sublist(12)));
    }
    return (bytes[0] & 0xfe) == 0xfc;
  }

  static HttpClient _defaultClient() => HttpClient();
}

/// Keeps the first successful startup lookup for this app session; a retry
/// may discard a result that is known to be the connected exit IP.
class NetworkLocationTracker extends ChangeNotifier {
  NetworkLocationTracker({NetworkLocationService? service})
    : _service = service ?? NetworkLocationService();

  final NetworkLocationService _service;
  NetworkLocation? originLocation;
  bool originLocationLoading = false;
  String? originLocationError;
  bool _disposed = false;
  bool _requested = false;
  bool _attempting = false;

  void requestOriginLocation({
    bool retry = false,
    Iterable<String> excludedIps = const [],
  }) {
    if (_disposed || _attempting) return;
    final excluded = excludedIps
        .map(_normalizedIp)
        .where((ip) => ip.isNotEmpty)
        .toSet();
    final currentIp = originLocation == null
        ? ''
        : _normalizedIp(originLocation!.ip);
    if (originLocation != null && (!retry || !excluded.contains(currentIp))) {
      return;
    }
    if (originLocation != null) originLocation = null;
    if (_requested && !retry) return;
    _requested = true;
    if (retry) originLocationError = null;
    _attempt(excluded);
  }

  void _attempt(Set<String> excludedIps) {
    if (_disposed || _attempting || originLocation != null) return;
    _attempting = true;
    originLocationLoading = true;
    _notify();
    unawaited(() async {
      try {
        final result = await _service.lookup(excludedIps: excludedIps);
        if (!_disposed && originLocation == null) {
          originLocation = result;
          originLocationError = null;
        }
      } on Object catch (_) {
        if (!_disposed) originLocationError = 'Origin location unavailable';
      } finally {
        _attempting = false;
        if (!_disposed) {
          originLocationLoading = false;
          _notify();
        }
      }
    }());
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
