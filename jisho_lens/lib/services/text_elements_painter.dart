import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

class TextElementsPainter extends CustomPainter {
  final List<TextLine>? textLines;
  final int previousSelectedLineIndex = -1;
  final int? selectedLineIndex;

  static const kBoxPadding = 4.0;

  TextElementsPainter({
    required this.textLines,
    required this.selectedLineIndex,
  });

  @override
  void paint(ui.Canvas canvas, ui.Size size) {
    final elements = textLines;
    if (elements == null) return;
    for (int i = 0; i < elements.length; i++) {
      final box = elements[i].boundingBox;
      final rect = Rect.fromLTRB(
        box.left - kBoxPadding,
        box.top - kBoxPadding,
        box.right + kBoxPadding,
        box.bottom + kBoxPadding,
      );
      final rrect = RRect.fromRectAndRadius(rect, const Radius.circular(8));
      final fill = Paint()
        ..style = PaintingStyle.fill
        ..color = Colors.white.withOpacity(0.25);
      final stroke = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.0
        ..color = Colors.white.withOpacity(0.4);

      canvas.drawRRect(rrect, stroke);

      if (i == selectedLineIndex) {
        canvas.drawRRect(rrect, fill);
      }
    }
  }

  @override
  bool shouldRepaint(TextElementsPainter oldDelegate) {
    return previousSelectedLineIndex != selectedLineIndex;
  }
}
