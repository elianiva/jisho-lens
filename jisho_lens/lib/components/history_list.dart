import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:intl/intl.dart';
import 'package:jisho_lens/components/history_card.dart';
import 'package:jisho_lens/constants/box_names.dart';
import 'package:jisho_lens/repository/history_repository.dart';

class HistoryList extends StatelessWidget {
  final _historyRepository = HistoryRepository();

  HistoryList({super.key});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Text(
              "RECENT SCAN",
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: Theme.of(context).colorScheme.primary,
                  ),
            ),
          ),
          Expanded(
            child: ValueListenableBuilder(
                valueListenable: Hive.box(kLensHistoryBox).listenable(),
                builder: (_, box, __) {
                  final histories = _historyRepository.getHistories();
                  return Visibility(
                    visible: histories.isNotEmpty,
                    replacement: Center(
                      child: Text(
                        "Your recent scan history can be seen here.",
                        style: Theme.of(context).textTheme.titleMedium?.copyWith(
                              color: Theme.of(context)
                                  .colorScheme
                                  .secondary
                                  .withOpacity(0.5),
                            ),
                      ),
                    ),
                    child: GridView.count(
                      primary: false,
                      padding: const EdgeInsets.all(20),
                      crossAxisCount: 2,
                      crossAxisSpacing: 20,
                      mainAxisSpacing: 20,
                      physics: const BouncingScrollPhysics(),
                      children: _historyRepository
                          .getHistories()
                          .map(
                            (h) => HistoryCard(
                              time: DateFormat.jm().format(h.createdAt),
                              date: DateFormat.yMMMEd().format(h.createdAt),
                              imagePath: h.imagePath,
                            ),
                          )
                          .toList(),
                    ),
                  );
                }),
          ),
        ],
      ),
    );
  }
}
