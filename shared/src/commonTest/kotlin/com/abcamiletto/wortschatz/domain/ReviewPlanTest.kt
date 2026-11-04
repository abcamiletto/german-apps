package com.abcamiletto.wortschatz.domain

import com.abcamiletto.wortschatz.data.Progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DAY = 86_400_000L
private const val NOW = 1_000 * DAY

private fun row(
    id: String,
    stability: Double = 5.0,
    dueIn: Long = 0,
    reviewedAgo: Long = 5 * DAY,
    reviews: Int = 3,
    lapses: Int = 0,
) = Progress(id, stability, 5.0, NOW + dueIn, NOW - reviewedAgo, reviews, lapses)

class ReviewPlanTest {
    @Test fun wordsLosingTheMostByWaitingComeFirst() {
        val fragile = row("fragile", stability = 1.0, reviewedAgo = DAY)
        val stable = row("stable", stability = 30.0, reviewedAgo = 30 * DAY)
        val longGone = row("gone", stability = 1.0, reviewedAgo = 300 * DAY)
        assertEquals(listOf("fragile", "stable", "gone"), ReviewPlan.members(ReviewSet.Due, listOf(longGone, stable, fragile), NOW).map { it.wordId })
    }

    @Test fun aWordThatIsDueOnlyAppearsInDue() {
        val rows = listOf(row("due", lapses = 3, reviews = 1))
        assertEquals(listOf("due"), ReviewPlan.members(ReviewSet.Due, rows, NOW).map { it.wordId })
        listOf(ReviewSet.Tricky, ReviewSet.Soon, ReviewSet.Recent).forEach {
            assertTrue(ReviewPlan.members(it, rows, NOW).isEmpty(), "$it")
        }
    }

    @Test fun trickySortsByHowOftenAWordWasForgotten() {
        val rows = listOf(row("once", dueIn = DAY, lapses = 1), row("thrice", dueIn = DAY, lapses = 3), row("never", dueIn = DAY))
        assertEquals(listOf("thrice", "once"), ReviewPlan.members(ReviewSet.Tricky, rows, NOW).map { it.wordId })
    }

    @Test fun aWordJustPractisedIsNotSuggestedAgain() {
        val rows = listOf(row("now", dueIn = DAY, reviewedAgo = 60_000, lapses = 1, reviews = 1))
        listOf(ReviewSet.Tricky, ReviewSet.Soon, ReviewSet.Recent).forEach {
            assertTrue(ReviewPlan.members(it, rows, NOW).isEmpty(), "$it")
        }
    }

    @Test fun soonCoversTheNextThreeDaysInDueOrder() {
        val rows = listOf(row("later", dueIn = 2 * DAY), row("first", dueIn = DAY), row("far", dueIn = 10 * DAY))
        assertEquals(listOf("first", "later"), ReviewPlan.members(ReviewSet.Soon, rows, NOW).map { it.wordId })
    }

    @Test fun recentCoversWordsLearnedThisWeek() {
        val rows = listOf(
            row("new", dueIn = DAY, reviewedAgo = DAY, reviews = 1),
            row("old", dueIn = DAY, reviewedAgo = 20 * DAY, reviews = 1),
            row("practised", dueIn = DAY, reviewedAgo = DAY, reviews = 9),
        )
        assertEquals(listOf("new"), ReviewPlan.members(ReviewSet.Recent, rows, NOW).map { it.wordId })
    }

    @Test fun aSessionTakesAtMostItsSizeAndSkipsUnknownWords() {
        val words = List(30) { verb("w$it", category = "c$it") }
        val rows = words.map { row(it.id) } + row("removed_word", stability = 0.1)
        val session = ReviewPlan.session(ReviewSet.Due, rows, words, NOW)
        assertEquals(ReviewPlan.SESSION_SIZE, session.size)
        assertTrue(session.all { it in words })
    }

    @Test fun equalUrgencyFavoursTheCommonerWord() {
        val words = listOf(verb("common", category = "a"), verb("rare", category = "b"))
        val session = ReviewPlan.session(ReviewSet.Due, listOf(row(words[1].id), row(words[0].id)), words, NOW)
        assertEquals(words, session)
    }

    @Test fun relatedWordsAreNotAskedBackToBack() {
        val words = listOf(verb("a", category = "food"), verb("b", category = "food"), verb("c", category = "work"))
        val session = ReviewPlan.session(ReviewSet.Due, words.map { row(it.id) }, words, NOW)
        assertEquals(listOf("a", "c", "b"), session.map { it.lemma })
    }

    @Test fun forecastCountsDueWordsPerDay() {
        val rows = listOf(row("a"), row("b", dueIn = -DAY), row("c", dueIn = 1), row("d", dueIn = DAY), row("e", dueIn = DAY + 1), row("f", dueIn = 50 * DAY))
        assertEquals(listOf(2, 2, 1, 0), ReviewPlan.forecast(rows, NOW, days = 4))
    }

    @Test fun forecastIgnoresAFarFutureDate() {
        val farAway = Progress("a", 1.0, 5.0, Long.MAX_VALUE, 0, 1, 0)
        assertEquals(listOf(0, 0), ReviewPlan.forecast(listOf(farAway), NOW, days = 2))
    }

    @Test fun wordsSpelledAlikeWaitForDifferentBatches() {
        val words = listOf(word(Article.Das, "Leben", category = "a"), verb("leben", category = "b"), verb("gehen", category = "c"))
        assertEquals(listOf("Leben", "gehen"), nextToLearn(words, emptySet(), 2).map { it.lemma })
        assertEquals(listOf("leben", "gehen"), nextToLearn(words, setOf(words[0].id), 2).map { it.lemma })
    }

    @Test fun aPartialBatchKeepsItsWordsAndTakesTheNextOnes() {
        val words = listOf("Haus", "Baum", "Tisch").map { word(Article.Der, it) }
        val batch = nextToLearn(words, setOf(words[0].id), 2, partial = listOf(words[2]))
        assertEquals(listOf("Tisch", "Baum"), batch.map { it.lemma })
    }
}
