package com.abcamiletto.wortschatz.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.IOException

/** Plays bundled pronunciation clips. A new clip interrupts the one that is playing. */
class AndroidPronunciationPlayer(private val context: Context) : PronunciationPlayer {
    private var player: MediaPlayer? = null

    override fun play(wordId: String) {
        stop()
        try {
            val descriptor = context.assets.openFd("audio/$wordId.ogg")
            val next = MediaPlayer()
            descriptor.use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            next.setOnPreparedListener { it.start() }
            next.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            player = next
            next.prepareAsync()
        } catch (error: IOException) {
            Log.w("PronunciationPlayer", "Cannot play $wordId", error)
        }
    }

    override fun stop() {
        player?.release()
        player = null
    }
}
