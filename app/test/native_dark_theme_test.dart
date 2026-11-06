import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Android native windows are dark in default and night resources', () {
    for (final variant in ['values', 'values-night']) {
      final styles = File('android/app/src/main/res/$variant/styles.xml')
          .readAsStringSync();
      expect(styles, isNot(contains('Theme.Light')));
      expect(styles, contains('Theme.Black.NoTitleBar'));
      expect(styles, contains('@color/clxy_window_background'));
      expect(
        styles,
        contains('<item name="android:windowLightStatusBar">false</item>'),
      );
      expect(
        styles,
        contains('<item name="android:windowLightNavigationBar">false</item>'),
      );
    }
    final colors = File('android/app/src/main/res/values/colors.xml')
        .readAsStringSync();
    expect(colors, contains('name="clxy_window_background">#111218</color>'));
  });

  test('Android splash has no white or system-derived background', () {
    for (final variant in ['drawable', 'drawable-v21']) {
      final splash = File(
        'android/app/src/main/res/$variant/launch_background.xml',
      ).readAsStringSync();
      expect(splash, contains('@color/clxy_window_background'));
      expect(splash, isNot(contains('@android:color/white')));
      expect(splash, isNot(contains('?android:colorBackground')));
    }
  });
}
