package com.abcamiletto.wortschatz.ui

/** "3085" becomes "3,085". */
fun Int.withThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()

private val FOLDED = mapOf(
    'ß' to "ss", 'ä' to "a", 'ö' to "o", 'ü' to "u", 'à' to "a", 'á' to "a", 'â' to "a", 'ç' to "c", 'è' to "e",
    'é' to "e", 'ê' to "e", 'ë' to "e", 'í' to "i", 'î' to "i", 'ï' to "i", 'ñ' to "n", 'ó' to "o", 'ô' to "o",
    'ù' to "u", 'ú' to "u", 'û' to "u",
)

/** Search ignores case and umlauts, so "muller" finds "Müller". */
fun fold(text: String): String = buildString {
    text.lowercase().forEach { append(FOLDED[it] ?: it) }
}
