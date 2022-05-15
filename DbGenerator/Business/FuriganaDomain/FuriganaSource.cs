using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Unicode;
using System.Threading.Tasks;

namespace Business.FuriganaDomain;

public class FuriganaSource
{
    private string _jsonPath;

    public FuriganaSource(string path)
    {
        if (string.IsNullOrEmpty(path))
        {
            throw new ArgumentException("Path to the furigana file can't be empty!", nameof(path));
        }

        _jsonPath = path;
    }

    public async Task<IEnumerable<FuriganaEntry>> GetEntries()
    {
        Console.WriteLine("Getting entries from json...");
        using var fileStream = new FileStream(_jsonPath, FileMode.Open, FileAccess.Read);
        var serializerOptions = new JsonSerializerOptions
        {
            Encoder = JavaScriptEncoder.Create(UnicodeRanges.All),
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        };
        Console.WriteLine("Deserializing json...");
        var entries = await JsonSerializer.DeserializeAsync<IEnumerable<FuriganaEntry>>(fileStream, serializerOptions);
        return entries?.Select((e, i) => e with { Id = i + 1 }) ?? Enumerable.Empty<FuriganaEntry>();
    }
}
