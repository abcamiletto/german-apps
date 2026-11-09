package com.abcamiletto.wortschatz

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.abcamiletto.wortschatz.audio.AndroidPronunciationPlayer
import com.abcamiletto.wortschatz.data.Dictionary
import com.abcamiletto.wortschatz.data.HttpSyncApi
import com.abcamiletto.wortschatz.data.ProgressSync
import com.abcamiletto.wortschatz.data.RoomProgressStore
import com.abcamiletto.wortschatz.data.Settings
import com.abcamiletto.wortschatz.data.WordContent
import com.abcamiletto.wortschatz.speech.AndroidSpeechEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
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
            val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) onResult(true) else launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

private class SharedPreferenceSettings(context: Context) : Settings {
    private val preferences = context.getSharedPreferences("wortschatz", Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)
    override fun set(key: String, value: String?) = preferences.edit { putString(key, value) }
}

class WortschatzApp : Application() {
    val container: AppContainer by lazy {
        val sync = ProgressSync(RoomProgressStore(this), SharedPreferenceSettings(this), HttpSyncApi(HttpClient(Android)))
        AppContainer(
            dictionary = Dictionary(AssetContent(this)),
            progress = sync,
            sync = sync,
            player = AndroidPronunciationPlayer(this),
            microphone = RuntimePermission,
            speechFactory = { AndroidSpeechEngine(this) },
        )
    }
}
