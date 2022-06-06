import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive/hive.dart';
import 'package:jisho_lens/constants/box_names.dart';

// just in case if we have more settings to add in the future
class SearchSettings {
  final bool useFuzzySearch;

  SearchSettings({this.useFuzzySearch = false});
}

class SearchSettingsNotifier extends StateNotifier<SearchSettings> {
  final Box<dynamic> settingsBox;

  SearchSettingsNotifier(this.settingsBox)
      : super(SearchSettings(
          useFuzzySearch: settingsBox.get("enable_fuzzy_search") ?? false,
        ));

  static final provider =
      StateNotifierProvider<SearchSettingsNotifier, SearchSettings>((ref) {
    final settingsBox = Hive.box<dynamic>(kSettingsBox);

    return SearchSettingsNotifier(settingsBox);
  });

  void setFuzzySearchTo(bool isEnabled) {
    state = SearchSettings(useFuzzySearch: isEnabled);
    settingsBox.put("enable_fuzzy_search", isEnabled);
  }
}
