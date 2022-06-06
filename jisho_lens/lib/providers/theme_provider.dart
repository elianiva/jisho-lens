import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive/hive.dart';
import 'package:jisho_lens/constants/box_names.dart';

enum PreferredTheme {
  system("Follows system"),
  light("Light"),
  dark("Dark");

  final String _humanReadable;
  const PreferredTheme(this._humanReadable);

  String get describe => _humanReadable;
}

class PreferredThemeNotifier extends StateNotifier<PreferredTheme> {
  final Box<String> themeBox;

  PreferredThemeNotifier(this.themeBox)
      : super(
          PreferredTheme.values.firstWhere(
            (p) => p.name == themeBox.get("theme"),
            orElse: () => PreferredTheme.system,
          ),
        );

  static final provider =
      StateNotifierProvider<PreferredThemeNotifier, PreferredTheme>((ref) {
    final themeBox = Hive.box<String>(kThemeBox);
    return PreferredThemeNotifier(themeBox);
  });

  void setPreferredTheme(PreferredTheme theme) {
    state = theme;
    themeBox.put("theme", theme.name);
  }
}
