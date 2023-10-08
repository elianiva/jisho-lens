using System;
using System.Collections.Generic;
using System.Data.Common;
using System.IO;
using System.Linq;
using DbGenerator.Business.FuriganaDomain;
using DbGenerator.Business.JmdictDomain;
using Microsoft.Data.Sqlite;

public class SQLiteInserter
{
    private const string JmdictEntry = "JMdictEntry";
    private const string JmdictKanji = "JMdictKanji";
    private const string JmdictReading = "JMdictReading";
    private const string JmdictSense = "JMdictSense";
    private const string JmdictSenseFts = "JMdictSenseFTS";
    private const string JmdictReadingFts = "JMdictReadingFTS";
    private const string JmdictKanjiFts = "JMdictKanjiFTS";

    private readonly SqliteConnection _connection;

    /// <summary>
    /// A class used to insert JMdict entries into a SQLite database.
    /// A custom sqlite3 binary with ICU support is required.
    /// </summary>
    public SQLiteInserter(string dbPath)
    {
        if (string.IsNullOrWhiteSpace(dbPath))
        {
            throw new ArgumentException("Path to the database or extension file can't be empty!", nameof(dbPath));
        }

        _connection = new SqliteConnection($"Data Source={dbPath}");
        Console.WriteLine(_connection.ServerVersion);
    }

    private void RemoveDatabaseFile()
    {
        bool fileExists = File.Exists(_connection.DataSource);
        if (fileExists)
        {
            File.Delete(_connection.DataSource);
        }
    }

    private void PrepareTables()
    {
        using SqliteTransaction transaction = _connection.BeginTransaction();
        try
        {
            SqliteCommand command = _connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = $@"
            CREATE TABLE IF NOT EXISTS {JmdictEntry} (
                Id INTEGER PRIMARY KEY,
                EntrySequence INTEGER
            );

            CREATE TABLE IF NOT EXISTS {JmdictKanji} (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                EntryId INTEGER,
                KanjiText TEXT,
                Priorities TEXT,
                FOREIGN KEY (EntryId) REFERENCES {JmdictEntry}(Id) DEFERRABLE INITIALLY DEFERRED
            );

            CREATE TABLE IF NOT EXISTS {JmdictSense} (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                EntryId INTEGER,
                Glossaries TEXT,
                PartsOfSpeech TEXT,
                CrossReferences TEXT,
                FOREIGN KEY (EntryId) REFERENCES {JmdictEntry}(Id) DEFERRABLE INITIALLY DEFERRED
            );

            CREATE TABLE IF NOT EXISTS {JmdictReading} (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                ReadingId INTEGER,
                ReadingOrder INTEGER,
                KanjiText TEXT,
                Reading TEXT,
                Ruby TEXT,
                Rt TEXT
            );

            CREATE VIRTUAL TABLE IF NOT EXISTS {JmdictSenseFts}
            USING fts4(
                Glossaries,
                content='{JmdictSense}'
            );
            CREATE TRIGGER {JmdictSenseFts} 
            AFTER INSERT ON {JmdictSense}
            BEGIN
                INSERT INTO {JmdictSenseFts} (rowid, Glossaries)
                VALUES (new.rowid, new.Glossaries);
            END;

            CREATE VIRTUAL TABLE IF NOT EXISTS {JmdictReadingFts}
            USING fts4(
                Reading,
                content='{JmdictReading}',
                tokenize=icu ja_JP
            );
            CREATE TRIGGER {JmdictReadingFts} 
            AFTER INSERT ON {JmdictReading}
            BEGIN
                INSERT INTO {JmdictReadingFts} (rowid, Reading)
                VALUES (new.rowid, new.Reading);
            END;

            CREATE VIRTUAL TABLE IF NOT EXISTS {JmdictKanjiFts}
            USING fts4(
                KanjiText,
                content='{JmdictKanji}',
                tokenize=icu ja_JP
            );
            CREATE TRIGGER {JmdictKanjiFts} 
            AFTER INSERT ON {JmdictKanji}
            BEGIN
                INSERT INTO {JmdictKanjiFts} (rowid, KanjiText)
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
            Environment.Exit(1);
        }
    }

    private void AddTableIndex()
    {
        using SqliteTransaction transaction = _connection.BeginTransaction();
        try
        {
            SqliteCommand command = _connection.CreateCommand();
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
        SqliteCommand sqliteCommand = _connection.CreateCommand();
        sqliteCommand.CommandText = "PRAGMA synchronous = OFF; PRAGMA journal_mode = MEMORY;";
        sqliteCommand.ExecuteNonQuery();

        Console.WriteLine("Inserting entries...");
        // count inserted entries
        int insertedRowCount = 0;

        #region Furigana Entries

        using SqliteTransaction furiganaTrx = _connection.BeginTransaction(deferred: true);
        try
        {
            List<FuriganaEntry> entries = furiganaEntries.ToList();
            Console.WriteLine("Inserting chunk of {0} furigana entries...", entries.Count());
            foreach (FuriganaEntry? furiganaEntry in entries)
            {
                SqliteCommand readingCmd = _connection.CreateCommand();
                readingCmd.Transaction = furiganaTrx;
                string readingValuesTemplate = string.Join(",",
                    furiganaEntry.Furigana.Select((_, i) =>
                        $"(@readingId{i}, @readingOrder{i}, @kanjiText{i}, @reading{i}, @ruby{i}, @rt{i})"));
                readingCmd.CommandText = $"""
                                            INSERT INTO {JmdictReading} (ReadingId, ReadingOrder, KanjiText, Reading, Ruby, Rt)
                                            VALUES {readingValuesTemplate};
                                          """;
                int readingIdx = 0;
                foreach (Furigana furigana in furiganaEntry.Furigana)
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
        foreach (JmdictEntry[] chunk in jmdictEntries.Chunk(50_000))
        {
            Console.WriteLine("Inserting chunk of {0} JMdict entries...", chunk.Count());
            using SqliteTransaction jmdictTrx = _connection.BeginTransaction(deferred: true);
            try
            {
                foreach (JmdictEntry? entry in chunk)
                {
                    SqliteCommand jmdictEntryCmd = _connection.CreateCommand();
                    jmdictEntryCmd.CommandText =
                        $"INSERT INTO {JmdictEntry} (Id, EntrySequence) VALUES (@id, @entrySequence);";
                    jmdictEntryCmd.Transaction = jmdictTrx;
                    jmdictEntryCmd.Parameters.Clear();
                    jmdictEntryCmd.Parameters.Add(new SqliteParameter("@id", entry.Id));
                    jmdictEntryCmd.Parameters.Add(new SqliteParameter("@entrySequence", entry.EntrySequence));
                    jmdictEntryCmd.ExecuteNonQuery();

                    #region Kanji

                    // insert kanji and reading elements in bulk
                    if (entry.KanjiElements.Any())
                    {
                        SqliteCommand kanjiCmd = _connection.CreateCommand();
                        string kanjiTemplate = string.Join(",",
                            entry.KanjiElements.Select((_, index) => $"(@entryId{index}, @kanjiText{index}, @priorities{index})"));
                        kanjiCmd.CommandText =
                            $"INSERT INTO {JmdictKanji} (EntryId, KanjiText, Priorities) VALUES {kanjiTemplate};";
                        kanjiCmd.Transaction = jmdictTrx;
                        int kanjiIdx = 0;
                        foreach (Kanji kanji in entry.KanjiElements)
                        {
                            kanjiCmd.Parameters.Add(new SqliteParameter("@entryId" + kanjiIdx, entry.Id));
                            kanjiCmd.Parameters.Add(new SqliteParameter("@kanjiText" + kanjiIdx, kanji.KanjiText));
                            kanjiCmd.Parameters.Add(new SqliteParameter("@priorities" + kanjiIdx,
                                string.Join(",", kanji.Priorities)));
                            insertedRowCount++;
                            kanjiIdx++;
                        }
                        kanjiCmd.ExecuteNonQuery();
                    }

                    #endregion

                    #region Senses

                    // insert senses in bulk
                    if (entry.Senses.Any())
                    {
                        SqliteCommand command = _connection.CreateCommand();
                        command.Transaction = jmdictTrx;
                        string sensesValuesTemplate = string.Join(",",
                            entry.Senses.Select((_, i) =>
                                $"(@entryId{i}, @glossaries{i}, @partsOfSpeech{i}, @crossReferences{i})"));
                        command.CommandText = $"""
                                                   INSERT INTO {JmdictSense} (EntryId, Glossaries, PartsOfSpeech, CrossReferences)
                                                   VALUES {sensesValuesTemplate};
                                               """;
                        int senseIdx = 0;
                        foreach (Sense sense in entry.Senses)
                        {
                            command.Parameters.Add(new SqliteParameter("@entryId" + senseIdx, entry.Id));
                            command.Parameters.Add(new SqliteParameter("@glossaries" + senseIdx,
                                string.Join("|", sense.Glossaries)));
                            command.Parameters.Add(new SqliteParameter("@partsOfSpeech" + senseIdx,
                                string.Join("|", sense.PartsOfSpeech)));
                            command.Parameters.Add(new SqliteParameter("@crossReferences" + senseIdx,
                                string.Join("|", sense.CrossReferences)));
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

        // add index to the table for faster query
        AddTableIndex();

        // make the database file size smaller
        SqliteCommand vacuumCmd = _connection.CreateCommand();
        vacuumCmd.CommandText = $"VACUUM;";
        vacuumCmd.ExecuteNonQuery();

        _connection.Close();

        return insertedRowCount;
    }
}