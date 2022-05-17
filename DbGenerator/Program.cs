using System;
using System.Diagnostics;
using System.IO;
using Business.FuriganaDomain;
using Business.JmdictDomain;

string DATA_PATH = Path.Join(Directory.GetCurrentDirectory(), "Data");

var inserter = new SQLiteInserter(Path.Join(DATA_PATH, "jmdict.db"));
var furiganaSource = new FuriganaSource(Path.Join(DATA_PATH, "JmdictFurigana.json"));
var jmdictSource = new JmdictSource(Path.Join(DATA_PATH, "JMdict_e.gz"));

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
