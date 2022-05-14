using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Threading.Tasks;
using System.Xml;
using System.Xml.Linq;

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

    private string _archivePath;

    public JmdictSource(string path)
    {
        if (string.IsNullOrEmpty(path))
        {
            throw new ArgumentException("Path to the jmdict file can't be empty!", nameof(path));
        }
        _archivePath = path;
    }

    private async Task<FileStream> GetFileStreamFromArchive()
    {
        Console.WriteLine("Getting file stream from archive...");
        using var archiveStream = new FileStream(_archivePath, FileMode.Open, FileAccess.Read);
        using var decompressor = new GZipStream(archiveStream, CompressionMode.Decompress);
        var fileStream = File.Create(JMDICT_FILENAME);

        Console.WriteLine("Decompressing archive...");
        await decompressor.CopyToAsync(fileStream);
        return fileStream;
    }

    public async Task<IEnumerable<JmdictEntry>> GetEntries()
    {
        using var fileStream = await GetFileStreamFromArchive();
        // we need to reset the filestream position because the gzip stream
        // will advance to the end of the file after copying to the file stream
        if (fileStream.Position > 0)
        {
            fileStream.Seek(0, SeekOrigin.Begin);
        }
        var readerSettings = new XmlReaderSettings
        {
            DtdProcessing = DtdProcessing.Parse,
            MaxCharactersFromEntities = long.MaxValue,
            MaxCharactersInDocument = long.MaxValue,
        };
        using var xmlReader = XmlReader.Create(fileStream, readerSettings);

        Console.WriteLine("Loading xml...");
        var xdom = XDocument.Load(xmlReader);
        if (xdom.Root is null) throw new Exception("Failed to find xml root element.");

        Console.WriteLine("Parsing xml...");
        var entries = xdom.Root.Elements(JMdict_Entry).Select((entry, idx) =>
        {
            return new JmdictEntry
            {
                Id = idx + 1,
                EntrySequence = int.Parse(entry.Element(JMdict_EntrySequence)?.Value ?? "0"),
                KanjiElements = (from k in entry.Elements(JMdict_KanjiElement)
                                 select new JmdictEntry.Kanji(
                                         Text: k.Element(JMdict_KanjiContent)?.Value ?? "",
                                         Priorities: (from p in k.Elements(JMdict_KanjiPriority) select p.Value)
                                     )),
                ReadingElements = (from r in entry.Elements(JMdict_ReadingElement)
                                   select new JmdictEntry.Reading(
                                       Text: r.Element(JMdict_ReadingContent)?.Value ?? "",
                                       Priorities: (from p in r.Elements(JMdict_ReadingPriority) select p.Value)
                                   )),
                Senses = (from s in entry.Elements(JMdict_Sense)
                          select new JmdictEntry.Sense(
                              Glossaries: (from gloss in s.Elements(JMdict_Glossary) select gloss.Value),
                              PartsOfSpeech: (from pos in s.Elements(JMdict_PartOfSpeech) select pos.Value),
                              CrossReferences: (from xref in s.Elements(JMdict_CrossRefeence) select xref.Value)
                          )),
            };
        });

        return entries;
    }
}
