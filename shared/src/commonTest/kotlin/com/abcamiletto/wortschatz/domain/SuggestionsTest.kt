package com.abcamiletto.wortschatz.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.random.Random

class SuggestionsTest {
    private val pool = listOf("Apfel", "Birne", "Brot", "Käse", "Milch", "Wein", "Tisch", "Stuhl").map {
        word(Article.Der, it, category = if (it == "Tisch" || it == "Stuhl") "home" else "food_drink")
    }

    @Test fun offersFiveDistinctOptionsIncludingTheAnswer() {
        val target = pool.first()
        repeat(50) { seed ->
            val options = Suggestions.build(target, pool, Random(seed))
            assertEquals(5, options.size)
            assertEquals(5, options.toSet().size)
            assertTrue(Choice(Article.Der, "Apfel") in options)
        }
    }

    @Test fun includesTheNounWithAWrongArticle() {
        val options = Suggestions.build(pool.first(), pool, Random(1))
        assertTrue(options.any { it.lemma == "Apfel" && it.article != Article.Der })
    }

    @Test fun neverPresentsAnAcceptedVariantAsWrong() {
        val laptop = word(Article.Der, "Laptop", accepted = listOf(Article.Das, Article.Der))
        repeat(50) { seed ->
            val options = Suggestions.build(laptop, pool, Random(seed))
            assertFalse(options.any { it.lemma == "Laptop" && it.article == Article.Das })
        }
    }

    @Test fun pluralOnlyNounsGetNoWrongArticleVariant() {
        val eltern = word(Article.Die, "Eltern", pluralOnly = true)
        val options = Suggestions.build(eltern, pool, Random(1))
        assertEquals(1, options.count { it.lemma == "Eltern" })
        assertEquals(5, options.size)
    }

    @Test fun prefersTheSameCategory() {
        val options = Suggestions.build(pool.first(), pool, Random(3))
        assertFalse(options.any { it.lemma == "Tisch" || it.lemma == "Stuhl" })
    }

    @Test fun verbsOnlyCompeteWithOtherVerbs() {
        val verbs = listOf("gehen", "laufen", "rennen", "fahren", "fliegen", "kommen").map { verb(it) }
        val mixed = pool + verbs + adjective("groß")
        repeat(50) { seed ->
            val options = Suggestions.build(verbs.first(), mixed, Random(seed))
            assertEquals(5, options.toSet().size)
            assertTrue(options.all { it.article == null && it.lemma in verbs.map(Word::lemma) })
            assertTrue(Choice(null, "gehen") in options)
        }
    }

    @Test fun nounsNeverCompeteWithVerbsOrAdjectives() {
        val mixed = pool + verb("gehen", category = "food_drink") + adjective("groß", category = "food_drink")
        repeat(50) { seed ->
            assertTrue(Suggestions.build(pool.first(), mixed, Random(seed)).all { it.article != null })
        }
    }

    @Test fun aChoiceIsCorrectOnlyForTheRightWordAndArticle() {
        val apfel = pool.first()
        assertTrue(Choice(Article.Der, "Apfel").isCorrectFor(apfel))
        assertFalse(Choice(Article.Die, "Apfel").isCorrectFor(apfel))
        assertTrue(Choice(null, "gehen").isCorrectFor(verb("gehen")))
        assertFalse(Choice(null, "laufen").isCorrectFor(verb("gehen")))
    }
}
