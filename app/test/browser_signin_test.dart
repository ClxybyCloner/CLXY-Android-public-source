import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:clxy/app_settings.dart';
import 'package:clxy/ui/browser_signin.dart';

void main() {
  group('browser sign-in selection', () {
    test('uses the browser only when selected on Android', () {
      expect(
        shouldUseBrowserSignIn(
          SignInMethod.browser,
          platform: TargetPlatform.android,
        ),
        isTrue,
      );
      expect(
        shouldUseBrowserSignIn(
          SignInMethod.api,
          platform: TargetPlatform.android,
        ),
        isFalse,
      );
      expect(
        shouldUseBrowserSignIn(
          SignInMethod.browser,
          platform: TargetPlatform.linux,
        ),
        isFalse,
      );
    });

    test(
      'loads the saved method and defaults old settings to the form',
      () async {
        final directory = await Directory.systemTemp.createTemp(
          'clxy-settings-',
        );
        try {
          final file = File('${directory.path}/app-settings.json');
          await file.writeAsString(jsonEncode({'signin_method': 'browser'}));
          final saved = await AppSettings.load(directory.path);
          expect(saved.signInMethod, SignInMethod.browser);
          saved.dispose();

          await file.writeAsString(jsonEncode({'language': 'fa'}));
          final old = await AppSettings.load(directory.path);
          expect(old.signInMethod, SignInMethod.api);
          old.dispose();
        } finally {
          await directory.delete(recursive: true);
        }
      },
    );
  });

  group('FxaWebChannel', () {
    test('answers status, validates the session token and reports it once', () {
      final replies = <String>[];
      final sessions = <MapEntry<String, String>>[];
      final channel = FxaWebChannel(
        reply: replies.add,
        onSession: (email, token) => sessions.add(MapEntry(email, token)),
      );
      final token = List.filled(32, 'ab').join();

      void send(String command, Object? data, {String id = 'frame'}) {
        channel.handle(
          jsonEncode({
            'kind': 'webchannel',
            'detail': {
              'id': id,
              'message': {
                'messageId': 'message-1',
                'command': command,
                'data': data,
              },
            },
          }),
        );
      }

      send('fxaccounts:fxa_status', null);
      expect(
        replies.single,
        contains(
          'window.__clxyReply("frame", "message-1", "fxaccounts:fxa_status"',
        ),
      );
      send('fxaccounts:can_link_account', null);
      expect(replies.last, contains('"ok":true'));

      send('fxaccounts:login', {
        'email': 'person@example.com',
        'sessionToken': 'bad-token',
      });
      expect(sessions, isEmpty);
      send('fxaccounts:login', {
        'email': 'person@example.com',
        'sessionToken': token,
      });
      send('fxaccounts:login', {
        'email': 'person@example.com',
        'sessionToken': token,
      });
      expect(sessions, hasLength(1));
      expect(sessions.single.key, 'person@example.com');
      expect(sessions.single.value, token);
    });

    test('installs a CLXY-scoped WebChannel hook', () {
      final source = FxaWebChannel.hookJs('ClxyFxa.postMessage(o);');
      expect(source, contains('window.__clxyHooked'));
      expect(source, contains('WebChannelMessageToChrome'));
      expect(source, contains('window.__clxyReply'));
      expect(source, contains('ClxyFxa.postMessage(o);'));
    });
  });
}
