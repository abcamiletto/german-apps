package com.abcamiletto.wortschatz.domain

sealed interface Answer {
    data object Correct : Answer
    data class WrongArticle(val said: Article) : Answer
    data object MissingArticle : Answer
    data class WrongWord(val heard: String) : Answer
    data object Nothing : Answer
}

/** Grades what the learner said or typed against a word. */
object AnswerMatcher {
    // Recognisers often spell a lone article as a homophone.
    private val articleSpellings = mapOf(
        "der" to Article.Der, "dir" to Article.Der, "dehr" to Article.Der, "dea" to Article.Der,
        "die" to Article.Die, "di" to Article.Die, "dee" to Article.Die,
        "das" to Article.Das, "dass" to Article.Das, "daß" to Article.Das,
    )

    /**
     * [candidates] are recogniser alternatives, best first. The most favourable reading wins.
     * Only a [spoken] answer can use a homophone, since saying "dass" and "das" sounds the same but typing them doesn't.
     */
    fun grade(word: Word, candidates: List<String>, spoken: Boolean = false): Answer {
        val answers = candidates.filter { it.isNotBlank() }.map { grade(word, it, spoken) }
        return answers.minByOrNull(::rank) ?: Answer.Nothing
    }

    /** Whether a wrong answer is another German word for the same English, which the prompt can't tell apart. */
    fun isSynonym(word: Word, candidates: List<String>, all: List<Word>): Boolean =
        all.any { it.id != word.id && it.sharesMeaningWith(word) && grade(it, candidates, spoken = false) !is Answer.WrongWord }

    private fun rank(answer: Answer): Int = when (answer) {
        Answer.Correct -> 0
        is Answer.WrongArticle -> 1
        Answer.MissingArticle -> 2
        is Answer.WrongWord -> 3
        Answer.Nothing -> 4
    }

    private fun grade(word: Word, text: String, spoken: Boolean): Answer {
        val tokens = normalize(text).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return Answer.Nothing
        val article = if (word.kind == WordKind.Noun) articleSpellings[tokens.first()] else null
        val rest = (if (article != null) tokens.drop(1) else tokens).joinToString("").withoutSich(word)
        val forms = (word.alternatives + word.lemma + if (spoken) word.heardAs else emptyList()).map { normalize(it).replace(" ", "").withoutSich(word) }
        if (rest.isEmpty() || forms.none { closeEnough(word, rest, it) }) return Answer.WrongWord(text.trim())
        return when {
            word.kind != WordKind.Noun -> Answer.Correct
            article == null -> Answer.MissingArticle
            article in word.accepted -> Answer.Correct
            else -> Answer.WrongArticle(article)
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** "sich freuen" and "freuen" are the same answer, since the picture can't show the reflexive pronoun. */
    private fun String.withoutSich(word: Word): String =
        if (word.kind == WordKind.Verb) removePrefix("sich") else this

    /**
     * Tolerates one recogniser slip in longer words. Short words must match exactly. Verbs and adjectives need a
     * higher bar, because so many of them differ by one letter: wollen/sollen, liegen/legen, kennen/können.
     */
    private fun closeEnough(word: Word, heard: String, expected: String): Boolean {
        val tolerantFrom = if (word.kind == WordKind.Noun) 6 else 9
        return heard == expected || (expected.length >= tolerantFrom && editDistance(heard, expected) <= 1)
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val substitution = previous[j] + if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(previous[j + 1] + 1, current[j] + 1, substitution)
            }
            previous = current
        }
        return previous[b.length]
    }
}
