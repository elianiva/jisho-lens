import 'package:jisho_lens/repository/jmdict_repository.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'jmdict_repository_provider.g.dart';

@riverpod
JMDictRepository jmDictRepository(JmDictRepositoryRef ref) {
  return JMDictRepository();
}
