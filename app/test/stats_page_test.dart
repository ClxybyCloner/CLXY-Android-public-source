import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/core/core_controller.dart';
import 'package:clxy/core/models.dart';
import 'package:clxy/l10n/app_localizations.dart';
import 'package:clxy/ui/stats_page.dart';

CoreController _core({required bool withTraffic}) {
  final core = CoreController(() async => throw StateError('unused'));
  core.status = CoreStatus.fromJson({
    'state': {'phase': 'running'},
    if (withTraffic)
      'traffic': {'down': 1024, 'up': 4096, 'down_rate': 2048, 'up_rate': 512},
    if (withTraffic)
      'last_test': {
        'at': DateTime.now().toUtc().toIso8601String(),
        'total_ms': 123,
      },
  });
  return core;
}

Widget _app(CoreController core) => MaterialApp(
  locale: const Locale('fa'),
  supportedLocales: AppLocalizations.supportedLocales,
  localizationsDelegates: const [
    AppLocalizations.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  home: Scaffold(body: StatsPage(core: core)),
);

void main() {
  testWidgets('shows live traffic totals and the latest real test result', (
    tester,
  ) async {
    final core = _core(withTraffic: true);
    addTearDown(core.dispose);

    await tester.pumpWidget(_app(core));
    await tester.pumpAndSettle();

    expect(find.text('آمار ترافیک'), findsOneWidget);
    expect(find.textContaining('1.0 KB'), findsOneWidget);
    expect(find.textContaining('4.0 KB'), findsOneWidget);
    expect(find.textContaining('5.0 KB'), findsOneWidget);
    await tester.scrollUntilVisible(find.textContaining('123 ms'), 180);
    expect(find.textContaining('123 ms'), findsOneWidget);
  });

  testWidgets('does not invent traffic or a connection-test result', (
    tester,
  ) async {
    final core = _core(withTraffic: false);
    addTearDown(core.dispose);

    await tester.pumpWidget(_app(core));
    await tester.pumpAndSettle();

    expect(find.text('آمار ترافیک'), findsOneWidget);
    expect(find.textContaining('—'), findsAtLeastNWidgets(3));
    await tester.scrollUntilVisible(
      find.text('زمان پاسخ پس از اجرای تست اتصال نمایش داده می‌شود.'),
      180,
    );
    expect(
      find.text('زمان پاسخ پس از اجرای تست اتصال نمایش داده می‌شود.'),
      findsOneWidget,
    );
  });
}
