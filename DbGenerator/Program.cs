using System;
using System.Diagnostics;
using System.IO;
using DbGenerator;
using DbGenerator.Business.FuriganaDomain;
using DbGenerator.Business.JmdictDomain;
using SQLitePCL;

var dataPath = Path.Join(Directory.GetCurrentDirectory(), "Data");

// use a custom sqlite with icu support
SQLite3Provider_dynamic_cdecl.Setup("sqlite3", new NativeLibraryAdapter(Path.Join(dataPath, "sqlite/.libs/libsqlite3.so")));
SQLitePCL.raw.SetProvider(new SQLite3Provider_dynamic_cdecl());

var furiganaSource = new FuriganaSource(Path.Join(dataPath, "JmdictFurigana.json"));
var jmdictSource = new JmdictSource(Path.Join(dataPath, "JMdict_e.gz"));
var inserter = new SQLiteInserter(Path.Join(dataPath, "jmdict.db"));

try
{
    Console.WriteLine("====[ Getting furigana entries ]====");
    var furiganaEntries = await furiganaSource.GetEntries();

    Console.WriteLine("====[ Getting jmdict entries ]====");
    var jmdictEntries = await jmdictSource.GetEntries();

    Console.WriteLine("====[ Inserting entries ]====");
    var stopwatch = new Stopwatch();
    stopwatch.Start();
    var insertedRows = inserter.Insert(jmdictEntries, furiganaEntries);
    stopwatch.Stop();
    Console.WriteLine($"====[ Inserted roughly {insertedRows} entries in {stopwatch.ElapsedMilliseconds}ms ]====");
}
catch (Exception ex)
{
    Console.WriteLine(ex);
    System.Environment.Exit(1);
}
