import 'package:hive/hive.dart';
import 'package:jisho_lens/constants/box_names.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'theme_provider.g.dart';

enum PreferredTheme {
  system("Follows system"),
  light("Light"),
  dark("Dark");

  final String _humanReadable;
  const PreferredTheme(this._humanReadable);

  String get describe => _humanReadable;
}

@riverpod
class PreferredThemeNotifier extends _$PreferredThemeNotifier {
  final Box<String> themeBox = Hive.box<String>(kThemeBox);

  @override
  PreferredTheme build() {
    return PreferredTheme.values.firstWhere(
      (p) => p.name == themeBox.get("theme"),
      orElse: () => PreferredTheme.system,
    );
  }

  void setPreferredTheme(PreferredTheme theme) {
    state = theme;
    themeBox.put("theme", theme.name);
  }
}
