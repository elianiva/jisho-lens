import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jisho_lens/components/boxed_text.dart';
import 'package:jisho_lens/components/bulleted_list.dart';
import 'package:jisho_lens/constants/pos_definition.dart';
import 'package:jisho_lens/constants/priorities.dart';
import 'package:jisho_lens/extensions/context_extensions.dart';
import 'package:jisho_lens/extensions/sizedbox_extensions.dart';
import 'package:jisho_lens/models/furigana.dart';
import 'package:jisho_lens/models/sense.dart';
import 'package:jisho_lens/providers/jmdict_provider.dart';
import 'package:ruby_text/ruby_text.dart';

extension CasePolicy on String {
  String capitalise() {
    final str = this;
    if (str.length <= 1) return str;
    return "${str[0].toUpperCase()}${str.substring(1).toLowerCase()}";
  }
}

class ResultCard extends ConsumerWidget {
  const ResultCard({
    super.key,
    required this.kanji,
    required this.furigana,
    required this.senses,
    required this.priorities,
    // used to notify the keyword used in progress animation
    required this.currentSearchKeyword,
  });

  final String kanji;
  final List<Furigana> furigana;
  final List<Sense> senses;
  final List<String> priorities;
  final AutoDisposeStateProvider<String> currentSearchKeyword;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: context.theme.colorScheme.primary.withOpacity(0.05),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: priorities
                .map((p) => [
                      BoxedText(
                        text: p,
                        tooltipText: PRIORITIES_MAPPING[p] ?? p,
                        backgroundColor: context.theme.colorScheme.secondary
                            .withOpacity(0.25),
                        textStyle: context.theme.textTheme.bodySmall?.copyWith(
                          fontSize: 10,
                        ),
                      ),
                      8.horizontalBox,
                    ])
                .expand((e) => e)
                .toList(),
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              RubyText(
                furigana
                    .map(
                      (e) => RubyTextData(
                        e.ruby,
                        ruby: e.rt,
                        style: context.theme.textTheme.titleLarge?.copyWith(
                          fontSize: 24,
                        ),
                        rubyStyle: context.theme.textTheme.bodySmall,
                      ),
                    )
                    .toList(),
              )
            ],
          ),
          const Divider(height: 16),
          ...senses
              .map(
                (e) => [
                  Row(
                    children: e.partsOfSpeech
                        .map((e) {
                          return [
                            BoxedText(
                              text: e,
                              tooltipText: POS_DEFINITION_MAPPING[e] ?? e,
                              backgroundColor: context.theme.colorScheme.primary
                                  .withOpacity(0.25),
                              textStyle:
                                  context.theme.textTheme.bodySmall?.copyWith(
                                fontSize: 10,
                              ),
                            ),
                            8.horizontalBox,
                          ];
                        })
                        .expand((e) => e)
                        .toList(),
                  ),
                  BulletedList(items: e.glossaries),
                  Visibility(
                    visible: e.crossReferences.isNotEmpty,
                    child: Row(
                      children: [
                        Text(
                          "see: ",
                          style: context.theme.textTheme.bodySmall,
                        ),
                        4.horizontalBox,
                        ...e.crossReferences.map((e) {
                          return GestureDetector(
                            onTap: () {
                              // remove the numbers and change the separator to spaces
                              final keyword = e
                                  .replaceAll(r"・", " ")
                                  .replaceAll(RegExp(r"\d*"), "");
                              ref.read(currentSearchKeyword.notifier).state =
                                  keyword;
                              ref
                                  .read(jMDictNotifierProvider.notifier)
                                  .updateResults(
                                    keyword: keyword,
                                    fuzzy: false,
                                  );
                            },
                            child: Text(e,
                                style:
                                    context.theme.textTheme.bodySmall?.copyWith(
                                  decoration: TextDecoration.underline,
                                  decorationStyle: TextDecorationStyle.dashed,
                                )),
                          );
                        }).toList(),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                ],
              )
              .expand((e) => e)
              .toList(),
        ],
      ),
    );
  }
}
