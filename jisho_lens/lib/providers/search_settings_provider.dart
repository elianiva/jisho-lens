import 'package:hive/hive.dart';
import 'package:jisho_lens/constants/box_names.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'search_settings_provider.g.dart';

// just in case if we have more settings to add in the future
class SearchSettings {
  final bool useFuzzySearch;
  SearchSettings({this.useFuzzySearch = false});
}

@riverpod
class SearchSettingsNotifier extends _$SearchSettingsNotifier {
  Box<dynamic> settingsBox = Hive.box<dynamic>(kSettingsBox);

  @override
  SearchSettings build() {
    final enableFuzzySearch = settingsBox.get("use_fuzzy_search") ?? false;
    return SearchSettings(useFuzzySearch: enableFuzzySearch);
  }

  void setFuzzySearchTo(bool isEnabled) {
    state = SearchSettings(useFuzzySearch: isEnabled);
    settingsBox.put("use_fuzzy_search", isEnabled);
  }
}
