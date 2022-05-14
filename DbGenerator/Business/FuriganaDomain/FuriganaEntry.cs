using System.Collections.Generic;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Unicode;

namespace Business.FuriganaDomain;

public record FuriganaEntry(
    int Id,
    string? Text,
    string? Reading,
    List<Furigana> Furigana
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
