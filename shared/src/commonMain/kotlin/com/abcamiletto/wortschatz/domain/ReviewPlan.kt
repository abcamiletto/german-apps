package com.abcamiletto.wortschatz.domain

import com.abcamiletto.wortschatz.data.Progress

private const val DAY_MILLIS = 86_400_000L
private const val HOUR_MILLIS = 3_600_000L
private const val SOON_DAYS = 3
private const val RECENT_DAYS = 7
private const val RECENT_REVIEWS = 2

/** The groups of known words the app suggests repeating. A word that is due only ever appears in [Due]. */
enum class ReviewSet { Due, Tricky, Soon, Recent }

/** Decides what to repeat and in which order. */
object ReviewPlan {
    const val SESSION_SIZE = 20

    private fun recall(row: Progress, now: Long, laterDays: Int = 0): Double =
        Fsrs.retrievability((now - row.lastReviewAt).toDouble() / DAY_MILLIS + laterDays, row.stability)

    /**
     * How much of this memory is lost by waiting one more day. Fragile words fall fastest, so they come first.
     * Long-overdue words have little left to lose and wait, which keeps a backlog from crowding out what can be saved.
     */
    fun urgency(row: Progress, now: Long): Double = recall(row, now) - recall(row, now, laterDays = 1)

    fun members(set: ReviewSet, rows: Collection<Progress>, now: Long): List<Progress> {
        val waiting = rows.filter { it.dueAt > now }
        // A word just practised shouldn't come straight back as a suggestion.
        val rested = waiting.filter { now - it.lastReviewAt >= HOUR_MILLIS }
        return when (set) {
            ReviewSet.Due -> rows.filter { it.dueAt <= now }.sortedByDescending { urgency(it, now) }
            ReviewSet.Tricky -> rested.filter { it.lapses > 0 }
                .sortedWith(compareByDescending<Progress> { it.lapses }.thenBy { recall(it, now) })
            ReviewSet.Soon -> rested.filter { it.dueAt - now <= SOON_DAYS * DAY_MILLIS }.sortedBy { it.dueAt }
            ReviewSet.Recent -> rested
                .filter { it.reviews <= RECENT_REVIEWS && now - it.lastReviewAt <= RECENT_DAYS * DAY_MILLIS }
                .sortedBy { recall(it, now) }
        }
    }

    /** The words of one session. [words] is in frequency order, which settles ties in favour of the commoner word. */
    fun session(set: ReviewSet, rows: Collection<Progress>, words: List<Word>, now: Long): List<Word> {
        val rank = words.withIndex().associate { (index, word) -> word.id to index }
        val byId = words.associateBy { it.id }
        val ranked = rows.filter { it.wordId in rank }.sortedBy { rank[it.wordId] }
        return separated(members(set, ranked, now).take(SESSION_SIZE).map { byId.getValue(it.wordId) })
    }

    /** Words due now, then in each of the next days. */
    fun forecast(rows: Collection<Progress>, now: Long, days: Int = 7): List<Int> {
        val counts = IntArray(days)
        rows.forEach { row ->
            val ahead = row.dueAt - now
            val day = if (ahead <= 0) 0 else ((ahead - 1) / DAY_MILLIS + 1)
            if (day < days) counts[day.toInt()]++
        }
        return counts.toList()
    }

    /** Keeps priority order, except that words which would interfere with each other are not shown back to back. */
    private fun separated(words: List<Word>): List<Word> {
        val remaining = words.toMutableList()
        val result = ArrayList<Word>(words.size)
        while (remaining.isNotEmpty()) {
            val previous = result.lastOrNull()
            val next = remaining.firstOrNull { previous == null || !it.isRelatedTo(previous) } ?: remaining.first()
            remaining.remove(next)
            result.add(next)
        }
        return result
    }
}

/** Two words spelled alike ("das Leben", "leben") or from one topic are easy to mix up when taught or asked in a row. */
fun Word.isRelatedTo(other: Word): Boolean = isSpelledLike(other) || category == other.category

fun Word.isSpelledLike(other: Word): Boolean = lemma.equals(other.lemma, ignoreCase = true)

/** The next words to learn. Words spelled alike wait for separate batches. */
fun nextToLearn(words: List<Word>, known: Set<String>, count: Int): List<Word> {
    val batch = ArrayList<Word>(count)
    for (word in words) {
        if (batch.size == count) break
        if (word.id !in known && batch.none { it.isSpelledLike(word) }) batch.add(word)
    }
    return batch
}
