import 'package:jisho_lens/models/furigana.dart';
import 'package:jisho_lens/models/sense.dart';

class Vocabulary {
  final String kanji;
  final String reading;
  final List<Furigana> furigana;
  final List<Sense> senses;
  final List<String> priorities;

  Vocabulary({
    required this.kanji,
    required this.reading,
    required this.senses,
    required this.furigana,
    required this.priorities,
  });
}
