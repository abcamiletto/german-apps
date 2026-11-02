package com.abcamiletto.wortschatz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
        assertTrue(options.any { it.noun == "Apfel" && it.article != Article.Der })
    }

    @Test fun neverPresentsAnAcceptedVariantAsWrong() {
        val laptop = word(Article.Der, "Laptop", accepted = listOf(Article.Das, Article.Der))
        repeat(50) { seed ->
            val options = Suggestions.build(laptop, pool, Random(seed))
            assertFalse(options.any { it.noun == "Laptop" && it.article == Article.Das })
        }
    }

    @Test fun pluralOnlyNounsGetNoWrongArticleVariant() {
        val eltern = word(Article.Die, "Eltern", pluralOnly = true)
        val options = Suggestions.build(eltern, pool, Random(1))
        assertEquals(1, options.count { it.noun == "Eltern" })
        assertEquals(5, options.size)
    }

    @Test fun prefersTheSameCategory() {
        val options = Suggestions.build(pool.first(), pool, Random(3))
        assertFalse(options.any { it.noun == "Tisch" || it.noun == "Stuhl" })
    }
}
