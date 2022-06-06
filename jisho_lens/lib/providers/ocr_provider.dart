import 'package:flutter_riverpod/flutter_riverpod.dart';

final selectedImagePath = StateProvider<String?>((_) => null);
final selectedLineIndex = StateProvider.autoDispose<int>((_) => -1);
final selectedLine = StateProvider.autoDispose<List<String>>((_) => []);
final selectedWord = StateProvider.autoDispose<String>((_) => "");
