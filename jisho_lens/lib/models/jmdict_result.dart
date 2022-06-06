import 'package:jisho_lens/models/vocab.dart';

class JMdictResult {
  final int duration;
  final int rowsCount;
  final int resultCount;
  final List<Vocabulary> vocabularies;

  JMdictResult({
    required this.duration,
    required this.rowsCount,
    required this.vocabularies,
    required this.resultCount,
  });
}
