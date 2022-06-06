import 'package:flutter/material.dart';
import 'package:jisho_lens/themes/text_theme.dart';

final _colorScheme = ColorScheme.fromSeed(
  brightness: Brightness.light,
  seedColor: const Color(0xFF0EA5E9),
);

ThemeData lightTheme = ThemeData(
  useMaterial3: true,
  colorScheme: _colorScheme,
  backgroundColor: _colorScheme.background,
  canvasColor: Colors.transparent,
  appBarTheme: AppBarTheme(
    backgroundColor: _colorScheme.background,
    toolbarHeight: 72.0,
    centerTitle: true,
  ),
  textTheme: textTheme,
  navigationBarTheme: NavigationBarThemeData(
    indicatorColor: _colorScheme.primary.withOpacity(0.25),
  ),
  dialogBackgroundColor: ElevationOverlay.colorWithOverlay(
    _colorScheme.surface,
    _colorScheme.primary,
    3.0,
  ),
);
