import 'package:flutter_riverpod/flutter_riverpod.dart';

enum DbStatus {
  empty,
  ready,
}

final dbStatus = StateProvider<DbStatus>((_) => DbStatus.empty);
