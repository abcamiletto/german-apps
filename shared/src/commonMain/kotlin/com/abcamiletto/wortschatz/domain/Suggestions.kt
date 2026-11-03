package com.abcamiletto.wortschatz.domain

import kotlin.random.Random

data class Choice(val article: Article?, val lemma: String) {
    val display: String get() = if (article != null) "${article.text} $lemma" else lemma

    fun isCorrectFor(word: Word): Boolean =
        lemma == word.lemma && (word.article == null || article in word.accepted)
}

/** Builds the five answers offered by the Suggest assist. */
object Suggestions {
    const val COUNT = 5

    /**
     * One option is the correct answer. For a noun, one repeats it with a wrong article, which tests gender directly.
     * The rest are words of the same kind, from the same category and level where possible, so they stay plausible.
     */
    fun build(word: Word, all: List<Word>, random: Random = Random.Default): List<Choice> {
        val correct = Choice(word.article, word.lemma)
        val wrongArticle = if (word.kind != WordKind.Noun || word.pluralOnly) {
            null
        } else {
            Article.entries.filter { it !in word.accepted }.randomOrNull(random)?.let { Choice(it, word.lemma) }
        }
        val fixed = listOfNotNull(correct, wrongArticle)
        val others = all.filter { it.kind == word.kind && it.lemma != word.lemma }
        val similar = others.filter { it.category == word.category && it.level == word.level }.shuffled(random)
        val sameCategory = others.filter { it.category == word.category }.shuffled(random)
        val anything = others.shuffled(random)
        val distractors = (similar + sameCategory + anything)
            .distinctBy { it.lemma }
            .take(COUNT - fixed.size)
            .map { Choice(it.article, it.lemma) }
        return (fixed + distractors).shuffled(random)
    }
}
