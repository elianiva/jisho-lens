package com.elianiva.jisholens.domain

/** True for the CJK ideograph blocks; kana, Latin, and punctuation are not kanji. */
fun Char.isKanji(): Boolean = code in 0x3400..0x4DBF ||
    code in 0x4E00..0x9FFF ||
    code in 0xF900..0xFAFF ||
    code in 0x20000..0x3FFFF

fun String.hasKanji(): Boolean = any(Char::isKanji)
