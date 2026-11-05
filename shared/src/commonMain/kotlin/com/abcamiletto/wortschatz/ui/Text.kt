package com.abcamiletto.wortschatz.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

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

/** "Ich **gehe** heim." with the marked part in bold. */
fun marked(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

private val ARTICLE_OR_TO = Regex("\\b(the|to) ")

/** When an English prompt wraps, "the" and "to" stay on the line of the word they belong to. */
fun keepWithItsWord(gloss: String): String = gloss.replace(ARTICLE_OR_TO) { it.value.replace(' ', ' ') }
