package com.abcamiletto.wortschatz.data

import com.abcamiletto.wortschatz.domain.Fsrs
import com.abcamiletto.wortschatz.domain.Memory
import com.abcamiletto.wortschatz.domain.Rating
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

private const val DAY_MILLIS = 86_400_000L
private const val RELEARN_MILLIS = 10 * 60_000L

@Serializable
data class Progress(
    val wordId: String,
    val stability: Double,
    val difficulty: Double,
    val dueAt: Long,
    val lastReviewAt: Long,
    val reviews: Int,
    val lapses: Int,
)

/** Applies one graded answer to a word's schedule. [previous] is null the first time a word is graded. */
fun schedule(wordId: String, previous: Progress?, rating: Rating, now: Long): Progress {
    val memory = if (previous == null) {
        Fsrs.first(rating)
    } else {
        val elapsedDays = (now - previous.lastReviewAt).toDouble() / DAY_MILLIS
        Fsrs.next(Memory(previous.stability, previous.difficulty), elapsedDays, rating)
    }
    val delay = if (rating == Rating.Again) RELEARN_MILLIS else Fsrs.intervalDays(memory) * DAY_MILLIS
    return Progress(
        wordId = wordId,
        stability = memory.stability,
        difficulty = memory.difficulty,
        dueAt = now + delay,
        lastReviewAt = now,
        reviews = (previous?.reviews ?: 0) + 1,
        lapses = (previous?.lapses ?: 0) + if (rating == Rating.Again) 1 else 0,
    )
}

/** The learner's schedule. Android keeps it in Room, the website in the browser's local storage. */
interface ProgressStore {
    fun observeAll(): Flow<List<Progress>>
    suspend fun all(): List<Progress>
    suspend fun get(wordId: String): Progress?
    suspend fun upsert(progress: Progress)
    suspend fun upsertAll(rows: List<Progress>)
    suspend fun clear()
}
