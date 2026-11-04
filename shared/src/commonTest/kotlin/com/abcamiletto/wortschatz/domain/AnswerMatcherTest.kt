package com.abcamiletto.wortschatz.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

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

    @Test fun verbNeedsNoArticle() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(verb("fahren"), listOf("Fahren.")))

    @Test fun adjectiveNeedsNoArticle() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(adjective("groß"), listOf("groß")))

    @Test fun anArticleBeforeAVerbIsNotTheVerb() =
        assertEquals(Answer.WrongWord("das fahren"), AnswerMatcher.grade(verb("fahren"), listOf("das fahren")))

    @Test fun reflexiveVerbAcceptedWithOrWithoutSich() {
        val freuen = verb("sich freuen")
        assertEquals(Answer.Correct, AnswerMatcher.grade(freuen, listOf("sich freuen")))
        assertEquals(Answer.Correct, AnswerMatcher.grade(freuen, listOf("freuen")))
        assertEquals(Answer.Correct, AnswerMatcher.grade(verb("waschen"), listOf("sich waschen")))
    }

    @Test fun verbsThatDifferByOneLetterAreNotConfused() {
        assertEquals(Answer.WrongWord("sollen"), AnswerMatcher.grade(verb("wollen"), listOf("sollen")))
        assertEquals(Answer.WrongWord("legen"), AnswerMatcher.grade(verb("liegen"), listOf("legen")))
        assertEquals(Answer.WrongWord("können"), AnswerMatcher.grade(verb("kennen"), listOf("können")))
    }

    @Test fun longVerbStillToleratesOneSlip() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(verb("verschieben"), listOf("verschiben")))

    @Test fun multiWordVerbMatches() =
        assertEquals(Answer.Correct, AnswerMatcher.grade(verb("spazieren gehen"), listOf("Spazieren gehen")))

    @Test fun separatedSpellingOfAnAlternativeMatches() {
        val wehtun = Word(id = "w", kind = WordKind.Verb, lemma = "wehtun", alternatives = listOf("weh tun"), level = "A1", gloss = "to hurt", category = "c")
        assertEquals(Answer.Correct, AnswerMatcher.grade(wehtun, listOf("weh tun")))
    }

    @Test fun anotherWordForTheSameEnglishIsASynonym() {
        val zahlen = verb("zahlen").copy(gloss = "to pay")
        val bezahlen = verb("bezahlen").copy(gloss = "to pay, to settle")
        val gehen = verb("gehen").copy(gloss = "to walk")
        val all = listOf(zahlen, bezahlen, gehen)
        assertTrue(AnswerMatcher.isSynonym(zahlen, listOf("bezahlen"), all))
        assertFalse(AnswerMatcher.isSynonym(zahlen, listOf("gehen"), all))
    }
}
