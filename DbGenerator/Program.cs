using System;
using System.Diagnostics;
using System.IO;
using DbGenerator;
using DbGenerator.Business.FuriganaDomain;
using DbGenerator.Business.JmdictDomain;
using SQLitePCL;

string dataPath = Path.Join(Directory.GetCurrentDirectory(), "Data");

// use a custom sqlite with icu support
SQLite3Provider_dynamic_cdecl.Setup("sqlite3", new NativeLibraryAdapter(Path.Join(dataPath, "sqlite/.libs/libsqlite3.so")));
SQLitePCL.raw.SetProvider(new SQLite3Provider_dynamic_cdecl());

FuriganaSource furiganaSource = new(Path.Join(dataPath, "JmdictFurigana.json"));
JmdictSource jmdictSource = new(Path.Join(dataPath, "JMdict_e.gz"));
SQLiteInserter inserter = new(Path.Join(dataPath, "jmdict.db"));

try
{
    Console.WriteLine("====[ Getting furigana entries ]====");
    System.Collections.Generic.IEnumerable<FuriganaEntry> furiganaEntries = await furiganaSource.GetEntries();

    Console.WriteLine("====[ Getting jmdict entries ]====");
    System.Collections.Generic.IEnumerable<JmdictEntry> jmdictEntries = await jmdictSource.GetEntries();

    Console.WriteLine("====[ Inserting entries ]====");
    Stopwatch stopwatch = new();
    stopwatch.Start();
    int insertedRows = inserter.Insert(jmdictEntries, furiganaEntries);
    stopwatch.Stop();
    Console.WriteLine($"====[ Inserted roughly {insertedRows} entries in {stopwatch.ElapsedMilliseconds}ms ]====");
}
catch (Exception ex)
{
    Console.WriteLine(ex);
    System.Environment.Exit(1);
}
