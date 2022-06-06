import 'package:hive/hive.dart';
import 'package:jisho_lens/constants/box_names.dart';
import 'package:jisho_lens/models/lens_history.dart';

class HistoryRepository {
  final _historyBox = Hive.box(kLensHistoryBox);

  static const kHistoryKeyName = "history";
  static const kMaxHistoryLength = 10;

  List<History> getHistories() {
    final histories = _historyBox.get(kHistoryKeyName, defaultValue: []) ?? [];
    return histories.cast<History>()
      ..sort((History a, History b) => b.createdAt.compareTo(a.createdAt));
  }

  Future<void> addHistory(History history) async {
    List<History> histories = getHistories();

    // if it already exists then remove it because we want to update the creation date
    if (histories.indexWhere((h) => h.imagePath == history.imagePath) != -1) {
      histories.removeWhere((h) => h.imagePath == history.imagePath);
    }

    // keep limited history length
    if (histories.length >= kMaxHistoryLength) {
      histories = histories.take(kMaxHistoryLength - 1).toList();
    }

    histories.add(history);
    await _historyBox.put(kHistoryKeyName, histories);
  }

  void clearHistory() {
    _historyBox.delete(kHistoryKeyName);
  }
}
