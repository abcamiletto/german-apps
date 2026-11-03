package com.abcamiletto.wortschatz

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import com.abcamiletto.wortschatz.audio.BrowserPronunciationPlayer
import com.abcamiletto.wortschatz.data.BrowserProgressStore
import com.abcamiletto.wortschatz.data.BrowserSettings
import com.abcamiletto.wortschatz.data.Dictionary
import com.abcamiletto.wortschatz.data.HostedContent
import com.abcamiletto.wortschatz.data.HttpSyncApi
import com.abcamiletto.wortschatz.data.ProgressSync
import com.abcamiletto.wortschatz.speech.BrowserSpeechEngine
import com.abcamiletto.wortschatz.ui.WortschatzRoot
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The browser asks for the microphone itself when recognition starts. */
private object BrowserPrompt : MicrophoneAccess {
    @Composable
    override fun rememberRequest(onResult: (Boolean) -> Unit): () -> Unit = { onResult(true) }
}

/** A session that ended sends this page back to the server, which answers with its sign-in page. */
private fun reloadWhenSignedOut(sync: ProgressSync) {
    MainScope().launch {
        sync.needsSignIn.first { it }
        window.location.reload()
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalBrowserHistoryApi::class)
fun main() {
    // The server only hands out this page after signing in, and the browser's cookie carries that session.
    val sync = ProgressSync(BrowserProgressStore(), BrowserSettings, HttpSyncApi(HttpClient(Js), window.location.origin), usesToken = false)
    reloadWhenSignedOut(sync)
    val container = AppContainer(
        dictionary = Dictionary(HostedContent),
        progress = sync,
        sync = sync,
        player = BrowserPronunciationPlayer(),
        microphone = BrowserPrompt,
        speechFactory = { BrowserSpeechEngine() },
    )
    ComposeViewport(document.body!!) {
        WortschatzRoot(
            container,
            onNavigationReady = { navController ->
                document.getElementById("splash")?.remove()
                // Shows #home or #session in the address bar, rather than the route's full class name.
                navController.bindToBrowserNavigation { entry ->
                    "#" + entry.destination.route.orEmpty().substringBefore('/').substringAfterLast('.')
                        .removeSuffix("Destination").lowercase()
                }
            },
        )
    }
}
