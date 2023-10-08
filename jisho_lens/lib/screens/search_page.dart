import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jisho_lens/components/search_results.dart';
import 'package:jisho_lens/components/search_warning.dart';
import 'package:jisho_lens/constants/kana_patterns.dart';
import 'package:jisho_lens/extensions/context_extensions.dart';
import 'package:jisho_lens/providers/jmdict_provider.dart';
import 'package:jisho_lens/providers/search_settings_provider.dart';

class SearchPage extends ConsumerStatefulWidget {
  const SearchPage({super.key});

  @override
  ConsumerState<SearchPage> createState() => SearchPageState();
}

class SearchPageState extends ConsumerState<SearchPage> {
  final _searchController = TextEditingController();
  final _searchKeyword = StateProvider.autoDispose<String>((ref) => "");
  final _kanjiRegex = RegExp("[$kKanjiPattern]");

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final searchResult = ref.watch(jMDictNotifierProvider);

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        children: <Widget>[
          Container(
            decoration: BoxDecoration(
              borderRadius: const BorderRadius.all(Radius.circular(32)),
              color: context.theme.colorScheme.primary.withOpacity(0.075),
            ),
            child: TextField(
              controller: _searchController,
              onSubmitted: (_) async {
                final keyword = _searchController.text;
                if (keyword.isEmpty) return;

                ref.read(_searchKeyword.notifier).state = keyword;

                final resourceNotifier = ref.read(jMDictNotifierProvider.notifier);
                final fuzzy = ref.read(searchSettingsNotifierProvider).useFuzzySearch;

                if (!_kanjiRegex.hasMatch(keyword) && keyword.length <= 1 && fuzzy) {
                  showDialog(
                    context: context,
                    builder: (_) => const SearchWarningDialog(),
                  );
                  return;
                }

                await resourceNotifier.updateResults(
                  keyword: keyword,
                  fuzzy: fuzzy,
                );
              },
              style: context.theme.textTheme.bodyMedium,
              decoration: const InputDecoration(
                border: InputBorder.none,
                hintText: "Search for kanji, words, etc",
                prefixIcon: Icon(Icons.search),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Visibility(
              visible: searchResult != null && searchResult.vocabularies.isNotEmpty,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    "Fetched ${searchResult?.rowsCount} rows in ${searchResult?.duration}ms.",
                    style: context.theme.textTheme.bodySmall,
                  ),
                  Text(
                    "${searchResult?.resultCount} results found.",
                    style: context.theme.textTheme.bodySmall,
                  )
                ],
              ),
            ),
          ),
          SearchResults(
            searchResult: searchResult,
            searchKeyword: _searchKeyword,
          ),
        ],
      ),
    );
  }
}
