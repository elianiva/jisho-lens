import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jisho_lens/models/jmdict_result.dart';
import 'package:jisho_lens/repository/jmdict_repository.dart';

class JMDictNotifier extends StateNotifier<JMdictResult?> {
  JMDictNotifier(this.client, this.read) : super(null);

  JMDictRepository client;
  Reader read;

  static final isFetching = StateProvider.autoDispose<bool>((ref) => false);
  
  static final provider =
      StateNotifierProvider.autoDispose<JMDictNotifier, JMdictResult?>((ref) {
    final client = JMDictRepository();

    return JMDictNotifier(client, ref.read);
  });

  Future<void> updateResults({
    required String keyword,
    required bool fuzzy,
  }) async {
    read(isFetching.notifier).state = true;
    // always reset before updating the results
    state = null;
    final data = await client.findByKeyword(keyword: keyword, fuzzy: fuzzy);
    state = data;
    read(isFetching.notifier).state = false;
  }

  void reset() {
    state = null;
  }
}
