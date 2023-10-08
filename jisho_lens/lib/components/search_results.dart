import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jisho_lens/components/result_card.dart';
import 'package:jisho_lens/extensions/context_extensions.dart';
import 'package:jisho_lens/models/jmdict_result.dart';
import 'package:jisho_lens/providers/jmdict_provider.dart';

class SearchResults extends ConsumerWidget {
  const SearchResults({
    super.key,
    required this.searchResult,
    required this.searchKeyword,
  });

  final JMdictResult? searchResult;
  final AutoDisposeStateProvider<String> searchKeyword;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Expanded(
      // Material is used to wrap the Listview so it doesn't overflow
      // see: https://github.com/flutter/flutter/issues/86584
      child: Material(
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
        ),
        clipBehavior: Clip.antiAlias,
        color: Colors.transparent,
        child: Visibility(
          visible: searchResult != null && searchResult!.vocabularies.isNotEmpty,
          replacement: Center(
            child: Visibility(
              visible: ref.watch(jMDictNotifierProvider.notifier).isFetching,
              replacement: Text(
                "No Result",
                style: context.theme.textTheme.bodySmall,
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const CircularProgressIndicator(
                    semanticsLabel: "Fetching results",
                  ),
                  const SizedBox(height: 16),
                  Text(
                    "Searching for \"${ref.watch(searchKeyword)}\"...",
                  ),
                ],
              ),
            ),
          ),
          child: ListView.separated(
            shrinkWrap: true,
            padding: const EdgeInsets.only(
              top: 0, // remove the default top padding, not sure why it's there
            ),
            physics: const BouncingScrollPhysics(),
            separatorBuilder: (context, index) {
              return const SizedBox(height: 16);
            },
            itemBuilder: (context, index) {
              final result = searchResult!.vocabularies[index];
              return ResultCard(
                priorities: result.priorities,
                kanji: result.kanji,
                furigana: result.furigana,
                senses: result.senses,
                currentSearchKeyword: searchKeyword,
              );
            },
            itemCount: searchResult?.vocabularies.length ?? 0,
          ),
        ),
      ),
    );
  }
}
