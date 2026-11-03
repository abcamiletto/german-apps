package com.abcamiletto.wortschatz.domain

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class FsrsTest {
    @Test fun betterFirstAnswerGivesHigherStability() {
        val stabilities = Rating.entries.map { Fsrs.first(it).stability }
        assertEquals(stabilities.sorted(), stabilities)
    }

    @Test fun retrievabilityIsNinetyPercentAtStability() =
        assertEquals(0.9, Fsrs.retrievability(10.0, 10.0), 0.001)

    @Test fun retrievabilityFallsOverTime() =
        assertTrue(Fsrs.retrievability(30.0, 5.0) < Fsrs.retrievability(1.0, 5.0))

    @Test fun rememberingGrowsStabilityAndForgettingShrinksIt() {
        val memory = Memory(stability = 10.0, difficulty = 5.0)
        assertTrue(Fsrs.next(memory, 10.0, Rating.Good).stability > 10.0)
        assertTrue(Fsrs.next(memory, 10.0, Rating.Again).stability < 10.0)
    }

    @Test fun easierRatingsScheduleFurtherAhead() {
        val memory = Memory(stability = 10.0, difficulty = 5.0)
        val intervals = listOf(Rating.Hard, Rating.Good, Rating.Easy).map { Fsrs.next(memory, 10.0, it).stability }
        assertEquals(intervals.sorted(), intervals)
    }

    @Test fun failingRaisesDifficulty() {
        val memory = Memory(stability = 10.0, difficulty = 5.0)
        assertTrue(Fsrs.next(memory, 10.0, Rating.Again).difficulty > 5.0)
    }

    @Test fun intervalIsAtLeastOneDayAndAtMostOneYear() {
        assertEquals(1, Fsrs.intervalDays(Memory(0.1, 5.0)))
        assertEquals(365, Fsrs.intervalDays(Memory(5000.0, 5.0)))
    }

    @Test fun repeatedSuccessKeepsExtendingTheInterval() {
        var memory = Fsrs.first(Rating.Good)
        val intervals = (1..6).map {
            memory = Fsrs.next(memory, Fsrs.intervalDays(memory).toDouble(), Rating.Good)
            Fsrs.intervalDays(memory)
        }
        assertEquals(intervals.sorted(), intervals)
        assertTrue(intervals.last() > intervals.first())
    }
}
