import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/ui/vendor_text.dart';

void main() {
  test('provider names and complete hostnames are neutralized', () {
    const input =
        'Mozilla Firefox Accounts api.accounts.firefox.com vpn.mozilla.org '
        'firefox.settings.services.mozilla.com FxA GeckoView';
    final output = neutralizeProviderIdentifiers(input).toLowerCase();

    expect(output, isNot(contains('mozilla')));
    expect(output, isNot(contains('firefox')));
    expect(output, isNot(contains('fxa')));
    expect(output, isNot(contains('gecko')));
    expect(output, contains('[redacted]'));
  });

  test('DNS probe roles map to neutral labels', () {
    expect(dnsProbeLabel('Firefox Accounts', 0), 'Account service');
    expect(dnsProbeLabel('Guardian (proxy pass)', 1), 'Connection service');
    expect(dnsProbeLabel('Server list', 2), 'Service directory');
    expect(dnsProbeLabel('Proxy server (Fastly)', 3), 'Proxy endpoint');
    expect(dnsProbeLabel('unknown', 4), 'Service endpoint 5');
  });
}
