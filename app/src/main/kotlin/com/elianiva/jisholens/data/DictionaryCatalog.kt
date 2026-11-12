package com.elianiva.jisholens.data

import com.elianiva.jisholens.dictionary.DictionaryRecord

/** What a dictionary contributes to a lookup, which is also how the catalogue groups it. */
enum class DictionaryKind(val label: String) {
    TERM("Terms"),
    KANJI("Kanji"),
    FREQUENCY("Frequency"),
    PITCH("Pitch"),
    NAMES("Names"),
    SPECIALISED("Specialised"),
}

/** The five sections a reader browses: definitions, characters, ranks, pitch, and the rest. */
enum class DictionaryGroup(val label: String) {
    TERMS("Term dictionaries"),
    KANJI("Kanji dictionaries"),
    FREQUENCY("Frequency dictionaries"),
    PITCH("Pitch accent dictionaries"),
    NAMES_SPECIALISED("Names and specialised"),
}

val DictionaryKind.group: DictionaryGroup
    get() = when (this) {
        DictionaryKind.TERM -> DictionaryGroup.TERMS
        DictionaryKind.KANJI -> DictionaryGroup.KANJI
        DictionaryKind.FREQUENCY -> DictionaryGroup.FREQUENCY
        DictionaryKind.PITCH -> DictionaryGroup.PITCH
        DictionaryKind.NAMES, DictionaryKind.SPECIALISED -> DictionaryGroup.NAMES_SPECIALISED
    }

/**
 * A dictionary a reader can add, with the facts needed to choose it and the reason for
 * how it is offered.
 *
 * [url] is set only when the publisher's terms allow the app to fetch the archive for
 * the reader. A null [url] means the reader gets the file from [pageUrl] and imports it
 * with the file picker; the app never mirrors or bundles dictionary data.
 */
data class DictionarySource(
    val id: String,
    val title: String,
    val kind: DictionaryKind,
    /** The start of the archive's title, used to recognise an imported dictionary. */
    val titleMatch: String,
    val url: String?,
    val pageUrl: String,
    val sizeBytes: Long,
    val license: String,
    val terms: String,
    val description: String,
    val starter: Boolean = false,
)

/**
 * The Yomitan dictionaries that cover Japanese, grouped by what each one does.
 *
 * This follows the Japanese section of Yomitan's own recommended list (Jitendex, JMdict,
 * JMnedict, KANJIDIC, BCCWJ, JPDB) and the community picks that Yomitan readers install
 * for pitch and frequency. Every archive is the publisher's own file, and the licence
 * record decides whether the app may download it or only point at it.
 */
object DictionaryCatalog {
    val sources: List<DictionarySource> = listOf(
        DictionarySource(
            id = "jitendex",
            title = "Jitendex",
            kind = DictionaryKind.TERM,
            titleMatch = "Jitendex",
            url = "https://github.com/stephenmk/stephenmk.github.io/releases/latest/download/jitendex-yomitan.zip",
            pageUrl = "https://jitendex.org/pages/downloads.html",
            sizeBytes = 38_831_822,
            license = "CC BY-SA 4.0",
            terms = "Free to use and share, with attribution.",
            description = "JMdict with example sentences, cross references, and part-of-speech tags. The best first dictionary for English readers.",
            starter = true,
        ),
        DictionarySource(
            id = "jmdict",
            title = "JMdict",
            kind = DictionaryKind.TERM,
            titleMatch = "JMdict",
            url = "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMdict_english.zip",
            pageUrl = "https://github.com/yomidevs/jmdict-yomitan",
            sizeBytes = 15_626_183,
            license = "CC BY-SA 4.0 (EDRDG)",
            terms = "Free to use and share, with attribution.",
            description = "The original JMdict in Yomitan format, rebuilt daily. Smaller than Jitendex, with plainer glosses.",
        ),
        DictionarySource(
            id = "jmnedict",
            title = "JMnedict",
            kind = DictionaryKind.NAMES,
            titleMatch = "JMnedict",
            url = "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMnedict.zip",
            pageUrl = "https://github.com/yomidevs/jmdict-yomitan",
            sizeBytes = 11_433_834,
            license = "CC BY-SA 4.0 (EDRDG)",
            terms = "Free to use and share, with attribution.",
            description = "Person, place, and organisation names with their readings. Useful when a scan lands on a proper noun.",
        ),
        DictionarySource(
            id = "kanjidic",
            title = "KANJIDIC",
            kind = DictionaryKind.KANJI,
            titleMatch = "KANJIDIC",
            url = "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/KANJIDIC_english.zip",
            pageUrl = "https://github.com/yomidevs/jmdict-yomitan",
            sizeBytes = 721_032,
            license = "CC BY-SA 4.0 (EDRDG)",
            terms = "Free to use and share, with attribution.",
            description = "Readings, meanings, stroke counts, grade, and JLPT level for about 10,000 kanji.",
            starter = true,
        ),
        DictionarySource(
            id = "wiktionary-kanji",
            title = "Wiktionary Kanji",
            kind = DictionaryKind.KANJI,
            titleMatch = "Wiktionary漢字",
            url = "https://github.com/MarvNC/yomitan-dictionaries/raw/master/dl/%5BKanji%5D%20Wiktionary.zip",
            pageUrl = "https://github.com/MarvNC/yomitan-dictionaries#kanji-info",
            sizeBytes = 721_592,
            license = "CC BY-SA 4.0 (Wiktionary)",
            terms = "Free to use and share, with attribution.",
            description = "Readings, etymology, and variant forms for 18,000 kanji, converted from Japanese Wiktionary.",
        ),
        DictionarySource(
            id = "jiten",
            title = "Jiten",
            kind = DictionaryKind.FREQUENCY,
            titleMatch = "Jiten",
            url = "https://api.jiten.moe/api/frequency-list/download?downloadType=yomitan",
            pageUrl = "https://jiten.moe/frequency-dictionaries",
            sizeBytes = 7_982_407,
            license = "CC BY-SA 4.0",
            terms = "Free to use and share, with attribution.",
            description = "Word ranks from 18,000 films, novels, anime, manga, and games. The default frequency list, and the one that sorts scan results.",
            starter = true,
        ),
        DictionarySource(
            id = "jiten-kanji",
            title = "Jiten (Kanji)",
            kind = DictionaryKind.FREQUENCY,
            titleMatch = "Jiten (Kanji)",
            url = "https://api.jiten.moe/api/frequency-list/download-kanji?downloadType=yomitan",
            pageUrl = "https://jiten.moe/frequency-dictionaries",
            sizeBytes = 73_862,
            license = "CC BY-SA 4.0",
            terms = "Free to use and share, with attribution.",
            description = "Kanji ranks from the same Jiten corpus, by how often each character appears.",
        ),
        DictionarySource(
            id = "bccwj",
            title = "BCCWJ",
            kind = DictionaryKind.FREQUENCY,
            titleMatch = "BCCWJ",
            url = null,
            pageUrl = "https://github.com/Kuuuube/yomitan-dictionaries",
            sizeBytes = 18_659_666,
            license = "BCCWJ terms",
            terms = "Free for research and education only. Get the file from the page, then import it.",
            description = "Balanced Corpus of Contemporary Written Japanese. Wide coverage of books, news, and law, with readings kept apart.",
        ),
        DictionarySource(
            id = "jpdb",
            title = "JPDB v2.2",
            kind = DictionaryKind.FREQUENCY,
            titleMatch = "JPDBv2",
            url = null,
            pageUrl = "https://github.com/Kuuuube/yomitan-dictionaries",
            sizeBytes = 5_996_363,
            license = "No open licence",
            terms = "The converter publishes the file, but JPDB's data has no open licence. Get it from the page, then import it.",
            description = "Word ranks from the JPDB corpus of anime, dramas, novels, and visual novels, with kana readings counted separately.",
        ),
        DictionarySource(
            id = "wadoku-pitch",
            title = "Wadoku Pitch Accent",
            kind = DictionaryKind.PITCH,
            titleMatch = "Wadoku Pitch Accent",
            url = "https://github.com/classicsc/wadoku-pitch-dictionary-for-yomitan/releases/latest/download/wadoku-pitch.zip",
            pageUrl = "https://github.com/classicsc/wadoku-pitch-dictionary-for-yomitan",
            sizeBytes = 4_397_292,
            license = "WaDoku data licence",
            terms = "Free for personal and non-commercial use, with attribution.",
            description = "Pitch accents from the Wadoku Japanese-German dictionary. Draws the contour above each entry.",
            starter = true,
        ),
        DictionarySource(
            id = "wikipedia",
            title = "Wikipedia (Japanese)",
            kind = DictionaryKind.SPECIALISED,
            titleMatch = "JA Wikipedia",
            url = "https://github.com/MarvNC/wikipedia-yomitan/releases/download/v1.6.1/ja.Wikipedia.2022-12-01.v1.6.1.zip",
            pageUrl = "https://github.com/MarvNC/wikipedia-yomitan",
            sizeBytes = 211_915_015,
            license = "CC BY-SA 4.0 (Wikipedia)",
            terms = "Free to use and share, with attribution.",
            description = "Encyclopedic articles from the 2022 Japanese Wikipedia dump. Very large: about 1 GB after import.",
        ),
    )

    /** Definitions, characters, ranks, and pitch, all fetchable in one tap. */
    val starterSet: List<DictionarySource> = sources.filter { it.starter }

    val groups: List<Pair<DictionaryGroup, List<DictionarySource>>> =
        DictionaryGroup.entries.map { group -> group to sources.filter { it.kind.group == group } }

    /** The catalogue entry for an imported dictionary, matched on the longest title prefix. */
    fun find(title: String): DictionarySource? = sources
        .filter { title.startsWith(it.titleMatch) }
        .maxByOrNull { it.titleMatch.length }

    fun byId(id: String): DictionarySource = sources.first { it.id == id }

    /**
     * What an imported dictionary is, for the list. A catalogue title is exact; anything
     * else is read from what the archive actually stored.
     */
    fun kindOf(dictionary: DictionaryRecord): DictionaryKind? {
        find(dictionary.title)?.let { return it.kind }
        val counts = dictionary.counts
        return when {
            (counts["kanji"] ?: 0) > 0 -> DictionaryKind.KANJI
            (counts["terms"] ?: 0) > 0 -> DictionaryKind.TERM
            "pitch" in dictionary.termMetaModes -> DictionaryKind.PITCH
            "freq" in dictionary.termMetaModes -> DictionaryKind.FREQUENCY
            (counts["kanjiMeta"] ?: 0) > 0 -> DictionaryKind.FREQUENCY
            else -> null
        }
    }
}
