import 'dart:ui';

import 'package:flutter/material.dart';

import '../app_settings.dart';
import '../core/core_controller.dart';
import '../l10n/app_localizations.dart';
import 'widgets.dart';

/// The first run: the user picks a language and a country, and the
/// country's sites go directly (the core's routing region).
class WelcomePage extends StatefulWidget {
  const WelcomePage({super.key, required this.core, required this.settings});

  final CoreController core;
  final AppSettings settings;

  @override
  State<WelcomePage> createState() => _WelcomePageState();
}

class _WelcomePageState extends State<WelcomePage> {
  late String _language;
  late String _region;

  /// The region follows the language until the user picks one.
  bool _regionPicked = false;
  bool _saving = false;

  static const _background = Color(0xFF0E1017);
  static const _panel = Color(0xFF191A22);
  static const _field = Color(0xFF11131B);
  static const _border = Color(0xFF353744);
  static const _muted = Color(0xFFB8BBC9);
  static const _blue = Color(0xFF3975D7);
  static const _violet = Color(0xFF5C5BD5);

  @override
  void initState() {
    super.initState();
    final system = PlatformDispatcher.instance.locale;
    _language = widget.settings.language.isNotEmpty
        ? widget.settings.language
        : AppSettings.languageFor(system);
    _region = AppSettings.suggestedRegion(_language, system.countryCode);
  }

  void _pickLanguage(String language) {
    setState(() {
      _language = language;
      if (!_regionPicked) {
        _region = AppSettings.regionForLanguage(language) ?? _region;
      }
    });
    // Show the page in the chosen language at once.
    widget.settings.update(language: language);
  }

  Future<void> _start() async {
    if (widget.core.status == null || _saving) return;
    setState(() => _saving = true);
    final region = _region;
    final saved = await runShowingErrors(
      context,
      () => widget.core.editSettings((config) {
        final routing = config['routing'];
        if (routing is! Map<String, Object?>) return;
        routing['enabled'] = region.isNotEmpty;
        if (region.isNotEmpty) routing['region'] = region;
      }),
    );
    if (!mounted) return;
    if (saved) {
      widget.settings.update(language: _language, firstRun: false);
    } else {
      setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final baseTheme = Theme.of(context);
    const white = Color(0xFFF5F6FA);
    const brand = Color(0xFF5B65DC);
    final scheme = baseTheme.colorScheme.copyWith(
      brightness: Brightness.dark,
      primary: brand,
      onPrimary: Colors.white,
      surface: _panel,
      onSurface: white,
      onSurfaceVariant: _muted,
      outline: _border,
      outlineVariant: _border,
    );
    final darkTheme = baseTheme.copyWith(
      brightness: Brightness.dark,
      colorScheme: scheme,
      canvasColor: _panel,
      cardColor: _panel,
      scaffoldBackgroundColor: _background,
      textTheme: baseTheme.textTheme.apply(
        bodyColor: white,
        displayColor: white,
      ),
    );

    InputDecoration selectorDecoration(String label, IconData icon) =>
        InputDecoration(
          labelText: label,
          labelStyle: const TextStyle(color: _muted),
          floatingLabelStyle: const TextStyle(color: _muted),
          prefixIcon: Icon(icon, color: _muted, size: 21),
          filled: true,
          fillColor: _field,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 17,
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(9),
            borderSide: const BorderSide(color: _border),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(9),
            borderSide: const BorderSide(color: _border),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(9),
            borderSide: const BorderSide(color: brand, width: 1.4),
          ),
        );

    return Theme(
      data: darkTheme,
      child: Scaffold(
        backgroundColor: _background,
        body: DecoratedBox(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [Color(0xFF11131C), _background],
            ),
          ),
          child: SafeArea(
            child: Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(
                  horizontal: 20,
                  vertical: 24,
                ),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 460),
                  child: Container(
                    width: double.infinity,
                    decoration: BoxDecoration(
                      color: _panel,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: _border),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.20),
                          blurRadius: 28,
                          offset: const Offset(0, 12),
                        ),
                      ],
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 24,
                      vertical: 30,
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Center(child: AppLogo(size: 78)),
                        const SizedBox(height: 18),
                        Text(
                          l.welcomeTitle,
                          textAlign: TextAlign.center,
                          style: baseTheme.textTheme.headlineSmall?.copyWith(
                            color: white,
                            fontSize: 27,
                            fontWeight: FontWeight.w700,
                            height: 1.18,
                          ),
                        ),
                        const SizedBox(height: 12),
                        Text(
                          l.welcomeBody,
                          textAlign: TextAlign.center,
                          style: baseTheme.textTheme.bodyLarge?.copyWith(
                            color: _muted,
                            fontSize: 15.5,
                            height: 1.48,
                          ),
                        ),
                        const SizedBox(height: 26),
                        DropdownButtonFormField<String>(
                          initialValue: _language,
                          isExpanded: true,
                          dropdownColor: _panel,
                          menuMaxHeight: 420,
                          style: const TextStyle(color: white, fontSize: 16),
                          decoration: selectorDecoration(
                            l.language,
                            Icons.translate_rounded,
                          ),
                          onChanged: (value) {
                            if (value != null) _pickLanguage(value);
                          },
                          items: [
                            for (final code in AppSettings.supportedLanguages)
                              DropdownMenuItem(
                                value: code,
                                child: Text(
                                  languageNames[code]!,
                                  style: languageStyle(code)
                                      ?.copyWith(color: white),
                                ),
                              ),
                          ],
                        ),
                        const SizedBox(height: 16),
                        DropdownButtonFormField<String>(
                          key: ValueKey(_region),
                          initialValue: _region,
                          isExpanded: true,
                          dropdownColor: _panel,
                          menuMaxHeight: 420,
                          style: const TextStyle(color: white, fontSize: 16),
                          decoration: selectorDecoration(
                            l.region,
                            Icons.alt_route_rounded,
                          ),
                          onChanged: (value) {
                            if (value == null) return;
                            setState(() {
                              _region = value;
                              _regionPicked = true;
                            });
                          },
                          items: [
                            for (final region in AppSettings.regions)
                              DropdownMenuItem(
                                value: region,
                                child: Text(regionName(l, region)),
                              ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Text(
                          l.routingDirectHint,
                          textAlign: TextAlign.center,
                          style: baseTheme.textTheme.bodySmall?.copyWith(
                            color: _muted,
                            fontSize: 13,
                            height: 1.4,
                          ),
                        ),
                        const SizedBox(height: 28),
                        ListenableBuilder(
                          listenable: widget.core,
                          builder: (context, _) {
                            final ready = widget.core.status != null;
                            final waiting = !ready || _saving;
                            return DecoratedBox(
                              decoration: BoxDecoration(
                                gradient: const LinearGradient(
                                  begin: Alignment.centerLeft,
                                  end: Alignment.centerRight,
                                  colors: [_blue, _violet],
                                ),
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: FilledButton(
                                style: ButtonStyle(
                                  minimumSize: const WidgetStatePropertyAll(
                                    Size.fromHeight(56),
                                  ),
                                  padding: const WidgetStatePropertyAll(
                                    EdgeInsets.symmetric(horizontal: 20),
                                  ),
                                  backgroundColor: const WidgetStatePropertyAll(
                                    Colors.transparent,
                                  ),
                                  foregroundColor: const WidgetStatePropertyAll(
                                    Colors.white,
                                  ),
                                  elevation: const WidgetStatePropertyAll(0),
                                  shape: WidgetStatePropertyAll(
                                    RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(999),
                                    ),
                                  ),
                                ),
                                onPressed: ready && !_saving ? _start : null,
                                child: Row(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    if (waiting) ...[
                                      const SizedBox(
                                        width: 17,
                                        height: 17,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                          color: Colors.white,
                                        ),
                                      ),
                                      const SizedBox(width: 10),
                                    ],
                                    Text(
                                      l.start,
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
