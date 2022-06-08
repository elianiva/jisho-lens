import 'dart:io';

import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:mecab_dart/mecab_dart.dart';
import 'package:jisho_lens/constants/kana_patterns.dart';

class WordExtractor {
  TextRecognizer? _textRecognizer;
  RecognizedText? _cachedResult;
  Mecab? _mecab;
  bool hasBeenInitialised = false;

  final _japaneseFullRegex = RegExp(
    kHiraganaPattern + kKatakanaPattern + kKanjiPattern + kMiscPattern,
  );

  // ignore: non_constant_identifier_names
  final JAPANESE_SMALL_KANA = RegExp(r"[ぁぃぅぇぉっょァィゥェォッョ]");

  WordExtractor() {
    _textRecognizer = TextRecognizer(script: TextRecognitionScript.japanese);
    _mecab = Mecab();
  }

  Future<void> initMecab() async {
    // no need to initialise mecab if it has already been initialised
    if (hasBeenInitialised) return;
    await _mecab?.init("assets/ipadic", true);
  }

  Future<RecognizedText?> _scanImage(String path) async {
    if (_cachedResult != null) {
      return Future.value(_cachedResult);
    }

    final file = File(path);
    final image = InputImage.fromFile(file);
    final result = await _textRecognizer?.processImage(image);
    _cachedResult = result;
    return result;
  }

  Future<List<TextBlock>?> extractAsBlocks(String path) async {
    final result = await _scanImage(path);
    return result?.blocks
        .where((b) => _japaneseFullRegex.hasMatch(b.text.trim()))
        .toList();
  }

  Future<List<TextLine>?> extractAsLines(String path) async {
    final blocks = await extractAsBlocks(path);
    if (blocks == null) return null;

    List<TextLine> lines = [];
    for (final block in blocks) {
      for (final line in block.lines) {
        lines.add(line);
      }
    }

    return lines;
  }

  List<String> splitToWords(String text) {
    final mecabResult = _mecab?.parse(text).cast<TokenNode>();
    if (mecabResult == null) return [];

    return mecabResult
        // remove non japanese words and the ones that don't have lemma
        .where(
          (e) =>
              e.features.isNotEmpty && _japaneseFullRegex.hasMatch(e.surface),
        )
        .map((e) {
          final features = e.features.cast<String>();
          final lemma = features[6];
          // if the lemma is empty, use the surface instead
          return lemma == "*" ? e.surface : lemma;
        })
        .toSet()
        .toList();
  }

  void dispose() {
    _mecab?.destroy();
    _textRecognizer?.close();
  }
}
