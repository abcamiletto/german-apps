package com.abcamiletto.wortschatz.domain

import kotlin.random.Random

data class Choice(val article: Article, val noun: String) {
    val display: String get() = "${article.text} $noun"
}

/** Builds the five answers offered by the Suggest assist. */
object Suggestions {
    const val COUNT = 5

    /**
     * One option is the correct answer. One repeats the noun with a wrong article, which tests gender directly.
     * The rest are nouns from the same category and level where possible, so they stay plausible for the image.
     */
    fun build(word: Word, all: List<Word>, random: Random = Random.Default): List<Choice> {
        val correct = Choice(word.article, word.noun)
        val wrongArticle = if (word.pluralOnly) {
            null
        } else {
            Article.entries.filter { it !in word.accepted }.randomOrNull(random)?.let { Choice(it, word.noun) }
        }
        val fixed = listOfNotNull(correct, wrongArticle)
        val others = all.filter { it.noun != word.noun }
        val similar = others.filter { it.category == word.category && it.level == word.level }.shuffled(random)
        val sameCategory = others.filter { it.category == word.category }.shuffled(random)
        val anything = others.shuffled(random)
        val distractors = (similar + sameCategory + anything)
            .distinctBy { it.noun }
            .take(COUNT - fixed.size)
            .map { Choice(it.article, it.noun) }
        return (fixed + distractors).shuffled(random)
    }
}
