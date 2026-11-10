package com.elianiva.jisholens.data

/** A dictionary that the app can fetch for the reader, with the facts needed to choose it. */
data class DictionarySource(
    val title: String,
    val url: String,
    val pageUrl: String,
    val sizeMegabytes: Int,
    val license: String,
    val description: String,
)

/**
 * The dictionaries worth starting with. Both are Yomitan archives published by their
 * authors, both are CC BY-SA 4.0, and the app downloads them only when asked: the URL
 * is the publisher's latest release, not a copy.
 */
object DictionaryCatalog {
    val sources: List<DictionarySource> = listOf(
        DictionarySource(
            title = "Jitendex",
            url = "https://github.com/stephenmk/stephenmk.github.io/releases/latest/download/jitendex-yomitan.zip",
            pageUrl = "https://jitendex.org/pages/downloads.html",
            sizeMegabytes = 37,
            license = "CC BY-SA 4.0",
            description = "JMdict with example sentences, cross references, and part-of-speech tags. The best first dictionary for English readers.",
        ),
        DictionarySource(
            title = "JMdict",
            url = "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMdict_english.zip",
            pageUrl = "https://github.com/yomidevs/jmdict-yomitan",
            sizeMegabytes = 15,
            license = "CC BY-SA 4.0",
            description = "The original JMdict in Yomitan format. Smaller than Jitendex, and built daily.",
        ),
    )
}
