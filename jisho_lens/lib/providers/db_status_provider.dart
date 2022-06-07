import 'package:flutter_riverpod/flutter_riverpod.dart';

enum DbStatus {
  unknown,
  empty,
  ready,
}

final dbStatus = StateProvider<DbStatus>((_) => DbStatus.unknown);
