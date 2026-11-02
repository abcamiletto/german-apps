package com.abcamiletto.wortschatz

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.abcamiletto.wortschatz.domain.Article
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.speech.SpeechEngine
import com.abcamiletto.wortschatz.speech.SpeechFailure
import com.abcamiletto.wortschatz.speech.SpeechListener
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Replays scripted recogniser output, so the full flow runs without a speech service. */
private class FakeSpeech : SpeechEngine {
    var response: () -> List<String> = { emptyList() }
    var failure: SpeechFailure? = null
    override val isAvailable = true

    override fun start(listener: SpeechListener) {
        val current = failure
        if (current != null) listener.onFailure(current) else listener.onResults(response())
    }

    override fun stop() = Unit
    override fun destroy() = Unit
}

class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val microphone: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val speech = FakeSpeech()
    private val container
        get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WortschatzApp).container
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var firstWords: List<Word>

    @Before
    fun setUp() = runBlocking {
        container.database.progress().clear()
        container.speechFactory = { speech }
        firstWords = container.dictionary.words().take(5)
        speech.response = { firstWords.map { it.display } }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() = scenario.close()

    private fun tap(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun skipIntros() {
        tap("learn")
        repeat(firstWords.size) { tap("primary") }
    }

    private fun known() = runBlocking { container.database.progress().all() }

    @Test
    fun learningFiveWordsBySpeakingThemFinishesTheSession() {
        skipIntros()
        repeat(firstWords.size) {
            tap("mic")
            waitForText("Correct")
            tap("primary")
        }
        waitForText("5 of 5 right the first time")
        tap("done")
        waitForText("5 of ")
        compose.onNodeWithTag("review").assertIsDisplayed()
        assertEquals(5, known().size)
        assertEquals(setOf(1), known().map { it.reviews }.toSet())
    }

    @Test
    fun aWrongArticleIsCalledOutAndTheWordReturns() {
        skipIntros()
        // Say every word with an article that is wrong for it.
        speech.response = {
            firstWords.map { word -> "${Article.entries.first { it !in word.accepted }.text} ${word.noun}" }
        }
        tap("mic")
        waitForText("You said")
        tap("primary")
        // Answer the remaining words correctly: the failed word must come back before the session ends.
        speech.response = { firstWords.map { it.display } }
        repeat(firstWords.size) {
            tap("mic")
            waitForText("Correct")
            tap("primary")
        }
        waitForText("4 of 5 right the first time")
        assertEquals(1, known().count { it.lapses == 1 })
    }

    @Test
    fun theNounAloneAsksForTheArticle() {
        skipIntros()
        speech.response = { firstWords.map { it.noun } }
        tap("mic")
        waitForText("say it with its article")
        compose.onNodeWithTag("mic").assertIsDisplayed()
    }

    @Test
    fun suggestOffersFiveOptions() {
        skipIntros()
        tap("suggest")
        repeat(5) { compose.onNodeWithTag("option$it").assertIsDisplayed() }
    }

    @Test
    fun revealShowsTheAnswerAndTheWordReturns() {
        skipIntros()
        tap("reveal")
        waitForText("Here's the answer")
        compose.onNodeWithTag("noun").assertIsDisplayed()
        assertEquals(1, known().single().lapses)
    }

    @Test
    fun typingWorksWithoutTheMicrophone() {
        skipIntros()
        speech.failure = SpeechFailure.Unavailable
        tap("mic")
        waitForText("Type your answer")
        compose.onNodeWithTag("answer").performTextInput("xyz")
        tap("check")
        waitForText("different word")
    }

    @Test
    fun alreadyKnownWordSkipsTheTest() {
        tap("learn")
        repeat(firstWords.size) { tap("secondary") }
        waitForText("Nothing to practise")
        assertEquals(5, known().size)
    }

    @Test
    fun libraryFindsAWordFromItsEnglishMeaning() {
        tap("browse")
        compose.onNodeWithTag("search").performTextInput("dog")
        waitForText("Hund")
    }
}
