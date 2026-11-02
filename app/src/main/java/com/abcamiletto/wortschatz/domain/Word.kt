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

/** One dictionary entry. Field names match assets/words.json. */
@Serializable
data class Word(
    val id: String,
    val article: Article,
    val noun: String,
    val plural: String? = null,
    val pluralOnly: Boolean = false,
    val accepted: List<Article> = listOf(article),
    val alternatives: List<String> = emptyList(),
    val level: String,
    val region: String = "",
    val gloss: String,
    val category: String,
) {
    val display: String get() = "${article.text} $noun"
}
