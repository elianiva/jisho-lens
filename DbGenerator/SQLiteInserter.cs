using System;
using System.Collections.Generic;
using System.Data.Common;
using System.IO;
using System.Linq;
using Business.FuriganaDomain;
using Business.JmdictDomain;
using Microsoft.Data.Sqlite;

public class SQLiteInserter
{
    private const string JMDICT_ENTRY = "JMdictEntry";
    private const string JMDICT_KANJI = "JMdictKanji";
    private const string JMDICT_READING = "JMdictReading";
    private const string JMDICT_SENSE = "JMdictSense";
    private const string JMDICT_FURIGANA = "JMdictFurigana";
    private const string JMDICT_SENSE_FTS = "JMdictSenseFTS";
    private const string JMDICT_READING_FTS = "JMdictReadingFTS";
    private const string JMDICT_KANJI_FTS = "JMdictKanjiFTS";

    private readonly SqliteConnection _connection;

    public SQLiteInserter(string dbFilePath)
    {
        if (string.IsNullOrEmpty(dbFilePath))
        {
            throw new ArgumentException("Path to the database file can't be empty!", nameof(dbFilePath));
        }

        _connection = new SqliteConnection($"Data Source={dbFilePath}");
    }

    private void RemoveDatabaseFile()
    {
        var fileExists = File.Exists(_connection.DataSource);
        if (fileExists)
        {
            File.Delete(_connection.DataSource);
        }
    }

    private void PrepareTables()
    {
        using var transaction = _connection.BeginTransaction();
        try
        {
            var command = _connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = $@"
            CREATE TABLE IF NOT EXISTS {JMDICT_ENTRY} (
                Id INTEGER PRIMARY KEY,
                EntrySequence INTEGER
            );

            CREATE TABLE IF NOT EXISTS {JMDICT_KANJI} (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                EntryId INTEGER,
                KanjiText TEXT,
                Priorities TEXT,
                FOREIGN KEY (EntryId) REFERENCES {JMDICT_ENTRY}(Id) DEFERRABLE INITIALLY DEFERRED
            );

            CREATE TABLE IF NOT EXISTS {JMDICT_SENSE} (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                EntryId INTEGER,
                Glossaries TEXT,
                PartsOfSpeech TEXT,
                CrossReferences TEXT,
                FOREIGN KEY (EntryId) REFERENCES {JMDICT_ENTRY}(Id) DEFERRABLE INITIALLY DEFERRED
            );

            CREATE TABLE IF NOT EXISTS {JMDICT_READING} (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                ReadingId INTEGER,
                ReadingOrder INTEGER,
                KanjiText TEXT,
                Reading TEXT,
                Ruby TEXT,
                Rt TEXT
            );

            CREATE VIRTUAL TABLE IF NOT EXISTS {JMDICT_SENSE_FTS}
            USING fts4(
                Glossaries,
                content='{JMDICT_SENSE}'
            );
            CREATE TRIGGER {JMDICT_SENSE_FTS} AFTER INSERT ON {JMDICT_SENSE}
            BEGIN
                INSERT INTO {JMDICT_SENSE_FTS} (rowid, Glossaries)
                VALUES (new.rowid, new.Glossaries);
            END;

            CREATE VIRTUAL TABLE IF NOT EXISTS {JMDICT_READING_FTS}
            USING fts4(
                Reading,
                content='{JMDICT_READING}'
            );
            CREATE TRIGGER {JMDICT_READING_FTS} AFTER INSERT ON {JMDICT_READING}
            BEGIN
                INSERT INTO {JMDICT_READING_FTS} (rowid, Reading)
                VALUES (new.rowid, new.Reading);
            END;

            CREATE VIRTUAL TABLE IF NOT EXISTS {JMDICT_KANJI_FTS}
            USING fts4(
                KanjiText,
                content='{JMDICT_KANJI}'
            );
            CREATE TRIGGER {JMDICT_KANJI_FTS} AFTER INSERT ON {JMDICT_KANJI}
            BEGIN
                INSERT INTO {JMDICT_KANJI_FTS} (rowid, KanjiText)
                VALUES (new.rowid, new.KanjiText);
            END;
            ";

            command.ExecuteNonQuery();
            transaction.Commit();
            Console.WriteLine("Tables created successfully.");
        }
        catch (DbException ex)
        {
            Console.WriteLine("Failed to create tables. Reason: {0}", ex.Message);
            transaction.Rollback();
            System.Environment.Exit(1);
        }
    }

    private void AddTableIndex()
    {
        using var transaction = _connection.BeginTransaction();
        try
        {
            var command = _connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = $@"
            CREATE INDEX idx_JMdictKanji_KanjiText ON JMdictKanji(KanjiText);
            CREATE INDEX idx_JMdictReading_KanjiText ON JMdictReading(KanjiText);
            CREATE INDEX idx_JMdictSense_Glossaries ON JMdictSense(Glossaries);
            ";

            command.ExecuteNonQuery();
            transaction.Commit();
            Console.WriteLine("Index created successfully.");
        }
        catch (DbException ex)
        {
            Console.WriteLine("Failed to create index. Reason: {0}", ex.Message);
            transaction.Rollback();
        }
    }

    public int Insert(IEnumerable<JmdictEntry> jmdictEntries, IEnumerable<FuriganaEntry> furiganaEntries)
    {
        if (furiganaEntries is null) throw new Exception("Furigana entries are null!");
        if (jmdictEntries is null) throw new Exception("JMdict entries are null!");

        Console.WriteLine("Removing old database file");
        RemoveDatabaseFile();

        _connection.Open();

        Console.WriteLine("Creating tables...");
        PrepareTables();

        // turn off synchronous mode and set journal_mode to memory to speed up inserts
        var _command = _connection.CreateCommand();
        _command.CommandText = "PRAGMA synchronous = OFF; PRAGMA journal_mode = MEMORY;";
        _command.ExecuteNonQuery();

        Console.WriteLine("Inserting entries...");
        // count inserted entries
        var insertedRowCount = 0;

        #region Furigana Entries
        using var furiganaTrx = _connection.BeginTransaction(deferred: true);
        try
        {
            Console.WriteLine("Inserting chunk of {0} furigana entries...", furiganaEntries.Count());
            foreach (var furiganaEntry in furiganaEntries)
            {
                var readingCmd = _connection.CreateCommand();
                readingCmd.Transaction = furiganaTrx;
                var readingValuesTemplate = string.Join(",", furiganaEntry.Furigana.Select((_, i) => $"(@readingId{i}, @readingOrder{i}, @kanjiText{i}, @reading{i}, @ruby{i}, @rt{i})"));
                readingCmd.CommandText = $@"
                INSERT INTO {JMDICT_READING} (ReadingId, ReadingOrder, KanjiText, Reading, Ruby, Rt)
                VALUES {readingValuesTemplate};
                ";
                var readingIdx = 0;
                foreach (var furigana in furiganaEntry.Furigana)
                {
                    readingCmd.Parameters.Add(new SqliteParameter("@readingId" + readingIdx, furiganaEntry.Id));
                    readingCmd.Parameters.Add(new SqliteParameter("@readingOrder" + readingIdx, readingIdx));
                    readingCmd.Parameters.Add(new SqliteParameter("@kanjiText" + readingIdx, furiganaEntry.KanjiText));
                    readingCmd.Parameters.Add(new SqliteParameter("@reading" + readingIdx, furiganaEntry.Reading));
                    readingCmd.Parameters.Add(new SqliteParameter("@ruby" + readingIdx, furigana.Ruby ?? ""));
                    readingCmd.Parameters.Add(new SqliteParameter("@rt" + readingIdx, furigana.Rt ?? ""));
                    readingIdx++;
                    insertedRowCount++;
                }
                readingCmd.ExecuteNonQuery();
                insertedRowCount++;
            }
            furiganaTrx.Commit();
        }
        catch (DbException ex)
        {
            Console.WriteLine(ex);
            furiganaTrx.Rollback();
        }
        #endregion

        // insert them in chunks so I can see the progress
        #region JMdict Entries
        // track the sense id manually because we don't want to get its id
        // from the database otherwise it'll be too slow
        foreach (var chunk in jmdictEntries.Chunk(50_000))
        {
            Console.WriteLine("Inserting chunk of {0} JMdict entries...", chunk.Count());
            using var jmdictTrx = _connection.BeginTransaction(deferred: true);
            try
            {
                foreach (var entry in chunk)
                {
                    var jmdictEntryCmd = _connection.CreateCommand();
                    jmdictEntryCmd.CommandText = $@"
                    INSERT INTO {JMDICT_ENTRY} (Id, EntrySequence)
                    VALUES (@id, @entrySequence);
                    ";
                    jmdictEntryCmd.Transaction = jmdictTrx;
                    jmdictEntryCmd.Parameters.Clear();
                    jmdictEntryCmd.Parameters.Add(new SqliteParameter("@id", entry.Id));
                    jmdictEntryCmd.Parameters.Add(new SqliteParameter("@entrySequence", entry.EntrySequence));
                    jmdictEntryCmd.ExecuteNonQuery();

                    #region Kanji
                    // insert kanji and reading elements in bulk
                    var kanjiCmd = _connection.CreateCommand();
                    kanjiCmd.CommandText = $@"
                    INSERT INTO {JMDICT_KANJI} (EntryId, KanjiText, Priorities)
                    VALUES (@entryId, @kanjiText, @priorities);
                    ";
                    kanjiCmd.Transaction = jmdictTrx;
                    foreach (var kanji in entry.KanjiElements)
                    {
                        kanjiCmd.Parameters.Clear();
                        kanjiCmd.Parameters.Add(new SqliteParameter("@entryId", entry.Id));
                        kanjiCmd.Parameters.Add(new SqliteParameter("@kanjiText", kanji.KanjiText));
                        kanjiCmd.Parameters.Add(new SqliteParameter("@priorities", string.Join(",", kanji.Priorities)));
                        kanjiCmd.ExecuteNonQuery();
                        insertedRowCount++;
                    }
                    #endregion

                    #region Senses
                    // insert senses in bulk
                    if (entry.Senses.Count() > 0)
                    {
                        var command = _connection.CreateCommand();
                        command.Transaction = jmdictTrx;
                        var sensesValuesTemplate = string.Join(",", entry.Senses.Select((_, i) => $"(@entryId{i}, @glossaries{i}, @partsOfSpeech{i}, @crossReferences{i})"));
                        command.CommandText = $@"
                        INSERT INTO {JMDICT_SENSE} (EntryId, Glossaries, PartsOfSpeech, CrossReferences)
                        VALUES {sensesValuesTemplate};
                        ";
                        var senseIdx = 0;
                        foreach (var sense in entry.Senses)
                        {
                            command.Parameters.Add(new SqliteParameter("@entryId" + senseIdx, entry.Id));
                            command.Parameters.Add(new SqliteParameter("@glossaries" + senseIdx, string.Join("|", sense.Glossaries)));
                            command.Parameters.Add(new SqliteParameter("@partsOfSpeech" + senseIdx, string.Join("|", sense.PartsOfSpeech)));
                            command.Parameters.Add(new SqliteParameter("@crossReferences" + senseIdx, string.Join("|", sense.CrossReferences)));
                            senseIdx++;
                            insertedRowCount++;
                        }
                        command.ExecuteNonQuery();
                    }
                    #endregion
                }

                jmdictTrx.Commit();
                Console.WriteLine("JMdict entries inserted successfully.");
            }
            catch (DbException ex)
            {
                Console.WriteLine(ex);
                jmdictTrx.Rollback();
            }
        }
        #endregion

        // #region Add FTS index
        // Console.WriteLine("Adding FTS index...");
        // using var ftsIndexTrx = _connection.BeginTransaction(deferred: true);
        // try
        // {
        //     var ftsIndexCmd = _connection.CreateCommand();
        //     ftsIndexCmd.Transaction = ftsIndexTrx;
        //     ftsIndexCmd.CommandText = $@"
        //     CREATE VIRTUAL TABLE IF NOT EXISTS {JMDICT_FTS_INDEX}
        //     USING fts4(Glossaries TEXT);

        //     INSERT INTO {JMDICT_FTS_INDEX}
        //     SELECT Glossaries FROM {JMDICT_SENSE};
        //     ";
        //     ftsIndexCmd.ExecuteNonQuery();
        //     ftsIndexTrx.Commit();
        // }
        // catch (DbException ex)
        // {
        //     Console.WriteLine(ex);
        //     ftsIndexTrx.Rollback();
        // }
        // #endregion

        // add index to the table for faster query
        AddTableIndex();

        // make the database file size smaller
        var vacumCmd = _connection.CreateCommand();
        vacumCmd.CommandText = $"VACUUM;";
        vacumCmd.ExecuteNonQuery();

        _connection.Close();

        return insertedRowCount;
    }
}
