package com.elianiva.jisholens.domain

import com.elianiva.jisholens.dictionary.Kana

/** One pitch accent of a word, as Yomitan's `pitch` term metadata stores it. */
data class PitchAccent(
    /** The reading the accent applies to, which can differ from the headword. */
    val reading: String,
    /** The mora after which the pitch drops; 0 means there is no drop (heiban). */
    val position: Int,
    val tags: List<String> = emptyList(),
)

/** The four accent patterns, named the way Japanese dictionaries name them. */
enum class PitchShape {
    /** No downstep; the particle after the word stays high. */
    Heiban,

    /** The first mora is high and the second drops. */
    Atamadaka,

    /** The drop is inside the word. */
    Nakadaka,

    /** The last mora is high and the following particle drops. */
    Odaka,
}

/**
 * Splits a reading into morae. A small kana (ゃ, ゅ, ょ, ぁ, ...) belongs to the
 * mora before it; ん, っ, and ー each count as one mora of their own.
 */
fun morae(reading: String): List<String> {
    val hiragana = Kana.toHiragana(reading)
    val result = ArrayList<String>(hiragana.length)
    for (character in hiragana) {
        val previous = result.lastOrNull()
        if (previous != null && character in SMALL_KANA) {
            result[result.lastIndex] = previous + character
        } else {
            result.add(character.toString())
        }
    }
    return result
}

/** How the accent sits inside a word of [moraCount] morae. */
fun PitchAccent.shape(moraCount: Int): PitchShape = when {
    position <= 0 -> PitchShape.Heiban
    position >= moraCount -> PitchShape.Odaka
    position == 1 -> PitchShape.Atamadaka
    else -> PitchShape.Nakadaka
}

/** True when mora [index] (counted from 1) is pronounced high. */
fun PitchAccent.isHigh(index: Int, moraCount: Int): Boolean = when {
    position <= 0 -> index >= 2
    position == 1 -> index == 1
    else -> index in 2..position.coerceAtMost(moraCount)
}

/** The pattern's name and its position, as a reader would say them: `heiban [0]`. */
fun PitchAccent.label(moraCount: Int): String {
    val name = when (shape(moraCount)) {
        PitchShape.Heiban -> "heiban"
        PitchShape.Atamadaka -> "atamadaka"
        PitchShape.Nakadaka -> "nakadaka"
        PitchShape.Odaka -> "odaka"
    }
    return "$name [$position]"
}

private const val SMALL_KANA = "ゃゅょぁぃぅぇぉゎャュョァィゥェォヮ"
