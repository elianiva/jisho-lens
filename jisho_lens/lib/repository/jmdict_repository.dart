import 'dart:async';
import 'dart:io';

import 'package:jisho_lens/models/furigana.dart';
import 'package:jisho_lens/models/jmdict_result.dart';
import 'package:jisho_lens/models/sense.dart';
import 'package:jisho_lens/models/vocab.dart';
import 'package:jisho_lens/services/sqlite_client.dart';

class JMDictRepository {
  Future<void> importDbFile(String? sourcePath) async {
    if (sourcePath == null) {
      throw const FileSystemException("File path is null");
    }

    final sourceFile = File(sourcePath);
    final dbPath = await SqliteClient.instance.path;
    await sourceFile.copy(dbPath);
  }

  Future<void> removeDbFile() async {
    final dbPath = await SqliteClient.instance.path;
    final dbFile = File(dbPath);
    final isDbFileExists = await dbFile.exists();
    if (!isDbFileExists) {
      throw const FileSystemException("The database file doesn't exist.");
    }

    await dbFile.delete();
  }

  static const _glossariesPredicateFuzzy = '''
  JMdictSense.Glossaries in (
    SELECT Glossaries FROM JMdictSenseFTS
    WHERE Glossaries MATCH ?
  )
  ''';

  static const _kanaPredicateFuzzy = '''
  (JMdictReading.Reading IN (
    SELECT JMdictReadingFTS.Reading FROM JMdictReadingFTS
    WHERE Reading MATCH ?
  )) OR (JMdictKanji.KanjiText IN (
    SELECT JMdictKanjiFTS.KanjiText FROM JMdictKanjiFTS
    WHERE KanjiText MATCH ?
  ))
  ''';

  static const _kanaPredicateExact = '''
  JMdictReading.Reading = ?
  OR JMdictKanji.KanjiText = ?
  ''';

  static final _latinLettersRE = RegExp(r'[a-zA-Z]');

  Future<JMdictResult?> findByKeyword({
    required String keyword,
    required bool fuzzy,
  }) async {
    final db = await SqliteClient.instance.db;
    if (db == null) return null;

    final isSearchingKana = _latinLettersRE.hasMatch(keyword) == false;

    final stopwatch = Stopwatch();
    stopwatch.start();
    final rows = await db.rawQuery('''
    SELECT
      JMdictKanji.KanjiText as kanji,
      JMdictKanji.Priorities as priorities,
      JMdictReading.ReadingId as reading_id,
      JMdictReading.ReadingOrder as reading_order,
      JMdictReading.Reading as reading,
      JMdictReading.Ruby as reading_ruby,
      JMdictReading.Rt as reading_rt,
      JMdictSense.Id as sense_id,
      JMdictSense.Glossaries as glossaries,
      JMdictSense.PartsOfSpeech as parts_of_speech,
      JMdictSense.CrossReferences as cross_references
    FROM
      JMdictEntry
    INNER JOIN JMdictSense
      ON JMdictEntry.Id = JMdictSense.EntryId
    INNER JOIN JMdictKanji
      ON JMdictEntry.Id = JMdictKanji.EntryId
    INNER JOIN JMdictReading
      ON JMdictKanji.KanjiText = JMdictReading.KanjiText
    WHERE
      (${isSearchingKana ? (fuzzy ? _kanaPredicateFuzzy : _kanaPredicateExact) : _glossariesPredicateFuzzy})
    ORDER BY
      -- Show lesser kanji first because it's more likely to be what the user is looking for
      LENGTH(JMdictKanji.KanjiText) ASC,
      -- More "priorities" means more frequent usage
      LENGTH(JmdictKanji.Priorities) DESC;
    ''', isSearchingKana ? [keyword, keyword] : [keyword]);
    stopwatch.stop();

    final vocabularies = _mapRowsToResults(rows);
    return JMdictResult(
      duration: stopwatch.elapsedMilliseconds,
      rowsCount: rows.length,
      resultCount: vocabularies.length,
      vocabularies: vocabularies,
    );
  }

  List<Vocabulary> _mapRowsToResults(List<Map<String, Object?>> queryRows) {
    final List<List<Map<String, Object?>>> groupedByReading =
        queryRows.fold([], (previousValue, current) {
      final readingId = current['reading_id'] as int;
      final reading = current['reading'] as String;
      final readingRt = current['reading_rt'] as String;

      final group = previousValue.firstWhere(
        (e) => e.first["reading_id"] == readingId,
        orElse: () => [],
      );

      // if the group is empty, create a new one
      if (previousValue.isEmpty || group.isEmpty) {
        previousValue.add([current]);
        return previousValue;
      }

      // duplicate
      if (group.any(
        (g) => g["reading"] == reading && g["reading_rt"] == readingRt,
      )) {
        return previousValue;
      }

      // add to the group
      final groupIdx = previousValue.indexOf(group);
      previousValue[groupIdx].add(current);
      return previousValue;
    });

    final results = groupedByReading.map((row) {
      // this indentation is wonky as heck
      final furigana = row
          .map((e) => Furigana(
                readingOrder: e["reading_id"] as int,
                ruby: e["reading_ruby"] as String,
                rt: e["reading_rt"] as String,
              ))
          .toList()
        ..sort((a, b) => a.readingOrder - b.readingOrder);

      final senses =
          queryRows.where((e) => e["reading_id"] == row.first["reading_id"])
              // remove duplicates
              .fold<List<Map<String, Object?>>>([], (previousValue, current) {
        if (previousValue.any((e) => e["sense_id"] == current["sense_id"])) {
          return previousValue;
        }

        previousValue.add(current);
        return previousValue;
      }).map((item) {
        final glossaries = (item["glossaries"] as String).split("|").toList();
        final partsOfSpeech =
            (item["parts_of_speech"] as String).split("|").toList();

        // we don't need to split empty cross references (e.g. "")
        List<String> crossReferences = [];
        final crossReferencesStr = (item["cross_references"] as String);
        if (crossReferencesStr.isNotEmpty) {
          crossReferences = crossReferencesStr.split("|").toList();
        }

        return Sense(
          glossaries: glossaries,
          crossReferences: crossReferences,
          partsOfSpeech: partsOfSpeech,
        );
      }).toList();

      final kanji = row.first["kanji"] as String;
      final reading = row.first["reading"] as String;

      // we don't need to split empty priorities (e.g. "")
      List<String> priorities = [];
      final prioritiesStr = row.first["priorities"] as String;
      if (prioritiesStr.isNotEmpty) {
        priorities = prioritiesStr.split(",").toList();
      }

      return Vocabulary(
        kanji: kanji,
        reading: reading,
        senses: senses,
        furigana: furigana,
        priorities: priorities,
      );
    }).toList();

    return results;
  }
}
