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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val wordId: String,
    val stability: Double,
    val difficulty: Double,
    val dueAt: Long,
    val lastReviewAt: Long,
    val reviews: Int,
    val lapses: Int,
)

private fun ProgressEntity.toProgress() = Progress(wordId, stability, difficulty, dueAt, lastReviewAt, reviews, lapses)

private fun Progress.toEntity() = ProgressEntity(wordId, stability, difficulty, dueAt, lastReviewAt, reviews, lapses)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress")
    suspend fun all(): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE wordId = :wordId")
    suspend fun get(wordId: String): ProgressEntity?

    @Upsert
    suspend fun upsert(rows: List<ProgressEntity>)

    @Query("DELETE FROM progress")
    suspend fun clear()
}

@Database(entities = [ProgressEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao
}

class RoomProgressStore(context: Context) : ProgressStore {
    private val dao = Room.databaseBuilder(context, AppDatabase::class.java, "wortschatz.db").build().progress()

    override fun observeAll(): Flow<List<Progress>> = dao.observeAll().map { rows -> rows.map { it.toProgress() } }
    override suspend fun all(): List<Progress> = dao.all().map { it.toProgress() }
    override suspend fun get(wordId: String): Progress? = dao.get(wordId)?.toProgress()
    override suspend fun upsert(progress: Progress) = dao.upsert(listOf(progress.toEntity()))
    override suspend fun upsertAll(rows: List<Progress>) = dao.upsert(rows.map { it.toEntity() })
    override suspend fun clear() = dao.clear()
}
