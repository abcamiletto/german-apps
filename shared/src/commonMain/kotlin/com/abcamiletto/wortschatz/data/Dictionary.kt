package com.abcamiletto.wortschatz.data

import com.abcamiletto.wortschatz.domain.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Where a platform keeps the files in /content. */
interface WordContent {
    suspend fun wordsJson(): String
    fun imageUri(wordId: String): String
}

/** The bundled word list, ordered from most to least useful to learn next. */
class Dictionary(private val content: WordContent) {
    private val mutex = Mutex()
    private var cached: List<Word>? = null

    suspend fun words(): List<Word> = mutex.withLock {
        cached ?: withContext(Dispatchers.Default) {
            Json.decodeFromString<List<Word>>(content.wordsJson())
        }.also { cached = it }
    }

    fun imageUri(wordId: String): String = content.imageUri(wordId)
}
