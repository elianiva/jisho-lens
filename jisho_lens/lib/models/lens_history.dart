import 'package:hive/hive.dart';

part 'lens_history.g.dart';

@HiveType(typeId: 1)
class History {
  History({
    required this.createdAt,
    required this.imagePath,
  });

  @HiveField(0)
  final DateTime createdAt;

  @HiveField(1)
  final String imagePath;
}
