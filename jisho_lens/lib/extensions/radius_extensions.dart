import 'package:flutter/material.dart';

extension BorderRadiusShortcut on num {
  BorderRadius get circular => BorderRadius.circular(toDouble());
}