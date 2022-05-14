using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Unicode;

namespace Business.JmdictDomain;

public class JmdictEntry
{
    public int Id { get; init; }
    public int EntrySequence { get; init; }
    public IEnumerable<Kanji> KanjiElements { get; init; } = Enumerable.Empty<Kanji>();
    public IEnumerable<Reading> ReadingElements { get; init; } = Enumerable.Empty<Reading>();
    public IEnumerable<Sense> Senses { get; init; } = Enumerable.Empty<Sense>();

    public record Reading(string Text, IEnumerable<string> Priorities);

    public record Kanji(string Text, IEnumerable<string> Priorities);

    public record Sense(IEnumerable<string> Glossaries, IEnumerable<string> PartsOfSpeech, IEnumerable<string> CrossReferences);

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
