import 'package:clxy/ui/app_zoom.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

class _RetainedChild extends StatefulWidget {
  const _RetainedChild({required this.onBuild, super.key});

  final VoidCallback onBuild;

  @override
  State<_RetainedChild> createState() => _RetainedChildState();
}

class _RetainedChildState extends State<_RetainedChild> {
  @override
  Widget build(BuildContext context) {
    widget.onBuild();
    return const SizedBox(height: 20);
  }
}

void main() {
  Future<void> pumpZoom(
    WidgetTester tester, {
    Size size = const Size(1000, 800),
    required Widget child,
  }) async {
    await tester.binding.setSurfaceSize(size);
    await tester.pumpWidget(MaterialApp(home: AppZoom(child: child)));
    await tester.pump();
  }

  Future<void> ctrlKey(
    WidgetTester tester,
    LogicalKeyboardKey key, {
    bool shift = false,
  }) async {
    if (shift) await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    if (key == LogicalKeyboardKey.add || key == LogicalKeyboardKey.numpadAdd) {
      final physicalKey = PhysicalKeyboardKey.numpadAdd;
      HardwareKeyboard.instance.handleKeyEvent(
        KeyDownEvent(
          physicalKey: physicalKey,
          logicalKey: key,
          timeStamp: Duration.zero,
        ),
      );
      HardwareKeyboard.instance.handleKeyEvent(
        KeyUpEvent(
          physicalKey: physicalKey,
          logicalKey: key,
          timeStamp: Duration.zero,
        ),
      );
    } else {
      await tester.sendKeyDownEvent(key);
      await tester.sendKeyUpEvent(key);
    }
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    if (shift) await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    await tester.pump();
  }

  testWidgets('Ctrl plus and minus zoom in ten percent steps', (tester) async {
    final scales = <double>[];
    await pumpZoom(
      tester,
      child: Builder(
        builder: (context) {
          scales.add(AppZoom.scaleOf(context));
          return const SizedBox.expand();
        },
      ),
    );

    expect(scales.last, 1.0);
    await ctrlKey(tester, LogicalKeyboardKey.equal);
    expect(scales.last, 1.1);
    await ctrlKey(tester, LogicalKeyboardKey.minus);
    expect(scales.last, 1.0);
    await ctrlKey(tester, LogicalKeyboardKey.equal);
    expect(scales.last, 1.1);
    await ctrlKey(tester, LogicalKeyboardKey.numpadSubtract);
    expect(scales.last, 1.0);
  });

  testWidgets('shifted equal, add, and keypad add are accepted and clamp', (
    tester,
  ) async {
    await pumpZoom(tester, child: const SizedBox.expand());

    for (var i = 0; i < 4; i++) {
      await ctrlKey(tester, LogicalKeyboardKey.equal, shift: true);
    }
    for (var i = 0; i < 4; i++) {
      await ctrlKey(tester, LogicalKeyboardKey.add);
    }
    for (var i = 0; i < 4; i++) {
      await ctrlKey(tester, LogicalKeyboardKey.numpadAdd);
    }
    expect(AppZoom.scaleOf(tester.element(find.byType(SizedBox).last)), 1.5);

    await ctrlKey(tester, LogicalKeyboardKey.equal, shift: true);
    expect(AppZoom.scaleOf(tester.element(find.byType(SizedBox).last)), 1.5);

    for (var i = 0; i < 8; i++) {
      await ctrlKey(tester, LogicalKeyboardKey.numpadSubtract);
    }
    for (var i = 0; i < 8; i++) {
      await ctrlKey(tester, LogicalKeyboardKey.minus);
    }
    expect(AppZoom.scaleOf(tester.element(find.byType(SizedBox).last)), 0.8);
    await ctrlKey(tester, LogicalKeyboardKey.numpadSubtract);
    expect(AppZoom.scaleOf(tester.element(find.byType(SizedBox).last)), 0.8);
  });

  testWidgets('logical viewport and actual child layout reflow at zoom', (
    tester,
  ) async {
    final childKey = GlobalKey();
    final mediaSizes = <Size>[];
    await pumpZoom(
      tester,
      child: Builder(
        builder: (context) => SizedBox(
          key: childKey,
          width: MediaQuery.sizeOf(context).width,
          height: MediaQuery.sizeOf(context).height,
          child: Builder(
            builder: (context) {
              mediaSizes.add(MediaQuery.sizeOf(context));
              return const SizedBox.expand();
            },
          ),
        ),
      ),
    );
    await ctrlKey(tester, LogicalKeyboardKey.equal);

    const logicalSize = Size(1000 / 1.1, 800 / 1.1);
    expect(mediaSizes.last, logicalSize);
    expect(MediaQuery.sizeOf(childKey.currentContext!), logicalSize);
    expect(tester.getSize(find.byKey(childKey)), logicalSize);

    final viewport = tester.renderObject<RenderBox>(find.byType(ClipRect));
    expect(viewport.size, const Size(1000, 800));
    final child = tester.renderObject<RenderBox>(find.byKey(childKey));
    final topLeft = child.localToGlobal(Offset.zero);
    final bottomRight = child.localToGlobal(
      Offset(logicalSize.width, logicalSize.height),
    );
    expect(topLeft, Offset.zero);
    expect(bottomRight, const Offset(1000, 800));

    await ctrlKey(tester, LogicalKeyboardKey.minus);
    await ctrlKey(tester, LogicalKeyboardKey.minus);
    const zoomedOutLogicalSize = Size(1000 / 0.9, 800 / 0.9);
    expect(tester.getSize(find.byKey(childKey)), zoomedOutLogicalSize);
    final zoomedOutChild = tester.renderObject<RenderBox>(find.byKey(childKey));
    expect(
      zoomedOutChild.localToGlobal(
        Offset(zoomedOutLogicalSize.width, zoomedOutLogicalSize.height),
      ),
      const Offset(1000, 800),
    );
  });

  testWidgets('zoom preserves child state and exact text focus node', (
    tester,
  ) async {
    final childKey = GlobalKey<_RetainedChildState>();
    final focusNode = FocusNode();
    var buildCount = 0;
    await pumpZoom(
      tester,
      child: Material(
        child: Column(
          children: [
            _RetainedChild(key: childKey, onBuild: () => buildCount++),
            TextField(focusNode: focusNode),
          ],
        ),
      ),
    );

    final originalState = childKey.currentState;
    await tester.tap(find.byType(TextField));
    expect(FocusManager.instance.primaryFocus, focusNode);
    final originalFocus = FocusManager.instance.primaryFocus;
    final originalBuildCount = buildCount;

    await ctrlKey(tester, LogicalKeyboardKey.equal);
    expect(childKey.currentState, same(originalState));
    expect(FocusManager.instance.primaryFocus, same(originalFocus));
    expect(FocusManager.instance.primaryFocus, same(focusNode));

    await ctrlKey(tester, LogicalKeyboardKey.minus);
    expect(childKey.currentState, same(originalState));
    expect(FocusManager.instance.primaryFocus, same(focusNode));
    expect(buildCount, originalBuildCount);

    focusNode.dispose();
  });
}
