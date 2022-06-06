import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

final textTheme = TextTheme(
  headlineLarge: GoogleFonts.raleway(
    textStyle: const TextStyle(
      fontWeight: FontWeight.w600,
      fontSize: 24,
    ),
  ),
  titleMedium: GoogleFonts.inter(
    textStyle: const TextStyle(
      fontWeight: FontWeight.w400,
      fontSize: 14,
    ),
  ),
  bodyLarge: GoogleFonts.inter(
    textStyle: const TextStyle(
      fontWeight: FontWeight.w400,
      fontSize: 16,
    ),
  ),
  bodyMedium: GoogleFonts.inter(
    textStyle: const TextStyle(
      fontWeight: FontWeight.w400,
      fontSize: 14,
      height: 1.75,
    ),
  ),
  labelLarge: GoogleFonts.inter(
    textStyle: const TextStyle(
      fontWeight: FontWeight.w400,
      fontSize: 14,
    ),
  ),
  labelMedium: GoogleFonts.inter(
    textStyle: const TextStyle(
      fontWeight: FontWeight.w400,
      fontSize: 12,
    ),
  ),
);
