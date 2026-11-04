package com.abcamiletto.wortschatz

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.abcamiletto.wortschatz.domain.Article
import com.abcamiletto.wortschatz.data.Progress
import com.abcamiletto.wortschatz.data.ProgressSync
import com.abcamiletto.wortschatz.data.Settings
import com.abcamiletto.wortschatz.data.SignInResult
import com.abcamiletto.wortschatz.data.SyncApi
import com.abcamiletto.wortschatz.data.SyncRequest
import com.abcamiletto.wortschatz.data.SyncResponse
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.domain.WordKind
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
    private lateinit var seeded: Set<String>

    @Before
    fun setUp() = runBlocking {
        container.sync = null
        container.progress.clear()
        container.speechFactory = { speech }
        focusOn(WordKind.Noun)
        speech.response = { firstWords.map { it.display } }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /** Learning order mixes every kind of word, so the others are marked as already known and far from due. */
    private fun focusOn(kind: WordKind) = runBlocking {
        val words = container.dictionary.words()
        val others = words.filter { it.kind != kind }
        container.progress.clear()
        container.progress.upsertAll(
            others.map { Progress(it.id, 100.0, 5.0, Long.MAX_VALUE, 0, 1, 0) },
        )
        seeded = others.mapTo(HashSet()) { it.id }
        firstWords = words.filter { it.kind == kind }.take(5)
    }

    private fun restartFocusedOn(kind: WordKind) {
        scenario.close()
        focusOn(kind)
        speech.response = { firstWords.map { it.display } }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun restartWith(rows: List<Progress>) {
        scenario.close()
        runBlocking { container.progress.upsertAll(rows) }
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

    private fun known() = runBlocking { container.progress.all().filter { it.wordId !in seeded } }

    @Test
    fun learningFiveWordsBySpeakingThemFinishesTheSession() {
        skipIntros()
        repeat(firstWords.size) {
            tap("mic")
            waitForText("Correct")
            tap("primary")
        }
        waitForText("5 of 5")
        tap("done")
        compose.onNodeWithTag("forecast").assertIsDisplayed()
        assertEquals(5, known().size)
        assertEquals(setOf(1), known().map { it.reviews }.toSet())
    }

    @Test
    fun aWrongArticleIsCalledOutAndTheWordReturns() {
        skipIntros()
        // Say every word with an article that is wrong for it.
        speech.response = {
            firstWords.map { word -> "${Article.entries.first { it !in word.accepted }.text} ${word.lemma}" }
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
        waitForText("4 of 5")
        assertEquals(1, known().count { it.lapses == 1 })
    }

    @Test
    fun theNounAloneAsksForTheArticle() {
        skipIntros()
        speech.response = { firstWords.map { it.lemma } }
        tap("mic")
        waitForText("Add the article")
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
        waitForText("The answer")
        compose.onNodeWithTag("lemma").assertIsDisplayed()
        assertEquals(1, known().single().lapses)
    }

    @Test
    fun typingWorksWithoutTheMicrophone() {
        skipIntros()
        speech.failure = SpeechFailure.Unavailable
        tap("mic")
        waitForText("Type instead")
        compose.onNodeWithTag("answer").performTextInput("xyz")
        tap("check")
        waitForText("Not that word")
    }

    @Test
    fun aNewBatchIsDiscoveredAndThenGuessedFromTheEnglish() {
        tap("learn")
        compose.onNodeWithTag("discover").assertIsDisplayed()
        compose.onNodeWithTag("guess").assertIsDisplayed()
        compose.onNodeWithTag("lemma").assertIsDisplayed()
        repeat(firstWords.size) { tap("primary") }
        compose.onNodeWithTag("mic").assertIsDisplayed()
        compose.onNodeWithTag("prompt").assertIsDisplayed()
        compose.onNodeWithTag("lemma").assertDoesNotExist()
    }

    @Test
    fun aWordAlreadyKnownIsReplacedByTheNextOne() {
        tap("learn")
        tap("secondary")
        repeat(firstWords.size) { tap("primary") }
        compose.onNodeWithTag("mic").assertIsDisplayed()
        assertEquals(1, known().size)
    }

    private fun speakEveryWordOfALearnSession() {
        skipIntros()
        repeat(firstWords.size) {
            tap("mic")
            waitForText("Correct")
            tap("primary")
        }
        waitForText("5 of 5")
        assertEquals(5, known().size)
    }

    @Test
    fun verbsAreLearnedWithoutAnArticle() {
        restartFocusedOn(WordKind.Verb)
        speakEveryWordOfALearnSession()
    }

    @Test
    fun adjectivesAreLearnedWithoutAnArticle() {
        restartFocusedOn(WordKind.Adjective)
        speakEveryWordOfALearnSession()
    }

    @Test
    fun aVerbQuestionAsksForTheVerbAndSuggestsOnlyVerbs() {
        restartFocusedOn(WordKind.Verb)
        skipIntros()
        waitForText("Say the verb")
        tap("suggest")
        repeat(5) { compose.onNodeWithTag("option$it").assertIsDisplayed() }
        compose.onAllNodes(hasText("der ", substring = true)).assertCountEquals(0)
    }

    @Test
    fun aDueWordIsReviewedFromTheHomeScreen() {
        val word = firstWords.first()
        val now = System.currentTimeMillis()
        restartWith(listOf(Progress(word.id, 3.0, 5.0, now - 1_000, now - 86_400_000, 2, 0)))
        tap("review")
        tap("mic")
        waitForText("Correct")
        assertEquals(3, runBlocking { container.progress.get(word.id)?.reviews })
    }

    @Test
    fun aTrickyWordIsSuggested() {
        val word = firstWords.first()
        val now = System.currentTimeMillis()
        restartWith(listOf(Progress(word.id, 3.0, 5.0, now + 86_400_000, now - 86_400_000, 4, 2)))
        tap("repeatTricky")
        compose.onNodeWithTag("mic").assertIsDisplayed()
    }

    @Test
    fun finishingABatchOffersFiveMore() {
        speakEveryWordOfALearnSession()
        tap("continue")
        compose.onNodeWithTag("sheet").assertIsDisplayed()
    }

    @Test
    fun theAppAsksForThePasswordOnceBeforeAnythingElse() {
        val server = object : SyncApi {
            override suspend fun exchange(token: String?, request: SyncRequest) = SyncResponse(0, emptyList())
            override suspend fun signIn(password: String) =
                if (password == "right") SignInResult.Success("token") else SignInResult.WrongPassword
        }
        val settings = object : Settings {
            val values = HashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun set(key: String, value: String?) {
                if (value == null) values.remove(key) else values[key] = value
            }
        }
        scenario.close()
        container.sync = ProgressSync(container.progress, settings, server)
        scenario = ActivityScenario.launch(MainActivity::class.java)

        compose.onNodeWithTag("password").performTextInput("wrong")
        tap("signIn")
        waitForText("Wrong password")
        compose.onAllNodes(hasTestTag("learn")).assertCountEquals(0)

        compose.onNodeWithTag("password").performTextClearance()
        compose.onNodeWithTag("password").performTextInput("right")
        tap("signIn")
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("learn")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun libraryFindsAVerbFromAConjugatedForm() {
        tap("browse")
        tap("kindVerbs")
        compose.onNodeWithTag("search").performTextInput("fährt")
        waitForText("fahren")
    }

    @Test
    fun libraryFindsAWordFromItsEnglishMeaning() {
        tap("browse")
        compose.onNodeWithTag("search").performTextInput("dog")
        waitForText("Hund")
    }
}
