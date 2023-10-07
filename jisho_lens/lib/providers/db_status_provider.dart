import 'package:flutter_riverpod/flutter_riverpod.dart';

enum DbStatus {
  unknown,
  empty,
  ready;

  static DbStatus fromBoolean(bool fileExists) => fileExists ? ready : empty;
}

final dbStatus = StateProvider<DbStatus>((_) => DbStatus.unknown);
