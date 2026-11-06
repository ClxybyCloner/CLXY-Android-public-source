import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/app_settings.dart';
import 'package:clxy/core/core_api.dart';
import 'package:clxy/core/core_controller.dart';
import 'package:clxy/l10n/app_localizations.dart';
import 'package:clxy/ui/proxy_profile_page.dart';
import 'package:clxy/ui/settings_page.dart';

class _UnusedCoreApi implements CoreApi {
  @override
  CoreEndpoint get endpoint =>
      CoreEndpoint(Uri.parse('http://127.0.0.1:9'), 'unused');

  @override
  void close() {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Widget _app(
  CoreController core,
  AppSettings settings, {
  required String locale,
}) => MaterialApp(
  locale: Locale(locale),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: ProxyProfilePage(core: core, settings: settings)),
);

void main() {
  testWidgets('shows unavailable message without connecting to the core', (
    tester,
  ) async {
    var connectCalls = 0;
    final core = CoreController(() async {
      connectCalls++;
      return _UnusedCoreApi();
    });
    final settings = AppSettings();

    await tester.pumpWidget(_app(core, settings, locale: 'en'));
    await tester.pump();

    expect(
      find.text('Hotspot sharing isn’t available in this test build.'),
      findsOneWidget,
    );
    expect(find.byType(Switch), findsNothing);
    expect(connectCalls, 0);

    await tester.pumpWidget(const SizedBox.shrink());
    core.dispose();
  });

  testWidgets('shows the Persian unavailable message', (tester) async {
    final core = CoreController(() async => _UnusedCoreApi());
    final settings = AppSettings();

    await tester.pumpWidget(_app(core, settings, locale: 'fa'));
    await tester.pump();

    expect(
      find.text('اشتراک‌گذاری هات‌اسپات در این نسخهٔ آزمایشی در دسترس نیست.'),
      findsOneWidget,
    );
    expect(find.byType(Switch), findsNothing);

    await tester.pumpWidget(const SizedBox.shrink());
    core.dispose();
  });

  test('Android LAN sharing requires both saved proxy credentials', () {
    expect(
      canEnableProxyLanSharing(isAndroid: true, username: '', password: ''),
      isFalse,
    );
    expect(
      canEnableProxyLanSharing(isAndroid: true, username: 'user', password: ''),
      isFalse,
    );
    expect(
      canEnableProxyLanSharing(
        isAndroid: true,
        username: ' user ',
        password: 'pass',
      ),
      isTrue,
    );
    expect(
      canEnableProxyLanSharing(isAndroid: false, username: '', password: ''),
      isTrue,
    );
  });

  test('Android LAN sharing cannot be left without either credential', () {
    expect(
      canSaveProxyLogin(
        isAndroid: true,
        lanSharing: true,
        username: 'user',
        password: '',
      ),
      isFalse,
    );
    expect(
      canSaveProxyLogin(
        isAndroid: true,
        lanSharing: true,
        username: 'user',
        password: 'pass',
      ),
      isTrue,
    );
    expect(
      canSaveProxyLogin(
        isAndroid: true,
        lanSharing: false,
        username: '',
        password: '',
      ),
      isTrue,
    );
    expect(
      canSaveProxyLogin(
        isAndroid: false,
        lanSharing: true,
        username: '',
        password: '',
      ),
      isTrue,
    );
  });
}
