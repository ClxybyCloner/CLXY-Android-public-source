import 'dart:io';

import 'core/android_core.dart';
import 'desktop/desktop_bridge.dart';

/// Opens [url] in the system's handler: the browser, or the app for a
/// scheme such as tg://. On Windows the runner opens it through the shell
/// and accepts only http and https links.
Future<void> openUrl(String url) async {
  if (Platform.isAndroid) {
    await AndroidCore.openUrl(url);
  } else if (Platform.isWindows) {
    await DesktopBridge.openUrl(url);
  } else if (Platform.isMacOS) {
    await Process.start('open', [url]);
  } else {
    await Process.start('xdg-open', [url]);
  }
}

/// A private IPv4 address assigned to one of this device's interfaces.
class LanAddress {
  const LanAddress(this.interfaceName, this.address);

  final String interfaceName;
  final String address;

  String get label => '$interfaceName · $address';
}

/// Whether a proxy listen address may accept connections from another device.
/// Wildcard and any non-loopback address are considered shared, so Android
/// keeps the authentication guard active for an interface-specific bind too.
bool sharedWithLan(String listen) {
  final host = _listenHost(listen).toLowerCase();
  if (host.isEmpty || host == '0.0.0.0' || host == '::') return true;
  return host != 'localhost' && host != '::1' && !host.startsWith('127.');
}

/// Whether a listener accepts connections on every interface.
bool listensOnAllInterfaces(String listen) {
  final host = _listenHost(listen).toLowerCase();
  return host.isEmpty || host == '0.0.0.0' || host == '::';
}

String _listenHost(String listen) {
  final colon = listen.lastIndexOf(':');
  var host = colon < 0 ? listen : listen.substring(0, colon);
  if (host.startsWith('[') && host.endsWith(']')) {
    host = host.substring(1, host.length - 1);
  }
  return host;
}

bool _isVpnTunnelInterface(String interfaceName) {
  final name = interfaceName.toLowerCase();
  return ['tun', 'tap', 'wg', 'utun', 'ipsec', 'vti'].any(name.startsWith);
}

/// Filters and ranks private IPv4 interface addresses for local sharing.
/// VPN tunnels are excluded: for example, Android CLXY's `tun0` address
/// (`172.19.0.1`) belongs to the VPN, not the phone's Wi-Fi hotspot.
List<LanAddress> selectLanAddresses(Iterable<LanAddress> candidates) {
  final found = candidates
      .where(
        (address) =>
            address.interfaceName.toLowerCase() != 'lo' &&
            !_isVpnTunnelInterface(address.interfaceName) &&
            isPrivateIPv4(address.address),
      )
      .toList();

  int rank(String name) {
    final value = name.toLowerCase();
    if (['ap', 'softap', 'swlan'].any(value.startsWith)) return 0;
    if (value.startsWith('wlan')) return 1;
    if (value.startsWith('eth')) return 2;
    if (['wi-fi', 'en'].any(value.startsWith)) return 3;
    return 4;
  }

  found.sort((a, b) {
    final byRank = rank(a.interfaceName).compareTo(rank(b.interfaceName));
    if (byRank != 0) return byRank;
    final byName = a.interfaceName.compareTo(b.interfaceName);
    return byName != 0 ? byName : a.address.compareTo(b.address);
  });
  final unique = <String, LanAddress>{};
  for (final address in found) {
    unique.putIfAbsent(address.address, () => address);
  }
  return unique.values.toList(growable: false);
}

/// Private IPv4 addresses on this device, with their interface names.
/// Keeping the interface visible lets the user choose the hotspot address
/// instead of opening the proxy on every network.
Future<List<LanAddress>> lanAddresses() async {
  try {
    final found = <LanAddress>[];
    for (final i in await NetworkInterface.list(
      type: InternetAddressType.IPv4,
    )) {
      for (final a in i.addresses) {
        found.add(LanAddress(i.name, a.address));
      }
    }
    return selectLanAddresses(found);
  } on SocketException {
    return const [];
  }
}

/// This device's first private IPv4 address on the local network, or null.
Future<String?> lanAddress() async =>
    (await lanAddresses()).firstOrNull?.address;

/// Whether [ip] is in 10/8, 172.16/12, or 192.168/16.
bool isPrivateIPv4(String ip) {
  final p = ip.split('.').map(int.tryParse).toList();
  if (p.length != 4 || p.contains(null)) return false;
  return p[0] == 10 ||
      (p[0] == 172 && p[1]! >= 16 && p[1]! <= 31) ||
      (p[0] == 192 && p[1] == 168);
}
