package com.abcamiletto.wortschatz.domain

fun word(
    article: Article,
    lemma: String,
    category: String = "objects_tools",
    level: String = "A1",
    accepted: List<Article> = listOf(article),
    alternatives: List<String> = emptyList(),
    pluralOnly: Boolean = false,
) = Word(
    id = lemma.lowercase(),
    article = article,
    lemma = lemma,
    accepted = accepted,
    alternatives = alternatives,
    level = level,
    gloss = "the $lemma",
    category = category,
    pluralOnly = pluralOnly,
)

fun verb(lemma: String, category: String = "movement", level: String = "A1") = Word(
    id = "verb_$lemma",
    kind = WordKind.Verb,
    lemma = lemma,
    level = level,
    gloss = "to $lemma",
    category = category,
)

fun adjective(lemma: String, category: String = "size_shape", level: String = "A1") = Word(
    id = "adj_$lemma",
    kind = WordKind.Adjective,
    lemma = lemma,
    level = level,
    gloss = lemma,
    category = category,
)
