import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Zooms the complete application surface while retaining a logical viewport.
///
/// Wrap this around the root app child (normally in MaterialApp.builder). The
/// zoom is deliberately session-local and is not persisted.
class AppZoom extends StatefulWidget {
  const AppZoom({required this.child, super.key});

  final Widget child;

  static double scaleOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<_AppZoomScope>()?.scale ??
        1.0;
  }

  @override
  State<AppZoom> createState() => _AppZoomState();
}

class _AppZoomState extends State<AppZoom> {
  static const int _minPercent = 80;
  static const int _maxPercent = 150;
  static const int _stepPercent = 10;

  int _percent = 100;

  @override
  void initState() {
    super.initState();
    HardwareKeyboard.instance.addHandler(_handleKeyEvent);
  }

  @override
  void dispose() {
    HardwareKeyboard.instance.removeHandler(_handleKeyEvent);
    super.dispose();
  }

  bool _handleKeyEvent(KeyEvent event) {
    if (event is! KeyDownEvent || !HardwareKeyboard.instance.isControlPressed) {
      return false;
    }

    final key = event.logicalKey;
    final zoomIn =
        key == LogicalKeyboardKey.equal ||
        key == LogicalKeyboardKey.add ||
        key == LogicalKeyboardKey.numpadAdd;
    final zoomOut =
        key == LogicalKeyboardKey.minus ||
        key == LogicalKeyboardKey.numpadSubtract;
    if (!zoomIn && !zoomOut) return false;

    final next = (_percent + (zoomIn ? _stepPercent : -_stepPercent)).clamp(
      _minPercent,
      _maxPercent,
    );
    if (next != _percent && mounted) {
      setState(() => _percent = next);
    }
    // Consume the shortcut, but leave focus and text editing untouched.
    return true;
  }

  @override
  Widget build(BuildContext context) {
    final scale = _percent / 100.0;
    return _AppZoomScope(
      scale: scale,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.hasBoundedWidth
              ? constraints.maxWidth
              : 0.0;
          final height = constraints.hasBoundedHeight
              ? constraints.maxHeight
              : 0.0;
          final logicalSize = Size(width / scale, height / scale);
          final mediaQuery = MediaQuery.maybeOf(context);
          final adjustedMediaQuery = mediaQuery?.copyWith(
            size: logicalSize,
            padding: mediaQuery.padding / scale,
            viewPadding: mediaQuery.viewPadding / scale,
            viewInsets: mediaQuery.viewInsets / scale,
            systemGestureInsets: mediaQuery.systemGestureInsets / scale,
          );

          Widget content = SizedBox(
            width: logicalSize.width,
            height: logicalSize.height,
            child: widget.child,
          );
          if (adjustedMediaQuery != null) {
            content = MediaQuery(data: adjustedMediaQuery, child: content);
          }

          // Keep this wrapper hierarchy identical at every zoom level. The
          // OverflowBox gives the transformed child logical constraints rather
          // than the physical viewport's tight constraints.
          return ClipRect(
            child: OverflowBox(
              alignment: Alignment.topLeft,
              minWidth: logicalSize.width,
              maxWidth: logicalSize.width,
              minHeight: logicalSize.height,
              maxHeight: logicalSize.height,
              child: Transform.scale(
                alignment: Alignment.topLeft,
                scale: scale,
                child: content,
              ),
            ),
          );
        },
      ),
    );
  }
}

class _AppZoomScope extends InheritedWidget {
  const _AppZoomScope({required this.scale, required super.child});

  final double scale;

  @override
  bool updateShouldNotify(_AppZoomScope oldWidget) => scale != oldWidget.scale;
}
