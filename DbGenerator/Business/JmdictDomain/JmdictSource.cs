using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Xml;
using System.Xml.Linq;
using System.Xml.XPath;

namespace Business.JmdictDomain;

public class JmdictSource
{
    private const string JMDICT_FILENAME = "JMdict_e.xml";
    private const string JMdict_Entry = "entry";
    private const string JMdict_EntrySequence = "ent_seq";
    private const string JMdict_Sense = "sense";
    private const string JMdict_Glossary = "gloss";
    private const string JMdict_PartOfSpeech = "pos";
    private const string JMdict_CrossRefeence = "xref";
    private const string JMdict_KanjiElement = "k_ele";
    private const string JMdict_KanjiContent = "keb";
    private const string JMdict_KanjiPriority = "ke_pri";
    private const string JMdict_ReadingElement = "r_ele";
    private const string JMdict_ReadingContent = "reb";
    private const string JMdict_ReadingPriority = "re_pri";
    private const string JMdict_ReadingNoKanji = "re_nokanji";

    private string _archivePath;

    public JmdictSource(string path)
    {
        if (string.IsNullOrEmpty(path))
        {
            throw new ArgumentException("Path to the jmdict file can't be empty!", nameof(path));
        }
        _archivePath = path;
    }

    private async Task<Stream> GetFileStreamFromArchive()
    {
        Console.WriteLine("Getting file stream from archive...");
        using var archiveStream = new FileStream(_archivePath, FileMode.Open, FileAccess.Read);
        using var decompressor = new GZipStream(archiveStream, CompressionMode.Decompress);
        var resultStream = new MemoryStream();

        Console.WriteLine("Decompressing archive...");
        await decompressor.CopyToAsync(resultStream);
        // we need to reset the filestream position because the gzip stream
        // will advance to the end of the file after copying to the file stream
        if (resultStream.Position > 0)
        {
            resultStream.Seek(0, SeekOrigin.Begin);
        }
        return resultStream;
    }

    public async Task<IEnumerable<JmdictEntry>> GetEntries()
    {
        using var fileStream = await GetFileStreamFromArchive();
        var readerSettings = new XmlReaderSettings
        {
            DtdProcessing = DtdProcessing.Parse,
        };
        using var xmlReader = XmlTextReader.Create(fileStream, readerSettings);

        Console.WriteLine("Loading xml...");
        var xdom = XDocument.Load(xmlReader);
        if (xdom.Root is null) throw new Exception("Failed to find xml root element.");

        var entities = xdom.DocumentType?.ToString();
        if (entities is null) throw new Exception("Failed to find xml entities.");

        // this dictionary is used to "unresolve" the expanded entity since the XmlReader
        // doesn't allow us to do that, apparently
        Console.WriteLine("Generating entities dictionary...");
        var posDefinition = ParseEntities(entities);

        Console.WriteLine("Parsing xml...");
        var entries = xdom.Root.Elements(JMdict_Entry).Select((entry, idx) =>
        {
            var readingElements = entry.Elements(JMdict_ReadingElement);
#pragma warning disable format
            return new JmdictEntry
            {
                Id = idx + 1,
                EntrySequence = int.Parse(entry.Element(JMdict_EntrySequence)?.Value ?? "0"),
                KanjiElements = (from k in entry.Elements(JMdict_KanjiElement)
                                 select new JmdictEntry.Kanji(
                                         KanjiText: k.Element(JMdict_KanjiContent)?.Value ?? "",
                                         Priorities: (from p in k.Elements(JMdict_KanjiPriority) select p.Value)
                                     )),
                ReadingElements = (from r in readingElements
                                   // Exclude the node if it contains the no kanji node, and is not the only reading.
                                   // This is a behavior that seems to be implemented in Jisho (example word: 台詞).
                                   where r.Element(JMdict_ReadingNoKanji) is null && readingElements.Count() <= 1
                                   select new JmdictEntry.Reading(
                                       KanjiText: r.Element(JMdict_ReadingContent)?.Value ?? "",
                                       Priorities: (from p in r.Elements(JMdict_ReadingPriority) select p.Value)
                                   )),
                Senses = (from s in entry.Elements(JMdict_Sense)
                          select new JmdictEntry.Sense(
                              Glossaries: (from gloss in s.Elements(JMdict_Glossary) select gloss.Value),
                              // unresolve the pos entity so we get the shorter version
                              // we'll resolve them in flutter
                              PartsOfSpeech: (from pos in s.Elements(JMdict_PartOfSpeech) select posDefinition[pos.Value]),
                              CrossReferences: (from xref in s.Elements(JMdict_CrossRefeence) select xref.Value)
                          )),
            };
#pragma warning restore format
        });

        return entries;
    }

    private Dictionary<string, string> ParseEntities(String dtd)
    {
        var entityRE = new Regex(@"<!ENTITY (?<key>.+) ""(?<definition>.+)"">");

        var entities = new Dictionary<string, string>();
        foreach (var match in dtd
                                .Split("\n")
                                .Where(d => d.StartsWith("<!ENTITY"))
                                .Select(line => entityRE.Match(line)))
        {
            var key = match.Groups["key"].Value;
            var definition = match.Groups["definition"].Value;
            entities[definition] = key;
        }

        return entities;
    }
}
