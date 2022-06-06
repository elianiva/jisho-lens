import 'dart:ui' as ui;

import 'package:flutter/material.dart';

class ScannedImagePainter extends CustomPainter {
  final ui.Image image;

  static const kBoxPadding = 4.0;

  ScannedImagePainter({
    required this.image,
  });

  @override
  void paint(ui.Canvas canvas, ui.Size size) {
    // dim the original image
    final paint = Paint()
      ..colorFilter =
          ColorFilter.mode(Colors.black.withOpacity(0.25), BlendMode.darken);
    canvas.drawImage(image, Offset.zero, paint);
  }

  @override
  bool shouldRepaint(ScannedImagePainter oldDelegate) => false;
}
