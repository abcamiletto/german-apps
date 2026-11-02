package com.abcamiletto.wortschatz.data

import android.content.res.AssetManager
import com.abcamiletto.wortschatz.domain.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The bundled word list, ordered from most to least useful to learn next. */
class Dictionary(private val assets: AssetManager) {
    private val mutex = Mutex()
    private var cached: List<Word>? = null

    suspend fun words(): List<Word> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val text = assets.open("words.json").bufferedReader().use { it.readText() }
            Json.decodeFromString<List<Word>>(text)
        }.also { cached = it }
    }

    companion object {
        fun imageUri(wordId: String) = "file:///android_asset/images/$wordId.webp"
        fun audioPath(wordId: String) = "audio/$wordId.ogg"
    }
}
