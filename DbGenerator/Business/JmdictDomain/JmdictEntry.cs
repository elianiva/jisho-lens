using System.Collections.Generic;
using System.Linq;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Unicode;

namespace DbGenerator.Business.JmdictDomain;

public sealed record Reading(string KanjiText, IEnumerable<string> Priorities);

public sealed record Kanji(string KanjiText, IEnumerable<string> Priorities);

public sealed record Sense(
    IEnumerable<string> Glossaries,
    IEnumerable<string> PartsOfSpeech,
    IEnumerable<string> CrossReferences
);

public sealed record JmdictEntry(
    int Id,
    int EntrySequence,
    IEnumerable<Kanji> KanjiElements,
    IEnumerable<Reading> ReadingElements,
    IEnumerable<Sense> Senses
)
{
#if DEBUG
    public override string ToString()
    {
        var serializerOptions = new JsonSerializerOptions
        {
            Encoder = JavaScriptEncoder.Create(UnicodeRanges.All),
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        };
        return JsonSerializer.Serialize(this, serializerOptions);
    }
#endif
}