package com.abcamiletto.wortschatz.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerMatcherTest {
    private val hund = word(Article.Der, "Hund")

    @Test fun correctAnswerIgnoresCaseAndPunctuation() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(hund, listOf("Der Hund.")))

    @Test fun wrongArticleIsReported() =
        assertEquals(Answer.WrongArticle(Article.Die), AnswerMatcher.grade(hund, listOf("die Hund")))

    @Test fun nounWithoutArticleAsksForTheArticle() =
        assertEquals(Answer.MissingArticle, AnswerMatcher.grade(hund, listOf("Hund")))

    @Test fun differentWordIsNotAccepted() =
        assertEquals(Answer.WrongWord("der Hut"), AnswerMatcher.grade(hund, listOf("der Hut")))

    @Test fun shortWordsMustMatchExactly() =
        assertEquals(Answer.WrongWord("der Hand"), AnswerMatcher.grade(hund, listOf("der Hand")))

    @Test fun laterAlternativeCanBeCorrect() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(hund, listOf("Dieter Hund", "der Hund")))

    @Test fun correctAlternativeOutranksWrongArticle() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(hund, listOf("die Hund", "der Hund")))

    @Test fun homophoneOfArticleIsUnderstood() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(word(Article.Das, "Haus"), listOf("dass Haus")))

    @Test fun longWordToleratesOneSlip() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(word(Article.Die, "Möglichkeit"), listOf("die Möglichkeid")))

    @Test fun umlautIsSignificantInShortWords() =
        assertEquals(Answer.WrongWord("der Bar"), AnswerMatcher.grade(word(Article.Der, "Bär"), listOf("der Bar")))

    @Test fun eitherAcceptedGenderIsCorrect() {
        val laptop = word(Article.Der, "Laptop", accepted = listOf(Article.Das, Article.Der))
        assertEquals(Answer.Correct, AnswerMatcher.grade(laptop, listOf("das Laptop")))
    }

    @Test fun spellingVariantIsCorrect() {
        val sauce = word(Article.Die, "Soße", alternatives = listOf("Sauce"))
        assertEquals(Answer.Correct, AnswerMatcher.grade(sauce, listOf("die Sauce")))
    }

    @Test fun multiWordNounMatches() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(word(Article.Das, "Europäische Parlament"), listOf("das europäische Parlament")))

    @Test fun hyphensDoNotMatter() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(word(Article.Die, "E-Mail"), listOf("die E Mail")))

    @Test fun nothingSaid() =
        assertEquals(Answer.Nothing, AnswerMatcher.grade(hund, listOf(" ")))
}
