package com.abcamiletto.wortschatz.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

enum class Rating(val grade: Int) { Again(1), Hard(2), Good(3), Easy(4) }

/** Memory state of one word, as modelled by FSRS. */
data class Memory(val stability: Double, val difficulty: Double)

/** The FSRS spaced-repetition scheduler with its published default parameters. */
object Fsrs {
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private const val MIN_DIFFICULTY = 1.0
    private const val MAX_DIFFICULTY = 10.0
    private const val MAX_INTERVAL_DAYS = 365L

    private val w = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
    )

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1 + FACTOR * max(elapsedDays, 0.0) / stability).pow(DECAY)

    fun first(rating: Rating): Memory =
        Memory(stability = w[rating.grade - 1], difficulty = initialDifficulty(rating))

    fun next(memory: Memory, elapsedDays: Double, rating: Rating): Memory {
        val recall = retrievability(elapsedDays, memory.stability)
        val stability = if (rating == Rating.Again) {
            forgetStability(memory, recall)
        } else {
            recallStability(memory, recall, rating)
        }
        return Memory(stability = stability, difficulty = nextDifficulty(memory.difficulty, rating))
    }

    /** Days until recall probability falls to 90%, which for FSRS equals the stability. */
    fun intervalDays(memory: Memory): Long =
        memory.stability.roundToLong().coerceIn(1, MAX_INTERVAL_DAYS)

    private fun initialDifficulty(rating: Rating): Double =
        (w[4] - exp(w[5] * (rating.grade - 1)) + 1).coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -w[6] * (rating.grade - 3)
        val damped = difficulty + delta * (10 - difficulty) / 9
        val reverted = w[7] * initialDifficulty(Rating.Easy) + (1 - w[7]) * damped
        return reverted.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    private fun recallStability(memory: Memory, recall: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.Hard) w[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) w[16] else 1.0
        val growth = exp(w[8]) * (11 - memory.difficulty) * memory.stability.pow(-w[9]) *
            (exp(w[10] * (1 - recall)) - 1) * hardPenalty * easyBonus
        return memory.stability * (growth + 1)
    }

    private fun forgetStability(memory: Memory, recall: Double): Double {
        val stability = w[11] * memory.difficulty.pow(-w[12]) *
            ((memory.stability + 1).pow(w[13]) - 1) * exp(w[14] * (1 - recall))
        return min(stability, memory.stability)
    }
}
