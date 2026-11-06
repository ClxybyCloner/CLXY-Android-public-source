import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart' as wf;

import '../app_settings.dart';
import '../core/android_core.dart';
import '../core/core_api.dart';
import '../core/core_controller.dart';
import '../l10n/app_localizations.dart';
import 'signin_dialog.dart';
import 'vendor_text.dart';
import 'widgets.dart';

/// Browser sign-in is available on Android, where the system WebView can use
/// the core's temporary local proxy.
bool get browserSignInSupported =>
    defaultTargetPlatform == TargetPlatform.android;

/// Whether the selected new-account method can open the in-app browser.
bool shouldUseBrowserSignIn(SignInMethod method, {TargetPlatform? platform}) =>
    method == SignInMethod.browser &&
    (platform ?? defaultTargetPlatform) == TargetPlatform.android;

/// Adds an account using the saved method, falling back to the email/password
/// form on platforms without the in-app browser.
Future<void> startAddAccount(
  BuildContext context,
  CoreController core,
  AppSettings settings, {
  String email = '',
}) async {
  if (shouldUseBrowserSignIn(settings.signInMethod)) {
    await Navigator.of(context, rootNavigator: true).push(
      MaterialPageRoute<void>(
        builder: (_) => BrowserSignInPage(core: core, email: email),
      ),
    );
  } else {
    await showSignInDialog(context, core, email: email);
  }
}

/// The account service's sync page sends a WebChannel login message that
/// carries the session token the local core exchanges for account tokens.
String _loginUrl(String email) => Uri.https('accounts.firefox.com', '/', {
  'context': 'fx_desktop_v3',
  'service': 'sync',
  'action': 'email',
  if (email.isNotEmpty) 'email': email,
}).toString();

/// The account service only exposes its sign-in WebChannel to a Firefox-like
/// user agent.
const _userAgent =
    'Mozilla/5.0 (Android 14; Mobile; rv:145.0) Gecko/145.0 Firefox/145.0';

Future<bool> _setViewProxy(String address) async {
  try {
    return await AndroidCore.setWebViewProxy(address);
  } on PlatformException {
    return false;
  } on MissingPluginException {
    return false;
  }
}

/// Follows the sign-in page's WebChannel: answers its status questions and
/// forwards only the captured session token and email to the core.
class FxaWebChannel {
  FxaWebChannel({required this.reply, required this.onSession});

  final void Function(String js) reply;
  final void Function(String email, String sessionToken) onSession;

  String _token = '';

  static final _tokenPattern = RegExp(r'^[0-9a-f]{64}$');

  /// Injects a listener into the page; [post] sends its object argument to
  /// Dart, and the page's reply callback is kept within this WebView.
  static String hookJs(String post) =>
      '''
(function () {
  if (window.__clxyHooked) return;
  window.__clxyHooked = true;
  var send = function (o) { try { $post } catch (e) {} };
  var onChannel = function (e) {
    var d = e.detail;
    if (typeof d === 'string') { try { d = JSON.parse(d); } catch (e) { return; } }
    if (d && d.message) send({ kind: 'webchannel', detail: d });
  };
  window.addEventListener('WebChannelMessageToChrome', onChannel);
  document.addEventListener('WebChannelMessageToChrome', onChannel);
  window.__clxyReply = function (id, messageId, command, data) {
    var detail = { id: id, message: { messageId: messageId, command: command, data: data } };
    window.dispatchEvent(new CustomEvent('WebChannelMessageToContent', { detail: detail }));
    document.dispatchEvent(new CustomEvent('WebChannelMessageToContent', { detail: detail }));
  };
})();
''';

  /// Accepts a JSON message from webview_flutter's JavaScript channel.
  void handle(Object? raw) {
    Object? message = raw;
    if (message is String) {
      try {
        message = jsonDecode(message);
      } on FormatException {
        return;
      }
    }
    if (message is! Map || message['kind'] != 'webchannel') return;
    _webchannel(message['detail']);
  }

  void _webchannel(Object? detail) {
    if (detail is! Map) return;
    final id = detail['id'];
    final message = detail['message'];
    if (id is! String || message is! Map) return;
    final command = message['command'];
    final messageId = message['messageId'];
    switch (command) {
      case 'fxaccounts:fxa_status':
        _reply(id, messageId, command as String, {
          'capabilities': <String, Object?>{},
          'signedInUser': null,
        });
      case 'fxaccounts:can_link_account':
        _reply(id, messageId, command as String, {'ok': true});
      case 'fxaccounts:login' || 'fxaccounts:oauth_login':
        _login(message['data']);
    }
  }

  void _reply(String id, Object? messageId, String command, Object data) =>
      reply(
        'window.__clxyReply(${jsonEncode(id)}, ${jsonEncode(messageId)}, '
        '${jsonEncode(command)}, ${jsonEncode(data)})',
      );

  /// The page may repeat the same session message after confirmation; only a
  /// new, valid 32-byte token starts another core sign-in.
  void _login(Object? data) {
    if (data is! Map) return;
    final token = data['sessionToken'] ?? data['token'];
    if (token is! String || !_tokenPattern.hasMatch(token) || token == _token) {
      return;
    }
    _token = token;
    final email = data['email'];
    onSession(email is String ? email : '', token);
  }
}

/// Signs in through the in-app page; the captured session is exchanged by the
/// local core, which also waits for any confirmation requested by the site.
class BrowserSignInPage extends StatefulWidget {
  const BrowserSignInPage({super.key, required this.core, this.email = ''});

  final CoreController core;
  final String email;

  @override
  State<BrowserSignInPage> createState() => _BrowserSignInPageState();
}

class _BrowserSignInPageState extends State<BrowserSignInPage> {
  late final FxaWebChannel _channel = FxaWebChannel(
    reply: _runJs,
    onSession: _gotSession,
  );

  wf.WebViewController? _view;
  String? _error;
  bool _loading = true;
  String? _signIn;
  Timer? _follow;
  bool _reading = false;
  bool _proxyOpen = false;
  bool _viewProxySet = false;

  @override
  void initState() {
    super.initState();
    _init();
  }

  @override
  void dispose() {
    _stopFollowing(cancel: true);
    if (_viewProxySet) _setViewProxy('').ignore();
    if (_proxyOpen) widget.core.api?.closeSignInProxy().ignore();
    super.dispose();
  }

  Future<void> _init() async {
    final proxy = await _openProxy();
    if (!mounted) return;
    try {
      await _initView(proxy);
    } on Exception catch (e) {
      if (mounted) setState(() => _error = '$e');
    }
  }

  /// The core's local proxy applies its configured DNS and connection path.
  /// If it cannot start, the WebView can still try its normal connection.
  Future<String?> _openProxy() async {
    final api = widget.core.api;
    if (api == null) return null;
    try {
      final address = await api.openSignInProxy();
      if (!mounted) {
        api.closeSignInProxy().ignore();
        return null;
      }
      _proxyOpen = address != null;
      return address;
    } on Exception {
      return null;
    }
  }

  Future<void> _initView(String? proxy) async {
    if (proxy != null) {
      final set = await _setViewProxy(proxy);
      if (!mounted) {
        if (set) _setViewProxy('').ignore();
        return;
      }
      _viewProxySet = set;
    }
    final hook = FxaWebChannel.hookJs(
      'ClxyFxa.postMessage(JSON.stringify(o));',
    );
    late final wf.WebViewController controller;
    controller = wf.WebViewController()
      ..setJavaScriptMode(wf.JavaScriptMode.unrestricted)
      ..setUserAgent(_userAgent)
      ..addJavaScriptChannel(
        'ClxyFxa',
        onMessageReceived: (message) => _channel.handle(message.message),
      )
      ..setNavigationDelegate(
        wf.NavigationDelegate(
          onPageStarted: (_) => controller.runJavaScript(hook),
          onPageFinished: (_) {
            controller.runJavaScript(hook);
            if (mounted) setState(() => _loading = false);
          },
        ),
      )
      ..loadRequest(Uri.parse(_loginUrl(widget.email)));
    if (!mounted) {
      if (_viewProxySet) _setViewProxy('').ignore();
      return;
    }
    setState(() => _view = controller);
  }

  void _runJs(String js) {
    _view?.runJavaScript(js);
  }

  Future<void> _gotSession(String email, String sessionToken) async {
    final api = widget.core.api;
    final accountEmail = email.isNotEmpty ? email : widget.email;
    if (api == null || accountEmail.isEmpty) {
      if (mounted) {
        setState(() => _error = AppLocalizations.of(context).signInFailed);
      }
      return;
    }
    _stopFollowing(cancel: true);
    try {
      final started = await api.startBrowserSignIn(accountEmail, sessionToken);
      if (!mounted) {
        api.cancelSignIn(started.id).ignore();
        return;
      }
      _signIn = started.id;
      _follow = Timer.periodic(
        const Duration(seconds: 1),
        (_) => _read(api, started.id),
      );
    } on CoreApiException catch (e) {
      if (mounted) {
        setState(() => _error = neutralizeProviderIdentifiers(e.message));
      }
    } on CoreUnavailable catch (e) {
      if (mounted) {
        setState(
          () => _error = describeProblem(AppLocalizations.of(context), e),
        );
      }
    }
  }

  Future<void> _read(CoreApi api, String id) async {
    if (_reading) return;
    _reading = true;
    try {
      final state = await api.signInState(id);
      if (!mounted || id != _signIn || !state.finished) return;
      _stopFollowing(cancel: false);
      final navigator = Navigator.of(context);
      navigator.pop();
      await showSignInDialog(navigator.context, widget.core, started: state);
    } on Exception {
      // Retry at the next tick if the local core is briefly unavailable.
    } finally {
      _reading = false;
    }
  }

  void _stopFollowing({required bool cancel}) {
    _follow?.cancel();
    _follow = null;
    final id = _signIn;
    _signIn = null;
    if (cancel && id != null) widget.core.api?.cancelSignIn(id).ignore();
  }

  void _useForm() {
    final core = widget.core;
    final email = widget.email;
    final navigator = Navigator.of(context);
    navigator.pop();
    showSignInDialog(navigator.context, core, email: email);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final view = _view == null ? null : wf.WebViewWidget(controller: _view!);
    return Scaffold(
      appBar: AppBar(title: Text(l.signInMethodBrowser)),
      body: _error != null
          ? _Problem(message: _error!, onUseForm: _useForm)
          : Stack(
              children: [
                if (view != null) Positioned.fill(child: view),
                if (_loading || view == null)
                  const Positioned(
                    top: 0,
                    left: 0,
                    right: 0,
                    child: LinearProgressIndicator(),
                  ),
              ],
            ),
    );
  }
}

class _Problem extends StatelessWidget {
  const _Problem({required this.message, required this.onUseForm});

  final String message;
  final VoidCallback onUseForm;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.public_off_rounded,
                size: 40,
                color: theme.colorScheme.error,
              ),
              const SizedBox(height: 12),
              Text(l.browserSignInBroken, style: theme.textTheme.titleMedium),
              const SizedBox(height: 8),
              Text(
                message,
                textAlign: TextAlign.center,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: onUseForm,
                child: Text(l.browserSignInFallback),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
