package com.abcamiletto.wortschatz.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Article(val text: String) {
    @SerialName("der") Der("der"),
    @SerialName("die") Die("die"),
    @SerialName("das") Das("das");

    companion object {
        fun parse(text: String): Article? = entries.firstOrNull { it.text == text }
    }
}

enum class WordKind {
    @SerialName("noun") Noun,
    @SerialName("verb") Verb,
    @SerialName("adjective") Adjective,
}

/** The colour family of a word: nouns by gender, everything else by part of speech. */
enum class Tone(val label: String) {
    Der("der"),
    Die("die"),
    Das("das"),
    Verb("verb"),
    Adjective("adjective"),
}

/** One dictionary entry. Field names match assets/words.json. Only nouns have an article. */
@Serializable
data class Word(
    val id: String,
    val kind: WordKind = WordKind.Noun,
    val article: Article? = null,
    val lemma: String,
    val plural: String? = null,
    val pluralOnly: Boolean = false,
    val forms: String = "",
    val accepted: List<Article> = listOfNotNull(article),
    val alternatives: List<String> = emptyList(),
    val level: String,
    val region: String = "",
    val gloss: String,
    val category: String,
) {
    val display: String get() = if (article != null) "${article.text} $lemma" else lemma

    val tone: Tone
        get() = when (kind) {
            WordKind.Verb -> Tone.Verb
            WordKind.Adjective -> Tone.Adjective
            WordKind.Noun -> when (article) {
                Article.Die -> Tone.Die
                Article.Das -> Tone.Das
                else -> Tone.Der
            }
        }
}
