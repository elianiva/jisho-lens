using System.Collections.Generic;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.Unicode;

namespace DbGenerator.Business.FuriganaDomain;

public sealed record FuriganaEntry(
    int Id,
    string? Reading,
    List<Furigana> Furigana
)
{
    [JsonPropertyName("text")]
    public string? KanjiText { get; init; }

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