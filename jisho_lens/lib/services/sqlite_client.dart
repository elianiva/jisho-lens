import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

class SqliteClient {
  SqliteClient._();

  static String? _path;
  static final instance = SqliteClient._();
  static Database? _dbInstance;

  Future<Database>? get db async => _dbInstance ?? await _getInstance();
  Future<String> get path async => _path ?? await _getPath();

  Future<String> _getPath() async {
    final dbPath = await getDatabasesPath();
    final path = p.join(dbPath, "jisho_lens.db");
    return path;
  }

  Future<Database> _getInstance() async {
    final dbPath = await getDatabasesPath();
    final path = p.join(dbPath, "jisho_lens.db");
    return openDatabase(path);
  }

  Future<void> close() async {
    await _dbInstance?.close();
  }
}
