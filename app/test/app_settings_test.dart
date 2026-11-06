import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/app_settings.dart';
import 'package:clxy/core/core_process.dart';

void main() {
  late Directory dir;

  setUp(() => dir = Directory.systemTemp.createTempSync('app_settings_'));
  tearDown(() => dir.deleteSync(recursive: true));

  test('defaults without a file', () async {
    final s = await AppSettings.load(dir.path);
    expect(s.language, '');
    expect(s.locale, isNull);
    expect(s.closeAction, CloseAction.ask);
    expect(s.firstRun, isTrue);
    // Starting with Windows is on until the user turns it off.
    expect(s.autostart, isTrue);
    // Connecting by itself is the user's choice.
    expect(s.autoConnect, isFalse);
  });

  test('saves and loads the choices without a theme preference', () async {
    final s = await AppSettings.load(dir.path);
    s.update(
      language: 'fa',
      closeAction: CloseAction.tray,
      firstRun: false,
      autostart: false,
      autoConnect: true,
      updateNotifiedVersion: '1.0.11',
    );
    // update saves in the background.
    await Future<void>.delayed(const Duration(milliseconds: 200));
    final again = await AppSettings.load(dir.path);
    expect(again.locale, const Locale('fa'));
    expect(again.closeAction, CloseAction.tray);
    expect(again.firstRun, isFalse);
    expect(again.autostart, isFalse);
    expect(again.autoConnect, isTrue);
    expect(again.updateNotifiedVersion, '1.0.11');
    final saved = jsonDecode(
      File('${dir.path}/app-settings.json').readAsStringSync(),
    ) as Map<String, Object?>;
    expect(saved, isNot(contains('theme')));
  });

  for (final legacyTheme in ['light', 'system', 'dark']) {
    test('purges legacy $legacyTheme preference on load', () async {
      final file = File('${dir.path}/app-settings.json')
        ..writeAsStringSync(
          jsonEncode({
            'language': 'fa',
            'theme': legacyTheme,
            'close': 'tray',
            'welcome_done': true,
          }),
        );
      final s = await AppSettings.load(dir.path);
      expect(s.locale, const Locale('fa'));
      expect(s.closeAction, CloseAction.tray);
      expect(s.firstRun, isFalse);
      final migrated =
          jsonDecode(file.readAsStringSync()) as Map<String, Object?>;
      expect(migrated, isNot(contains('theme')));
      expect(migrated['language'], 'fa');
      expect(migrated['close'], 'tray');
    });
  }

  test('maps languages to routing regions', () {
    expect(AppSettings.regionForLanguage('fa'), 'ir');
    expect(AppSettings.regionForLanguage('zh'), 'cn');
    expect(AppSettings.regionForLanguage('ru'), 'ru');
    expect(AppSettings.regionForLanguage('en'), isNull);
    expect(AppSettings.suggestedRegion('en', 'TR'), 'tr');
    expect(AppSettings.suggestedRegion('en', 'US'), '');
    expect(AppSettings.suggestedRegion('fa', 'DE'), 'ir');
  });

  test('ignores unknown values', () async {
    File('${dir.path}/app-settings.json')
        .writeAsStringSync('{"language": "xx", "theme": "neon", "close": 7}');
    final s = await AppSettings.load(dir.path);
    expect(s.language, '');
    expect(s.closeAction, CloseAction.ask);
  });

  test('takes the core executable from --core', () {
    expect(
      CoreProcess.executableFrom(['--core=C:/bin/core.exe']),
      'C:/bin/core.exe',
    );
    expect(
      CoreProcess.executableFrom(const []),
      endsWith(
        Platform.isWindows ? r'core\clxy-core.exe' : 'core/firefox-vpn-core',
      ),
    );
  });
}
