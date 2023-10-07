import 'package:jisho_lens/models/jmdict_result.dart';
import 'package:jisho_lens/providers/jmdict_repository_provider.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'jmdict_provider.g.dart';

@riverpod
class JMDictNotifier extends _$JMDictNotifier {
  JMDictNotifier();

  bool isFetching = false;

  @override
  JMdictResult? build() {
    return null;
  }

  Future<void> updateResults({
    required String keyword,
    required bool fuzzy,
  }) async {
    isFetching = true;
    // always reset before updating the results
    state = null;
    state = await ref
        .watch(jmDictRepositoryProvider)
        .findByKeyword(keyword: keyword, fuzzy: fuzzy);
    isFetching = false;
  }

  void reset() {
    state = null;
  }
}
