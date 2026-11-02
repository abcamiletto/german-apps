package com.abcamiletto.wortschatz.domain

fun word(
    article: Article,
    noun: String,
    category: String = "objects_tools",
    level: String = "A1",
    accepted: List<Article> = listOf(article),
    alternatives: List<String> = emptyList(),
    pluralOnly: Boolean = false,
) = Word(
    id = noun.lowercase(),
    article = article,
    noun = noun,
    accepted = accepted,
    alternatives = alternatives,
    level = level,
    gloss = "the $noun",
    category = category,
    pluralOnly = pluralOnly,
)
