package com.abcamiletto.wortschatz

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.abcamiletto.wortschatz.audio.AndroidPronunciationPlayer
import com.abcamiletto.wortschatz.data.Dictionary
import com.abcamiletto.wortschatz.data.RoomProgressStore
import com.abcamiletto.wortschatz.data.WordContent
import com.abcamiletto.wortschatz.speech.AndroidSpeechEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AssetContent(private val context: Context) : WordContent {
    override suspend fun wordsJson(): String = withContext(Dispatchers.IO) {
        context.assets.open("words.json").bufferedReader().use { it.readText() }
    }

    override fun imageUri(wordId: String) = "file:///android_asset/images/$wordId.webp"
}

private object RuntimePermission : MicrophoneAccess {
    @Composable
    override fun rememberRequest(onResult: (Boolean) -> Unit): () -> Unit {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
        return {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) onResult(true) else launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

class WortschatzApp : Application() {
    val container: AppContainer by lazy {
        AppContainer(
            dictionary = Dictionary(AssetContent(this)),
            progress = RoomProgressStore(this),
            player = AndroidPronunciationPlayer(this),
            microphone = RuntimePermission,
            speechFactory = { AndroidSpeechEngine(this) },
        )
    }
}
