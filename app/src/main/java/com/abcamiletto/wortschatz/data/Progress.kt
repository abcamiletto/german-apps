package com.abcamiletto.wortschatz.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.abcamiletto.wortschatz.domain.Fsrs
import com.abcamiletto.wortschatz.domain.Memory
import com.abcamiletto.wortschatz.domain.Rating
import kotlinx.coroutines.flow.Flow

private const val DAY_MILLIS = 86_400_000L
private const val RELEARN_MILLIS = 10 * 60_000L

@Entity(tableName = "progress")
data class Progress(
    @PrimaryKey val wordId: String,
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

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<Progress>>

    @Query("SELECT * FROM progress")
    suspend fun all(): List<Progress>

    @Query("SELECT * FROM progress WHERE wordId = :wordId")
    suspend fun get(wordId: String): Progress?

    @Upsert
    suspend fun upsert(progress: Progress)

    @Query("DELETE FROM progress")
    suspend fun clear()
}

@Database(entities = [Progress::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wortschatz.db").build()
    }
}
