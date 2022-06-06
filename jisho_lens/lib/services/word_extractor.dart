import 'dart:io';

import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:mecab_dart/mecab_dart.dart';

class WordExtractor {
  TextRecognizer? _textRecognizer;
  RecognizedText? _cachedResult;
  Mecab? _mecab;
  bool hasBeenInitialised = false;

  // ignore: non_constant_identifier_names
  final JAPANESE_FULL_RE = RegExp(
    r"[\u3041-\u3096\u30A0-\u30FF\u3400-\u4DB5\u4E00-\u9FCB\uF900-\uFA6A\u31F0-\u31FF]",
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
    await _mecab?.init("assets/ipadic", false);
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
    // see: http://www.localizingjapan.com/blog/2012/01/20/regular-expressions-for-japanese-text/
    return result?.blocks
        .where((b) => JAPANESE_FULL_RE.hasMatch(b.text.trim()))
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
    final mecabResult = _mecab?.parse(text).map((e) => MecabNode(e.surface));
    if (mecabResult == null) return [];

    return mecabResult
        .map((e) => e.surface)
        // mecab's last element is always EOS so we need to remove it because it's not a word
        // we also want to remove any punctuations, non-japanese words, and make the list distinct
        .where(
          (e) =>
              e != "EOS" &&
              e != "、" &&
              e != "。" &&
              JAPANESE_FULL_RE.hasMatch(e),
        )
        .toSet()
        .toList();
  }

  void dispose() {
    _mecab?.destroy();
    _textRecognizer?.close();
  }
}

// since mecab_dart doesn't have any typing and I really don't like `dynamic` stuff
class MecabNode {
  final String surface;

  MecabNode(this.surface);
}
