import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../app_settings.dart';
import '../core/core_controller.dart';
import '../core/models.dart';
import '../core/ports.dart';
import '../platform.dart';
import 'port_dialog.dart';
import 'proxy_profile_copy.dart';
import 'vendor_text.dart';
import 'widgets.dart';

/// Simple iPhone hotspot proxy setup: one toggle, Server and Port, and
/// advanced controls kept out of the way until needed.
class ProxyProfilePage extends StatefulWidget {
  const ProxyProfilePage({
    super.key,
    required this.core,
    required this.settings,
    this.loadAddresses,
    this.isPortAvailable,
  });

  final CoreController core;
  final AppSettings settings;
  final Future<List<LanAddress>> Function()? loadAddresses;
  final Future<bool> Function(int port)? isPortAvailable;

  @override
  State<ProxyProfilePage> createState() => _ProxyProfilePageState();
}

class _ProxyProfilePageState extends State<ProxyProfilePage> {
  static const _hotspotSharingSupported = bool.fromEnvironment(
    'CLXY_HOTSPOT_SHARING',
    defaultValue: false,
  );
  static const _defaultSharePort = 11811;
  static const _defaultSocksPort = 11812;

  final _port = TextEditingController();
  final _socksPort = TextEditingController();
  bool _loading = true;
  bool _loadFailed = false;
  bool _shareEnabled = false;
  bool _savingSharing = false;
  bool _savingPort = false;
  List<LanAddress> _addresses = const [];
  String? _selectedAddress;
  String? _selectedInterface;
  String _configuredListen = '';
  String _configuredSocksListen = '';
  String _configuredInterface = '';

  @override
  void initState() {
    super.initState();
    if (_hotspotSharingSupported) _load();
  }

  @override
  void dispose() {
    _port.dispose();
    _socksPort.dispose();
    super.dispose();
  }

  bool get _coreRunning => widget.core.status?.state.phase == Phase.running;
  bool get _canCopyProfile =>
      !_loading &&
      !_loadFailed &&
      _serverValue.isNotEmpty &&
      _portValue.isNotEmpty;
  HotspotShareStatus? get _shareStatus => widget.core.status?.hotspotShare;
  bool get _shareRunning => _shareStatus?.running == true;
  bool get _socksRunning => _shareStatus?.socksRunning == true;
  bool get _shareReady => _shareRunning && _socksRunning;

  String get _listenerError {
    final status = _shareStatus;
    final errors = <String>[
      if (status?.error.isNotEmpty == true) 'HTTP: ${status!.error}',
      if (status?.socksError.isNotEmpty == true)
        'SOCKS5: ${status!.socksError}',
    ];
    return neutralizeProviderIdentifiers(errors.join('\n'));
  }

  bool _isAvailableAddress(String? address, String? interfaceName) =>
      address != null &&
      interfaceName != null &&
      _addresses.any(
        (candidate) =>
            candidate.address == address &&
            candidate.interfaceName == interfaceName,
      );

  String get _serverValue {
    final status = _shareStatus;
    if (_shareEnabled && status != null && status.listen.isNotEmpty) {
      return _listenHost(status.listen);
    }
    return _selectedAddress ?? '';
  }

  String get _portValue {
    final status = _shareStatus;
    if (_shareEnabled && status != null && status.listen.isNotEmpty) {
      return '${portOf(status.listen) ?? _defaultSharePort}';
    }
    return _port.text;
  }

  String get _socksPortValue {
    final status = _shareStatus;
    if (_shareEnabled && status != null && status.socksListen.isNotEmpty) {
      return '${portOf(status.socksListen) ?? _defaultSocksPort}';
    }
    return _socksPort.text;
  }

  bool get _needsApply {
    if (!_shareEnabled) return false;
    final configuredHost = _hostFromListen(_configuredListen);
    final configuredSocksHost = _hostFromListen(_configuredSocksListen);
    final configuredPort = portOf(_configuredListen);
    final configuredSocksPort = portOf(_configuredSocksListen);
    return _selectedAddress != configuredHost ||
        configuredSocksHost != configuredHost ||
        _selectedInterface != _configuredInterface ||
        int.tryParse(_port.text) != configuredPort ||
        int.tryParse(_socksPort.text) != configuredSocksPort;
  }

  Future<void> _load() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _loadFailed = false;
      });
    }
    try {
      final config = await widget.core.settings();
      final addresses = selectLanAddresses(
        await (widget.loadAddresses?.call() ?? lanAddresses()),
      );
      final share = config['hotspot_share'];
      final shareConfig = share is Map<String, Object?>
          ? share
          : const <String, Object?>{};
      final enabled = shareConfig['enabled'] == true;
      final listen = shareConfig['listen'] as String? ?? '';
      final socksListen = shareConfig['socks_listen'] as String? ?? '';
      final interfaceName = shareConfig['interface'] as String? ?? '';
      final configuredHost = _hostFromListen(listen);
      final effectiveSocksListen = socksListen.isNotEmpty
          ? socksListen
          : configuredHost == null
          ? ''
          : '$configuredHost:$_defaultSocksPort';
      final currentAddress = _selectedAddress;
      final currentInterface = _selectedInterface;

      LanAddress? selected;
      for (final address in addresses) {
        if (address.address == configuredHost &&
            address.interfaceName == interfaceName) {
          selected = address;
          break;
        }
      }
      selected ??= addresses.firstOrNullWhere(
        (address) =>
            address.address == currentAddress &&
            address.interfaceName == currentInterface,
      );
      selected ??= addresses.firstOrNull;

      if (!mounted) return;
      setState(() {
        _addresses = addresses;
        _shareEnabled = enabled;
        _configuredListen = listen;
        _configuredSocksListen = effectiveSocksListen;
        _configuredInterface = interfaceName;
        _selectedAddress = selected?.address;
        _selectedInterface = selected?.interfaceName;
        _port.text = '${portOf(listen) ?? _defaultSharePort}';
        _socksPort.text =
            '${portOf(effectiveSocksListen) ?? _defaultSocksPort}';
        _loading = false;
      });
    } on Exception {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _loadFailed = true;
      });
    }
  }

  static String _listenHost(String listen) {
    final colon = listen.lastIndexOf(':');
    var host = colon < 0 ? listen : listen.substring(0, colon);
    if (host.startsWith('[') && host.endsWith(']')) {
      host = host.substring(1, host.length - 1);
    }
    return host;
  }

  static String? _hostFromListen(String listen) {
    if (listen.isEmpty) return null;
    final host = _listenHost(listen);
    return isPrivateIPv4(host) ? host : null;
  }

  Future<void> _toggleSharing(bool enabled) async {
    if (enabled &&
        (!isPrivateIPv4(_selectedAddress ?? '') ||
            (_selectedInterface ?? '').isEmpty ||
            !_isAvailableAddress(_selectedAddress, _selectedInterface))) {
      _toast(ProxyProfileCopy.of(context).hostMissing);
      return;
    }
    await _saveShare(enabled: enabled);
  }

  Future<void> _applyAddress() async {
    final address = _selectedAddress;
    final interfaceName = _selectedInterface;
    if (address == null ||
        !isPrivateIPv4(address) ||
        interfaceName == null ||
        interfaceName.isEmpty ||
        !_isAvailableAddress(address, interfaceName)) {
      _toast(ProxyProfileCopy.of(context).invalidBindAddress);
      return;
    }
    await _saveShare(
      enabled: _shareEnabled,
      host: address,
      interfaceName: interfaceName,
    );
  }

  Future<bool> _saveShare({
    required bool enabled,
    String? host,
    String? interfaceName,
    int? httpPort,
    int? socksPort,
    bool showToast = true,
  }) async {
    if (_savingSharing) return false;
    final effectiveHost =
        host ?? _selectedAddress ?? _hostFromListen(_configuredListen);
    final effectiveInterface =
        interfaceName ?? _selectedInterface ?? _configuredInterface;
    final effectiveHttpPort =
        httpPort ?? int.tryParse(_port.text) ?? _defaultSharePort;
    final effectiveSocksPort =
        socksPort ?? int.tryParse(_socksPort.text) ?? _defaultSocksPort;
    if (enabled &&
        (effectiveHost == null ||
            !isPrivateIPv4(effectiveHost) ||
            effectiveInterface.isEmpty ||
            !_isAvailableAddress(effectiveHost, effectiveInterface))) {
      _toast(ProxyProfileCopy.of(context).invalidBindAddress);
      return false;
    }

    setState(() => _savingSharing = true);
    final saved = await runShowingErrors(
      context,
      () => widget.core.editSettings((config) {
        final existing = config['hotspot_share'];
        final share = existing is Map<String, Object?>
            ? existing
            : <String, Object?>{};
        share['enabled'] = enabled;
        if (effectiveHost != null && effectiveHost.isNotEmpty) {
          share['listen'] = '$effectiveHost:$effectiveHttpPort';
          share['socks_listen'] = '$effectiveHost:$effectiveSocksPort';
        }
        if (effectiveInterface.isNotEmpty) {
          share['interface'] = effectiveInterface;
        }
        config['hotspot_share'] = share;
      }),
    );
    if (!mounted) return false;
    setState(() => _savingSharing = false);
    if (!saved) return false;

    _selectedAddress = effectiveHost;
    _selectedInterface = effectiveInterface.isEmpty ? null : effectiveInterface;
    _port.text = '$effectiveHttpPort';
    _socksPort.text = '$effectiveSocksPort';
    await widget.core.refresh();
    await _load();
    if (mounted && showToast) _toast(ProxyProfileCopy.of(context).sharingSaved);
    return true;
  }

  Future<void> _changePort({required bool socks5}) async {
    final copy = ProxyProfileCopy.of(context);
    final controller = socks5 ? _socksPort : _port;
    final value = await showPortDialog(
      context,
      current: controller.text,
      isAvailable: widget.isPortAvailable,
    );
    if (value == null || !mounted) return;
    setState(() => _savingPort = true);
    final saved = await _saveShare(
      enabled: _shareEnabled,
      httpPort: socks5 ? null : value,
      socksPort: socks5 ? value : null,
      showToast: false,
    );
    if (!mounted) return;
    setState(() => _savingPort = false);
    if (saved && mounted) {
      _toast(socks5 ? copy.socksPortSaved : copy.portSaved);
    }
  }

  void _chooseAddress(String? address) {
    if (address == null) return;
    final selected = _addresses.firstOrNullWhere(
      (item) => item.address == address,
    );
    setState(() {
      _selectedAddress = address;
      _selectedInterface = selected?.interfaceName;
    });
  }

  Future<void> _copy(String value, String message) async {
    if (value.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: value));
    if (mounted) _toast(message);
  }

  Future<void> _copySetup() async {
    if (!_canCopyProfile) return;
    final copy = ProxyProfileCopy.of(context);
    await _copy(
      '${copy.host}: $_serverValue\n${copy.httpPort}: $_portValue\n${copy.socks5Port}: $_socksPortValue',
      copy.profileCopied,
    );
  }

  void _toast(String text) => ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(text), behavior: SnackBarBehavior.floating),
  );

  Widget _endpointRow({
    required BuildContext context,
    required String label,
    required String value,
    required String tooltip,
    required VoidCallback? onCopy,
  }) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: theme.textTheme.labelMedium),
              const SizedBox(height: 4),
              SelectableText(
                value.isEmpty ? '—' : value,
                textDirection: TextDirection.ltr,
                style: theme.textTheme.titleLarge?.copyWith(
                  fontFamily: 'monospace',
                  color: value.isEmpty
                      ? theme.colorScheme.onSurfaceVariant
                      : Midnight.connected,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
        IconButton(
          tooltip: tooltip,
          onPressed: onCopy,
          icon: const Icon(Icons.content_copy_rounded),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final copy = ProxyProfileCopy.of(context);
    final theme = Theme.of(context);
    final error = _listenerError;

    if (!_hotspotSharingSupported) {
      return ListView(
        padding: pagePadding(context),
        children: [
          PageTitle(copy.title),
          const SizedBox(height: 8),
          Panel(
            title: copy.unavailableTitle,
            icon: Icons.info_outline_rounded,
            child: Text(
              copy.unavailableInTestBuild,
              style: theme.textTheme.bodyMedium,
            ),
          ),
        ],
      );
    }

    return ListView(
      padding: pagePadding(context),
      children: [
        PageTitle(copy.title),
        const SizedBox(height: 8),
        Text(copy.intro, style: theme.textTheme.bodyMedium),
        const SizedBox(height: 16),
        Panel(
          title: copy.sharingTitle,
          icon: Icons.wifi_tethering_rounded,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: Text(copy.sharingToggle),
                subtitle: Text(copy.sharingHint),
                value: _shareEnabled,
                onChanged: _loading || _loadFailed || _savingSharing
                    ? null
                    : _toggleSharing,
              ),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsetsDirectional.fromSTEB(14, 8, 8, 8),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color:
                        (_shareReady
                                ? Midnight.connected
                                : theme.colorScheme.outlineVariant)
                            .withValues(alpha: 0.45),
                  ),
                ),
                child: Column(
                  children: [
                    _endpointRow(
                      context: context,
                      label: copy.host,
                      value: _serverValue,
                      tooltip: copy.copyHost,
                      onCopy: _serverValue.isEmpty
                          ? null
                          : () => _copy(_serverValue, copy.copied),
                    ),
                    Divider(height: 1, color: theme.colorScheme.outlineVariant),
                    _endpointRow(
                      context: context,
                      label: copy.httpPort,
                      value: _portValue,
                      tooltip: copy.copyPort,
                      onCopy: _portValue.isEmpty
                          ? null
                          : () => _copy(_portValue, copy.copied),
                    ),
                    Divider(height: 1, color: theme.colorScheme.outlineVariant),
                    _endpointRow(
                      context: context,
                      label: copy.socks5Port,
                      value: _socksPortValue,
                      tooltip: copy.copyPort,
                      onCopy: _socksPortValue.isEmpty
                          ? null
                          : () => _copy(_socksPortValue, copy.copied),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Icon(
                    _shareReady
                        ? Icons.check_circle_rounded
                        : _shareEnabled && error.isNotEmpty
                        ? Icons.error_rounded
                        : Icons.info_outline_rounded,
                    size: 18,
                    color: _shareReady
                        ? Midnight.connected
                        : _shareEnabled && error.isNotEmpty
                        ? theme.colorScheme.error
                        : theme.colorScheme.onSurfaceVariant,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      !_shareEnabled
                          ? copy.listenerOff
                          : error.isNotEmpty
                          ? '${copy.bindFailed}\n$error'
                          : _shareReady
                          ? copy.listening
                          : copy.waitingForCLXY,
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
                ],
              ),
              if (_shareEnabled && !_coreRunning && error.isEmpty) ...[
                const SizedBox(height: 8),
                Text(copy.coreMustRun, style: theme.textTheme.bodySmall),
              ],
              if (_shareEnabled && _needsApply) ...[
                const SizedBox(height: 10),
                Text(copy.addressChanged, style: theme.textTheme.bodySmall),
              ],
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: _canCopyProfile ? _copySetup : null,
                icon: const Icon(Icons.content_copy_rounded),
                label: Text(copy.copyProfile),
              ),
              const SizedBox(height: 16),
              Text(copy.shareClientHint, style: theme.textTheme.bodySmall),
              const SizedBox(height: 8),
              Text(copy.telegramHint, style: theme.textTheme.bodySmall),
              const SizedBox(height: 8),
              Text(copy.shareLimits, style: theme.textTheme.bodySmall),
              const SizedBox(height: 12),
              Card(
                color: theme.colorScheme.tertiaryContainer,
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.warning_amber_rounded,
                        color: theme.colorScheme.onTertiaryContainer,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              copy.securityTitle,
                              style: theme.textTheme.titleSmall?.copyWith(
                                color: theme.colorScheme.onTertiaryContainer,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              copy.securityWarning,
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onTertiaryContainer,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 4),
              ExpansionTile(
                tilePadding: EdgeInsets.zero,
                childrenPadding: EdgeInsets.zero,
                title: Text(copy.advancedSettings),
                subtitle: Text(copy.networkInterface),
                children: [
                  if (_addresses.isNotEmpty)
                    DropdownButtonFormField<String>(
                      key: ValueKey<String?>(_selectedAddress),
                      initialValue:
                          _addresses.any(
                            (address) => address.address == _selectedAddress,
                          )
                          ? _selectedAddress
                          : null,
                      decoration: InputDecoration(
                        labelText: copy.networkInterface,
                        border: const OutlineInputBorder(),
                        suffixIcon: IconButton(
                          tooltip: copy.refreshAddresses,
                          onPressed: _loading || _savingSharing ? null : _load,
                          icon: const Icon(Icons.refresh_rounded),
                        ),
                      ),
                      items: [
                        for (final address in _addresses)
                          DropdownMenuItem(
                            value: address.address,
                            child: Text(
                              address.label,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                      ],
                      onChanged: _savingSharing ? null : _chooseAddress,
                    )
                  else
                    Text(copy.noAddresses, style: theme.textTheme.bodySmall),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(child: Text('${copy.httpPort}: $_portValue')),
                      TextButton.icon(
                        onPressed: _savingPort || _savingSharing
                            ? null
                            : () => _changePort(socks5: false),
                        icon: _savingPort
                            ? const SizedBox.square(
                                dimension: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.edit_outlined),
                        label: Text(copy.changeHttpPort),
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      Expanded(
                        child: Text('${copy.socks5Port}: $_socksPortValue'),
                      ),
                      TextButton.icon(
                        onPressed: _savingPort || _savingSharing
                            ? null
                            : () => _changePort(socks5: true),
                        icon: _savingPort
                            ? const SizedBox.square(
                                dimension: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.edit_outlined),
                        label: Text(copy.changeSocksPort),
                      ),
                    ],
                  ),
                  if (_shareEnabled && _needsApply) ...[
                    const SizedBox(height: 4),
                    Align(
                      alignment: AlignmentDirectional.centerEnd,
                      child: FilledButton.icon(
                        onPressed: _savingSharing ? null : _applyAddress,
                        icon: _savingSharing
                            ? const SizedBox.square(
                                dimension: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.wifi_tethering_rounded),
                        label: Text(copy.bindSelected),
                      ),
                    ),
                  ],
                ],
              ),
              if (_loading) ...[
                const SizedBox(height: 8),
                const LinearProgressIndicator(),
              ],
              if (_loadFailed) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: Text(copy.loadFailed)),
                    TextButton.icon(
                      onPressed: _load,
                      icon: const Icon(Icons.refresh_rounded),
                      label: Text(copy.retry),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

extension on Iterable<LanAddress> {
  LanAddress? firstOrNullWhere(bool Function(LanAddress) test) {
    for (final address in this) {
      if (test(address)) return address;
    }
    return null;
  }
}
