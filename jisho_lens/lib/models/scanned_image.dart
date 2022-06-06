import 'dart:ui' as ui;
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

class ScannedImageData {
  final ui.Image image;
  final List<TextLine>? textLines;

  ScannedImageData({
    required this.image,
    required this.textLines,
  });
}
