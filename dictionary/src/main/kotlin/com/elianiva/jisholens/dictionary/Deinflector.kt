package com.elianiva.jisholens.dictionary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One deinflected form of the input text. */
data class Deinflection(
    /** The text that the caller asked to deinflect. */
    val originalText: String,
    /** The dictionary form, or an intermediate form on the way to it. */
    val text: String,
    /** Tag chain produced by the rules; its last class tag identifies the word class. */
    val tags: List<String>,
    /** Rule descriptions in the order that the deinflector applied them. */
    val reasons: List<String>,
)

/**
 * Walks Japanese conjugation rules backwards.
 *
 * The rules are data, not code. They are ported from Jiten (Apache-2.0), which
 * takes them from JL (Apache-2.0). JL credits Nazeka, whose rule file marks
 * itself as public domain data. This port is distributed under the same
 * Apache-2.0 license. See `THIRD_PARTY_NOTICES.md` and the
 * `LICENSE-APACHE-2.0.txt` file next to the rule resource.
 *
 * [deinflect] returns every form that the rules can reach, including forms that
 * are not dictionary entries. Callers use the tag chain to keep only entries
 * whose part of speech matches, and to skip intermediate stems.
 */
class Deinflector private constructor(private val rules: List<Rule>) {
    private val rulesByEnding: Map<String, List<Rule>>
    private val longestEnding: Int

    init {
        val index = HashMap<String, MutableList<Rule>>()
        var longest = 0
        for (rule in rules) {
            index.getOrPut(rule.conEnd) { ArrayList() }.add(rule)
            if (rule.conEnd.length > longest) longest = rule.conEnd.length
        }
        rulesByEnding = index
        longestEnding = longest
    }

    /**
     * Returns the forms reachable from [text], longest text first. The input
     * itself is not returned. Each form carries the tags and rule descriptions
     * that explain how the deinflector reached it.
     */
    fun deinflect(text: String): List<Deinflection> {
        val original = text.trim()
        if (original.isEmpty()) return emptyList()
        // Rules are written in hiragana. Fold katakana input so that タベタ
        // follows the same path as たべた.
        val input = Kana.toHiragana(original)

        val maxTextLength = input.length + MAX_TEXT_GROWTH
        val maxTagCount = input.length + MAX_CHAIN_DEPTH
        val seen = HashSet<FormKey>()
        val discovered = ArrayList<Deinflection>()
        val initial = Deinflection(original, input, emptyList(), emptyList())
        seen.add(FormKey(input, emptyList()))
        var frontier = ArrayList<Deinflection>()
        frontier.add(initial)

        while (frontier.isNotEmpty() && discovered.size < MAX_STATES) {
            val next = ArrayList<Deinflection>()
            for (form in frontier) {
                if (discovered.size >= MAX_STATES) break
                if (form.text.isEmpty() || form.text.length > maxTextLength || form.tags.size > maxTagCount) {
                    continue
                }
                val maxSuffix = minOf(longestEnding, form.text.length)
                for (suffixLength in 0..maxSuffix) {
                    val suffix = form.text.substring(form.text.length - suffixLength)
                    val candidates = rulesByEnding[suffix] ?: continue
                    for (rule in candidates) {
                        if (!applies(rule, form)) continue

                        val prefixLength = form.text.length - rule.conEnd.length
                        val newText = form.text.substring(0, prefixLength) + rule.decEnd
                        if (newText == input) continue

                        val newTags = nextTags(form, rule)
                        if (!seen.add(FormKey(newText, newTags))) continue

                        val reasons = if (rule.detail.isEmpty()) {
                            form.reasons
                        } else {
                            form.reasons + rule.detail
                        }
                        val deinflection = Deinflection(original, newText, newTags, reasons)
                        discovered.add(deinflection)
                        next.add(deinflection)
                        if (discovered.size >= MAX_STATES) break
                    }
                    if (discovered.size >= MAX_STATES) break
                }
            }
            frontier = next
        }

        discovered.sortWith(
            compareByDescending<Deinflection> { it.text.length }.thenBy { it.text },
        )
        return discovered
    }

    private fun applies(rule: Rule, form: Deinflection): Boolean {
        when (rule.mode) {
            RuleMode.OnlyFinal -> if (form.tags.isNotEmpty()) return false
            RuleMode.NeverFinal -> if (form.tags.isEmpty()) return false
            RuleMode.Rewrite -> if (form.text != rule.conEnd) return false
            RuleMode.Context -> if (!contextAllows(rule, form)) return false
            RuleMode.Standard -> Unit
        }
        // A rule without a description only makes sense after another rule has
        // established the tag that it consumes.
        if (rule.detail.isEmpty() && form.tags.isEmpty()) return false
        if (form.tags.isNotEmpty() && form.tags.last() != rule.conTag) return false
        return true
    }

    private fun contextAllows(rule: Rule, form: Deinflection): Boolean = when (rule.context) {
        "v1inftrap" -> form.tags != listOf("stem-ren")
        "saspecial" -> {
            val prefixLength = form.text.length - rule.conEnd.length
            prefixLength <= 0 || form.text[prefixLength - 1] != 'さ'
        }
        "temirurule" -> {
            val prefix = form.text.substring(0, form.text.length - rule.conEnd.length)
            prefix.endsWith('て') || prefix.endsWith('で')
        }
        else -> true
    }

    private fun nextTags(form: Deinflection, rule: Rule): List<String> {
        if (form.tags.isEmpty()) {
            return buildList(2) {
                rule.conTag?.let(::add)
                rule.decTag?.let(::add)
            }
        }
        return if (rule.decTag == null) form.tags else form.tags + rule.decTag
    }

    private data class FormKey(val text: String, val tags: List<String>)

    private class Rule(
        val mode: RuleMode,
        val context: String?,
        val decEnd: String,
        val conEnd: String,
        val decTag: String?,
        val conTag: String?,
        val detail: String,
    )

    private enum class RuleMode { Standard, OnlyFinal, NeverFinal, Context, Rewrite }

    @Serializable
    private data class RuleJson(
        val type: String = "stdrule",
        val contextrule: String? = null,
        @SerialName("dec_end") val decEnd: List<String> = emptyList(),
        @SerialName("con_end") val conEnd: List<String> = emptyList(),
        @SerialName("dec_tag") val decTag: List<String>? = null,
        @SerialName("con_tag") val conTag: List<String>? = null,
        val detail: String = "",
    )

    companion object {
        private const val RULES_RESOURCE = "/deconjugation/deconjugator.json"
        private const val MAX_TEXT_GROWTH = 10
        private const val MAX_CHAIN_DEPTH = 6
        private const val MAX_STATES = 2_000

        /** The rules shipped with this library. They are parsed on first use. */
        val default: Deinflector by lazy { load() }

        private fun RuleJson.expand(): List<Rule> {
            if (decEnd.isEmpty() || conEnd.isEmpty()) return emptyList()
            return decEnd.indices.map { index ->
                Rule(
                    mode = when (type) {
                        "onlyfinalrule" -> RuleMode.OnlyFinal
                        "neverfinalrule" -> RuleMode.NeverFinal
                        "contextrule" -> RuleMode.Context
                        "rewriterule" -> RuleMode.Rewrite
                        else -> RuleMode.Standard
                    },
                    context = contextrule,
                    decEnd = decEnd[index],
                    conEnd = conEnd.pick(index),
                    decTag = decTag.pickOrNull(index),
                    conTag = conTag.pickOrNull(index),
                    detail = detail,
                )
            }
        }

        /** Short arrays apply their first value to every ending, as in Jiten. */
        private fun List<String>.pick(index: Int): String = if (index < size) this[index] else first()

        private fun List<String>?.pickOrNull(index: Int): String? = when {
            this == null || isEmpty() -> null
            index < size -> this[index]
            else -> first()
        }

        private fun load(): Deinflector {
            val stream = Deinflector::class.java.getResourceAsStream(RULES_RESOURCE)
                ?: error("Missing deconjugation rules at $RULES_RESOURCE")
            val source = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = source.lineSequence()
                .filterNot { it.trimStart().startsWith("//") }
                .joinToString("\n")
            val entries = Json { ignoreUnknownKeys = true }.decodeFromString<List<RuleJson>>(json)
            return Deinflector(entries.flatMap { it.expand() })
        }
    }
}

/**
 * Compares a deinflection tag chain with the `rules` column of a dictionary
 * entry, and decides which forms are worth querying.
 */
internal object DeinflectionTags {
    private val classes = setOf(
        "v1", "v1-s", "v4k", "v4g", "v4s", "v4t", "v4n", "v4b", "v4m", "v4r", "v4h",
        "v5aru", "v5b", "v5g", "v5k", "v5k-s", "v5m", "v5n", "v5r", "v5r-i", "v5s",
        "v5t", "v5u", "v5u-s", "v5uru", "vz", "vk", "vs", "vs-i", "vs-s", "vs-c",
        "vn", "vr", "aux", "aux-v", "adj-i", "adj-ix", "adj-na",
    )

    private val stems = setOf(
        "stem-past", "stem-te", "stem-te-defective", "stem-te-verbal",
        "stem-ren", "stem-ren-less", "stem-ren-less-v",
    )

    private val directVerbs = setOf(
        "v1", "v1-s", "v5aru", "v5b", "v5g", "v5k", "v5k-s", "v5m", "v5n",
        "v5r", "v5r-i", "v5s", "v5t", "v5u", "v5u-s", "v5uru", "vk", "vz",
        "v4k", "v4g", "v4s", "v4t", "v4n", "v4b", "v4m", "v4r", "v4h",
        "v2a-s", "v2b-k", "v2d-s", "v2g-k", "v2g-s", "v2h-k", "v2h-s",
        "v2k-k", "v2k-s", "v2m-k", "v2m-s", "v2n-s", "v2r-k", "v2r-s",
        "v2s-s", "v2t-k", "v2t-s", "v2w-s", "v2y-k", "v2y-s", "v2z-s",
        "vn", "vr", "aux-v",
    )

    private val stemMatches = mapOf(
        "stem-past" to directVerbs + setOf("adj-i", "adj-na"),
        "stem-te" to directVerbs + setOf("adj-i", "adj-na"),
        "stem-te-defective" to directVerbs,
        "stem-te-verbal" to directVerbs,
        "stem-ren" to directVerbs,
        "stem-ren-less" to directVerbs,
        "stem-ren-less-v" to directVerbs + setOf("adj-na"),
    )

    /**
     * True when the chain can describe a dictionary entry. A chain that ends
     * on a part-of-speech class, or that never reaches one, is a candidate.
     * A chain that reached a class and then stopped on a stem is an
     * intermediate form, such as the 食べ stem of 食べる.
     */
    fun isDictionaryForm(tags: List<String>): Boolean {
        if (tags.isEmpty()) return true
        val last = tags.last()
        return last in classes || last == "n" || tags.none { it in classes }
    }

    private val compatible = mapOf(
        "v1" to setOf("v1", "v1-s"),
        "v1-s" to setOf("v1-s", "v1"),
        "vs" to setOf("vs", "vs-i", "vs-s", "vs-c"),
        "vs-i" to setOf("vs-i", "vs", "vs-s"),
        "vs-s" to setOf("vs-s", "vs", "vs-i"),
        "vs-c" to setOf("vs-c", "vs"),
        "adj-i" to setOf("adj-i", "exp"),
        "adj-ix" to setOf("adj-ix", "adj-i"),
        "v5k" to setOf("v5k", "v5k-s"),
        "v5k-s" to setOf("v5k-s", "v5k"),
        "v5r" to setOf("v5r", "v5r-i", "v4r"),
        "v5r-i" to setOf("v5r-i", "v5r"),
        "v5u" to setOf("v5u", "v5u-s"),
        "v5u-s" to setOf("v5u-s", "v5u"),
        "v5aru" to setOf("v5aru", "v5r", "v5r-i", "v4r"),
        "v4r" to setOf("v4r", "v5r", "v5r-i", "v5aru"),
    )

    /** True when [tag] names a part of speech this matcher understands. */
    fun isPartOfSpeechTag(tag: String): Boolean = tag in classes || tag in stems

    fun matches(partsOfSpeech: Set<String>, tags: List<String>): Boolean {
        val last = tags.lastOrNull { it in classes || it in stems } ?: return true
        val rules = partsOfSpeech.filter { it.isNotEmpty() }.toSet()
        if (rules.isEmpty()) return false

        if (last in stems) {
            val allowed = stemMatches[last] ?: return true
            return rules.any { rule ->
                rule in allowed || (last.startsWith("v5") && rule == "v5")
            }
        }
        val alternatives = compatible[last]
        return rules.any { rule ->
            rule == last ||
                alternatives?.contains(rule) == true ||
                (last.startsWith("v5") && rule == "v5") ||
                (last.startsWith("v1") && rule == "v1")
        }
    }
}
