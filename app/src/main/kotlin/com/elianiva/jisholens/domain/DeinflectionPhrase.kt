package com.elianiva.jisholens.domain

/**
 * A deinflection chain written for a reader: the rules in the order they were applied
 * to the surface, and the English phrase those rules produce from the dictionary gloss.
 *
 * The deinflector records reasons as it peels a surface back to its dictionary form,
 * so the list arrives as `past`, `passive`, `causative` for 食べさせられた. The chain
 * shows them in the order the reader meets them in the word: causative, passive, past.
 */
data class DeinflectionPhrase(
    val steps: List<String>,
    val meaning: String?,
) {
    val chain: String get() = steps.joinToString(" → ")
}

/** Structural steps such as `(stem)` describe the rule, not the meaning, so they stay out of the chain. */
private fun semantic(reason: String): Boolean =
    reason.isNotBlank() && !reason.startsWith("(")

/**
 * Turns deinflector [reasons] and a dictionary [gloss] into a readable chain and a
 * phrase. Returns null when there is no reason worth showing.
 */
fun deinflectionPhrase(reasons: List<String>, gloss: String?): DeinflectionPhrase? {
    val ordered = reasons.filter(::semantic).asReversed()
    if (ordered.isEmpty()) return null
    val base = gloss?.let(::firstSense)
    return DeinflectionPhrase(
        steps = ordered.map(::stepLabel),
        meaning = base?.let { deriveMeaning(it, ordered) }?.takeIf { it.isNotBlank() },
    )
}

private val labels = mapOf(
    "causative" to "causative",
    "short causative" to "short causative",
    "passive" to "passive",
    "passive/potential" to "passive or potential",
    "past" to "past",
    "negative" to "negative",
    "polite" to "polite",
    "teiru" to "progressive",
    "teru (teiru)" to "progressive",
    "teoru" to "progressive",
    "toru (teoru)" to "progressive",
    "potential" to "potential",
    "volitional" to "volitional",
    "polite volitional" to "volitional",
    "shortened volitional" to "volitional",
    "imperative" to "imperative",
    "conditional" to "conditional",
    "provisional conditional" to "conditional",
    "formal conditional" to "conditional",
    "negative conditional" to "negative conditional",
    "want" to "want",
    "too much" to "excess",
    "excess" to "excess",
    "hearsay" to "hearsay",
    "seeming" to "seeming",
    "seemingness" to "seeming",
    "garu" to "shows signs of",
    "past polite" to "past, polite",
    "negative polite" to "negative, polite",
    "formal negative" to "negative, formal",
    "formal negative past" to "past, negative, formal",
    "past negative polite" to "past, negative, polite",
    "polite command" to "polite imperative",
    "negative polite (classical ぬ)" to "negative, classical",
)

private fun stepLabel(reason: String): String = labels[reason] ?: reason

/** The first dictionary sense, without `to`, alternatives, or a parenthetical note. */
internal fun firstSense(gloss: String): String? {
    var sense = gloss.trim()
        .substringBefore(';')
        .substringBefore(',')
        .substringBefore(" (")
        .trim()
    if (sense.startsWith("to ")) sense = sense.removePrefix("to ")
    return sense.takeIf { it.isNotBlank() }
}

private fun deriveMeaning(base: String, steps: List<String>): String {
    var phrase = base
    for (step in steps) {
        phrase = when (step) {
            "causative", "short causative" -> causative(phrase)
            "passive", "passive/potential" -> passive(phrase)
            "past", "past polite", "formal negative past", "past negative polite" -> past(phrase)
            "negative", "negative polite", "formal negative", "negative polite (classical ぬ)" -> negative(phrase)
            "teiru", "teru (teiru)", "teoru", "toru (teoru)" -> progressive(phrase)
            "potential" -> "can ${infinitive(phrase)}"
            "volitional", "polite volitional", "shortened volitional" -> "let's ${infinitive(phrase)}"
            "imperative", "polite command" -> "${infinitive(phrase)}!"
            "conditional", "provisional conditional", "formal conditional" -> "if ${infinitive(phrase)}"
            "negative conditional" -> "if ${negative(phrase)}"
            "want" -> "want to ${infinitive(phrase)}"
            "too much", "excess" -> "over-${infinitive(phrase)}"
            else -> phrase
        }
    }
    return phrase
}

private fun causative(phrase: String): String =
    if (phrase.startsWith("be ")) {
        "make (someone) ${phrase.removePrefix("be ")}"
    } else {
        "make (someone) ${infinitive(phrase)}"
    }

private fun passive(phrase: String): String = when {
    phrase.startsWith("make (someone) ") -> "be made to ${phrase.removePrefix("make (someone) ")}"
    phrase.startsWith("be ") -> phrase
    phrase.startsWith("not ") -> "not be ${participle(phrase.removePrefix("not "))}"
    else -> "be ${participle(phrase)}"
}

private fun past(phrase: String): String = when {
    phrase.startsWith("not be ") -> "was not ${phrase.removePrefix("not be ")}"
    phrase.startsWith("not ") -> "did not ${infinitive(phrase.removePrefix("not "))}"
    phrase.startsWith("be ") -> "was ${phrase.removePrefix("be ")}"
    phrase.startsWith("is ") -> "was ${phrase.removePrefix("is ")}"
    phrase.startsWith("are ") -> "were ${phrase.removePrefix("are ")}"
    phrase.startsWith("can ") -> "could ${phrase.removePrefix("can ")}"
    phrase.startsWith("cannot ") -> "could not ${phrase.removePrefix("cannot ")}"
    phrase.startsWith("make (someone) ") -> "made (someone) ${phrase.removePrefix("make (someone) ")}"
    phrase.startsWith("want to ") -> "wanted to ${phrase.removePrefix("want to ")}"
    phrase.startsWith("let's ") -> phrase
    else -> pastFirstWord(phrase)
}

private fun negative(phrase: String): String = when {
    phrase.startsWith("is ") -> "is not ${phrase.removePrefix("is ")}"
    phrase.startsWith("are ") -> "are not ${phrase.removePrefix("are ")}"
    phrase.startsWith("can ") -> "cannot ${phrase.removePrefix("can ")}"
    phrase.startsWith("be ") -> "not ${phrase}"
    else -> "not ${infinitive(phrase)}"
}

private fun progressive(phrase: String): String = when {
    phrase.startsWith("be ") -> "being ${phrase.removePrefix("be ")}"
    phrase.startsWith("not ") -> "is not ${presentParticiple(phrase.removePrefix("not "))}"
    else -> "is ${presentParticiple(phrase)}"
}

private fun infinitive(phrase: String): String = phrase.removePrefix("to ")

private fun pastFirstWord(phrase: String): String {
    val split = phrase.indexOf(' ')
    val first = if (split < 0) phrase else phrase.substring(0, split)
    val rest = if (split < 0) "" else phrase.substring(split)
    return pastTense(first) + rest
}

private fun participle(phrase: String): String {
    val split = phrase.indexOf(' ')
    val first = if (split < 0) phrase else phrase.substring(0, split)
    val rest = if (split < 0) "" else phrase.substring(split)
    return participles[first] ?: pastTense(first) + rest
}

private fun presentParticiple(phrase: String): String {
    val split = phrase.indexOf(' ')
    val first = if (split < 0) phrase else phrase.substring(0, split)
    val rest = if (split < 0) "" else phrase.substring(split)
    return presentParticiples[first] ?: when {
        first.endsWith("ie") -> first.dropLast(2) + "ying"
        first.endsWith("e") && first.length > 2 -> first.dropLast(1) + "ing"
        else -> first + "ing"
    } + rest
}

private fun pastTense(word: String): String = pasts[word] ?: when {
    word.endsWith("e") -> word + "d"
    word.endsWith("y") && word.length > 1 && word[word.length - 2] !in "aeiou" -> word.dropLast(1) + "ied"
    else -> word + "ed"
}

private val pasts = mapOf(
    "be" to "was",
    "become" to "became",
    "begin" to "began",
    "bring" to "brought",
    "buy" to "bought",
    "come" to "came",
    "do" to "did",
    "drink" to "drank",
    "eat" to "ate",
    "get" to "got",
    "give" to "gave",
    "go" to "went",
    "have" to "had",
    "hear" to "heard",
    "know" to "knew",
    "make" to "made",
    "read" to "read",
    "run" to "ran",
    "say" to "said",
    "see" to "saw",
    "sit" to "sat",
    "speak" to "spoke",
    "take" to "took",
    "teach" to "taught",
    "think" to "thought",
    "understand" to "understood",
    "write" to "wrote",
)

private val participles = mapOf(
    "be" to "been",
    "become" to "become",
    "begin" to "begun",
    "bring" to "brought",
    "buy" to "bought",
    "come" to "come",
    "do" to "done",
    "drink" to "drunk",
    "eat" to "eaten",
    "get" to "gotten",
    "give" to "given",
    "go" to "gone",
    "have" to "had",
    "hear" to "heard",
    "know" to "known",
    "make" to "made",
    "read" to "read",
    "run" to "run",
    "say" to "said",
    "see" to "seen",
    "sit" to "sat",
    "speak" to "spoken",
    "take" to "taken",
    "teach" to "taught",
    "think" to "thought",
    "understand" to "understood",
    "write" to "written",
)

private val presentParticiples = mapOf(
    "be" to "being",
    "come" to "coming",
    "die" to "dying",
    "get" to "getting",
    "lie" to "lying",
    "make" to "making",
    "run" to "running",
    "sit" to "sitting",
    "stop" to "stopping",
    "take" to "taking",
    "tie" to "tying",
    "write" to "writing",
)
