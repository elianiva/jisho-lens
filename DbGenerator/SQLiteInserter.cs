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
    private const string JMDICT_FURIGANA_ENTRY = "JMdictFuriganaEntry";

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

    private void CreateTables()
    {
        _connection.Open();

        using var transaction = _connection.BeginTransaction();
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
            Text TEXT,
            Priorities TEXT,
            FOREIGN KEY (EntryId) REFERENCES {JMDICT_ENTRY}(Id) DEFERRABLE INITIALLY DEFERRED
        );

        CREATE TABLE IF NOT EXISTS {JMDICT_READING} (
            Id INTEGER PRIMARY KEY AUTOINCREMENT,
            EntryId INTEGER,
            Text TEXT,
            Priorities INTEGER,
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

        CREATE TABLE IF NOT EXISTS {JMDICT_FURIGANA} (
            Id INTEGER PRIMARY KEY,
            Text TEXT,
            Reading TEXT
        );

        CREATE TABLE IF NOT EXISTS {JMDICT_FURIGANA_ENTRY} (
            Id INTEGER PRIMARY KEY AUTOINCREMENT,
            FuriganaId INTEGER,
            Ruby TEXT,
            Rt TEXT,
            FOREIGN KEY (FuriganaId) REFERENCES {JMDICT_FURIGANA}(Id) DEFERRABLE INITIALLY DEFERRED
        );
        ";

        try
        {
            command.ExecuteNonQuery();
            transaction.Commit();
            Console.WriteLine("Tables created successfully.");
        }
        catch (DbException ex)
        {
            Console.WriteLine("Failed to create tables. Reason: {0}", ex.Message);
            transaction.Rollback();
        }

        _connection.Close();
    }

    public void Insert(IEnumerable<JmdictEntry> jmdictEntries, IEnumerable<FuriganaEntry> furiganaEntries)
    {
        if (furiganaEntries is null) throw new Exception("Furigana entries are null!");
        if (jmdictEntries is null) throw new Exception("JMdict entries are null!");

        Console.WriteLine("Removing old database file");
        RemoveDatabaseFile();

        Console.WriteLine("Creating tables...");
        CreateTables();

        Console.WriteLine("Inserting entries...");
        _connection.Open();

        // turn off synchronous mode and set journal_mode to memory to speed up inserts
        var _command = _connection.CreateCommand();
        _command.CommandText = "PRAGMA synchronous = OFF; PRAGMA journal_mode = MEMORY;";
        _command.ExecuteNonQuery();

        // inserting all of them in a single transaction is not a good idea
        foreach (var chunk in jmdictEntries.Chunk(10_000))
        {
            Console.WriteLine("Inserting chunk of {0} JMdict entries...", chunk.Count());
            using var transaction = _connection.BeginTransaction(deferred: true);

            var jmdictEntryCmd = _connection.CreateCommand();
            jmdictEntryCmd.CommandText = $@"
            INSERT INTO {JMDICT_ENTRY} (Id, EntrySequence)
            VALUES (@id, @entrySequence);
            ";
            jmdictEntryCmd.Transaction = transaction;

            foreach (var entry in chunk)
            {
                jmdictEntryCmd.Parameters.Clear();
                jmdictEntryCmd.Parameters.Add(new SqliteParameter("@id", entry.Id));
                jmdictEntryCmd.Parameters.Add(new SqliteParameter("@entrySequence", entry.EntrySequence));
                jmdictEntryCmd.ExecuteNonQuery();

                #region Kanji
                // insert kanji elements in bulk
                if (entry.KanjiElements.Count() > 0)
                {
                    var kanjiCmd = _connection.CreateCommand();
                    kanjiCmd.Transaction = transaction;
                    var kanjiValuesTemplate = string.Join(",", entry.KanjiElements.Select((_, i) => $"(@entryId{i}, @text{i}, @priorities{i})"));
                    kanjiCmd.CommandText = $@"
                    INSERT INTO {JMDICT_KANJI} (EntryId, Text, Priorities)
                    VALUES {kanjiValuesTemplate};
                    ";
                    var kanjiIdx = 0;
                    foreach (var kanji in entry.KanjiElements)
                    {
                        kanjiCmd.Parameters.Add(new SqliteParameter("@entryId" + kanjiIdx, entry.Id));
                        kanjiCmd.Parameters.Add(new SqliteParameter("@text" + kanjiIdx, kanji.Text));
                        kanjiCmd.Parameters.Add(new SqliteParameter("@priorities" + kanjiIdx, string.Join("|", kanji.Priorities)));
                        kanjiIdx++;
                    }
                    kanjiCmd.ExecuteNonQuery();
                }
                #endregion

                #region Readings
                // insert reading elements in bulk
                if (entry.ReadingElements.Count() > 0)
                {
                    var command = _connection.CreateCommand();
                    var readingValuesTemplate = string.Join(",", entry.ReadingElements.Select((_, i) => $"(@entryId{i}, @text{i}, @priorities{i})"));
                    command.CommandText = $@"
                    INSERT INTO {JMDICT_READING} (EntryId, Text, Priorities)
                    VALUES {readingValuesTemplate};
                    ";
                    var readingIdx = 0;
                    foreach (var reading in entry.ReadingElements)
                    {
                        command.Parameters.Add(new SqliteParameter("@entryId" + readingIdx, entry.Id));
                        command.Parameters.Add(new SqliteParameter("@text" + readingIdx, reading.Text));
                        command.Parameters.Add(new SqliteParameter("@priorities" + +readingIdx, string.Join("|", reading.Priorities)));
                        readingIdx++;
                    }
                    command.ExecuteNonQuery();
                }
                #endregion

                #region Senses
                // insert senses in bulk
                if (entry.Senses.Count() > 0)
                {
                    var command = _connection.CreateCommand();
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
                    }
                    command.ExecuteNonQuery();
                }
                #endregion

            }

            try
            {
                transaction.Commit();
                Console.WriteLine("JMdict entries inserted successfully.");
            }
            catch (DbException ex)
            {
                Console.WriteLine(ex);
                transaction.Rollback();
            }
        }

        foreach (var chunk in furiganaEntries.Chunk(10_000))
        {
            Console.WriteLine("Inserting chunk of {0} furigana entries...", chunk.Count());
            using var transaction = _connection.BeginTransaction(deferred: true);

            var furiganaCmd = _connection.CreateCommand();
            furiganaCmd.Transaction = transaction;
            furiganaCmd.CommandText = $@"
            INSERT INTO {JMDICT_FURIGANA} (Id, Text, Reading)
            VALUES (@id, @text, @reading);
            ";

            foreach (var furigana in chunk)
            {
                furiganaCmd.Parameters.Clear();
                furiganaCmd.Parameters.Add(new SqliteParameter("@id", furigana.Id));
                furiganaCmd.Parameters.Add(new SqliteParameter("@text", furigana.Text ?? ""));
                furiganaCmd.Parameters.Add(new SqliteParameter("@reading", furigana.Reading ?? ""));
                furiganaCmd.ExecuteNonQuery();

                // insert furigana entries in bulk
                var furiganaEntryCmd = _connection.CreateCommand();
                var furiganaValuesTemplate = string.Join(",", furigana.Furigana.Select((_, i) => $"(@furiganaId{i}, @ruby{i}, @rt{i})"));
                furiganaEntryCmd.CommandText = $@"
                INSERT INTO {JMDICT_FURIGANA_ENTRY} (FuriganaId, Ruby, Rt)
                VALUES {furiganaValuesTemplate};
                ";
                var furiganaIdx = 0;
                foreach (var furiganaEntry in furigana.Furigana)
                {
                    furiganaEntryCmd.Parameters.Add(new SqliteParameter("@furiganaId" + furiganaIdx, furigana.Id));
                    furiganaEntryCmd.Parameters.Add(new SqliteParameter("@ruby" + furiganaIdx, furiganaEntry.Ruby ?? ""));
                    furiganaEntryCmd.Parameters.Add(new SqliteParameter("@rt" + furiganaIdx, furiganaEntry.Rt ?? ""));
                }
                furiganaEntryCmd.ExecuteNonQuery();
            }

            try
            {
                transaction.Commit();
                Console.WriteLine("Furigana entries inserted successfully.");
            }
            catch (DbException ex)
            {
                Console.WriteLine(ex);
                transaction.Rollback();
            }
        }

        _connection.Close();
    }
}
