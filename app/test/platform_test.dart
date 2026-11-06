import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/platform.dart';

void main() {
  test('a listen address on every interface is shared with the LAN', () {
    expect(sharedWithLan('0.0.0.0:10810'), isTrue);
    expect(sharedWithLan('[::]:10810'), isTrue);
    expect(sharedWithLan('192.168.43.1:10810'), isTrue);
    expect(sharedWithLan('127.0.0.1:10810'), isFalse);
    expect(sharedWithLan('localhost:10810'), isFalse);
    expect(listensOnAllInterfaces('0.0.0.0:10810'), isTrue);
    expect(listensOnAllInterfaces('192.168.43.1:10810'), isFalse);
  });

  test('private IPv4 addresses', () {
    for (final ip in [
      '10.0.0.2',
      '172.16.1.1',
      '172.31.255.1',
      '192.168.1.5',
    ]) {
      expect(isPrivateIPv4(ip), isTrue, reason: ip);
    }
    for (final ip in [
      '172.32.0.1',
      '8.8.8.8',
      '127.0.0.1',
      'fe80::1',
      '1.2.3',
    ]) {
      expect(isPrivateIPv4(ip), isFalse, reason: ip);
    }
  });

  test(
    'LAN selection excludes VPN tunnels and prefers the hotspot interface',
    () {
      final selected = selectLanAddresses(const [
        LanAddress('tun0', '172.19.0.1'),
        LanAddress('wg0', '10.8.0.1'),
        LanAddress('wlan0', '192.168.43.2'),
        LanAddress('ap0', '172.19.0.2'),
        LanAddress('lo', '192.168.1.1'),
      ]);

      expect(selected.map((address) => address.label).toList(), [
        'ap0 · 172.19.0.2',
        'wlan0 · 192.168.43.2',
      ]);
    },
  );

  test('a VPN tunnel address alone is not a selectable LAN address', () {
    expect(
      selectLanAddresses(const [LanAddress('tun0', '172.19.0.1')]),
      isEmpty,
    );
  });

  test('the Android swlan hotspot address remains selectable', () {
    final selected = selectLanAddresses(const [
      LanAddress('tun0', '172.19.0.1'),
      LanAddress('swlan0', '192.168.24.102'),
    ]);

    expect(selected, [const LanAddress('swlan0', '192.168.24.102')]);
  });
}
