import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jisho_lens/components/search_results.dart';
import 'package:jisho_lens/providers/jmdict_provider.dart';
import 'package:jisho_lens/providers/search_settings_provider.dart';

class SearchPage extends ConsumerStatefulWidget {
  const SearchPage({super.key});

  @override
  ConsumerState<SearchPage> createState() => SearchPageState();
}

class SearchPageState extends ConsumerState<SearchPage> {
  final _searchController = TextEditingController();
  final _searchKeyword = StateProvider<String>((ref) => "");

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final searchResult = ref.watch(JMDictNotifier.provider);

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        children: <Widget>[
          Container(
            decoration: BoxDecoration(
              borderRadius: const BorderRadius.all(Radius.circular(32)),
              color: Theme.of(context).colorScheme.primary.withOpacity(0.075),
            ),
            child: TextField(
              controller: _searchController,
              onSubmitted: (_) async {
                final keyword = _searchController.text;
                if (keyword.isEmpty) return;

                ref.read(_searchKeyword.notifier).state = keyword;

                final resourceNotifier =
                    ref.read(JMDictNotifier.provider.notifier);
                final fuzzy =
                    ref.read(SearchSettingsNotifier.provider).useFuzzySearch;

                _searchController.text = "";
                await resourceNotifier.updateResults(
                  keyword: keyword,
                  fuzzy: fuzzy,
                );
              },
              style: Theme.of(context).textTheme.bodyMedium,
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
              visible:
                  searchResult != null && searchResult.vocabularies.isNotEmpty,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    "Fetched ${searchResult?.rowsCount} rows in ${searchResult?.duration}ms.",
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  Text(
                    "${searchResult?.resultCount} results found.",
                    style: Theme.of(context).textTheme.bodySmall,
                  )
                ],
              ),
            ),
          ),
          SearchResults(
            searchResult: searchResult,
            ref: ref,
            searchKeyword: ref.watch(_searchKeyword),
          ),
        ],
      ),
    );
  }
}
