/// Replaces upstream product names, abbreviations, and branded hostnames in
/// text returned by the service before it is shown or copied by the app.
String neutralizeProviderIdentifiers(String text) => text.replaceAll(
  RegExp(
    r'(?:(?:[a-z0-9-]+\.)*(?:mozilla|firefox|fxa|gecko(?:view)?|thunderbird)(?:\.[a-z0-9-]+)+|(?:mozilla|firefox)(?:[\s._/-]+(?:vpn|accounts?|browser|network|client|services?))+(?![a-z0-9])|(?<![a-z0-9])(?:mozilla|firefox|fxa|gecko(?:view)?|thunderbird)(?![a-z0-9]))',
    caseSensitive: false,
  ),
  '[redacted]',
);

/// A short, neutral label for a DNS probe; its actual hostname stays internal.
String dnsProbeLabel(String role, int index) {
  final normalized = role.toLowerCase();
  if (normalized.contains('account')) return 'Account service';
  if (normalized.contains('guardian') || normalized.contains('proxy pass')) {
    return 'Connection service';
  }
  if (normalized.contains('server list') ||
      normalized.contains('remote settings')) {
    return 'Service directory';
  }
  if (normalized.contains('proxy')) return 'Proxy endpoint';
  return 'Service endpoint ${index + 1}';
}
