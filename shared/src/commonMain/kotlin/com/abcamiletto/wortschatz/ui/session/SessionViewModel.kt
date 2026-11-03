package com.abcamiletto.wortschatz.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.audio.PronunciationPlayer
import com.abcamiletto.wortschatz.data.Dictionary
import com.abcamiletto.wortschatz.data.Progress
import com.abcamiletto.wortschatz.data.ProgressStore
import com.abcamiletto.wortschatz.data.schedule
import com.abcamiletto.wortschatz.domain.Answer
import com.abcamiletto.wortschatz.domain.AnswerMatcher
import com.abcamiletto.wortschatz.domain.Article
import com.abcamiletto.wortschatz.domain.Choice
import com.abcamiletto.wortschatz.domain.Fsrs
import com.abcamiletto.wortschatz.domain.Rating
import com.abcamiletto.wortschatz.domain.SessionQueue
import com.abcamiletto.wortschatz.domain.Suggestions
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.domain.WordKind
import com.abcamiletto.wortschatz.speech.SpeechEngine
import com.abcamiletto.wortschatz.speech.SpeechFailure
import com.abcamiletto.wortschatz.speech.SpeechListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

const val LEARN_BATCH = 5
private const val REVIEW_BATCH = 20
private const val DAY_MILLIS = 86_400_000.0

enum class SessionMode { Learn, ReviewDue, ReviewKnown }

sealed interface Outcome {
    data object Correct : Outcome
    data object CorrectWithHelp : Outcome
    data class WrongArticle(val said: Article) : Outcome
    data class WrongChoice(val choice: Choice) : Outcome
    data object Revealed : Outcome
}

sealed interface Phase {
    data object Loading : Phase
    data class Intro(val word: Word, val position: Int, val total: Int) : Phase
    data class Question(
        val word: Word,
        val listening: Boolean = false,
        val level: Float = 0f,
        val heard: String = "",
        val hint: String = askFor(word),
        val options: List<Choice>? = null,
        val typing: Boolean = false,
    ) : Phase
    data class Result(val word: Word, val outcome: Outcome) : Phase
    data class Finished(val answered: Int, val firstTry: Int) : Phase
    data object Empty : Phase
}

data class SessionState(val phase: Phase = Phase.Loading, val completed: Int = 0, val total: Int = 0)

private fun askFor(word: Word): String = when (word.kind) {
    WordKind.Noun -> "Say the article and the noun"
    WordKind.Verb -> "Say the verb"
    WordKind.Adjective -> "Say the adjective"
}

class SessionViewModel(
    private val mode: SessionMode,
    private val dictionary: Dictionary,
    private val progress: ProgressStore,
    private val speech: SpeechEngine,
    private val player: PronunciationPlayer,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val mutable = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutable.asStateFlow()

    private var allWords: List<Word> = emptyList()
    private var intros: List<Word> = emptyList()
    private var introIndex = 0
    private var queue = SessionQueue<Word>(emptyList())
    private val graded = HashSet<String>()
    private var firstTry = 0
    private var slips = 0

    val speechAvailable: Boolean get() = speech.isAvailable

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        allWords = dictionary.words()
        val rows = progress.all().associateBy { it.wordId }
        val byId = allWords.associateBy { it.id }
        val clock = now()
        when (mode) {
            SessionMode.Learn -> intros = allWords.filter { it.id !in rows }.take(LEARN_BATCH)
            SessionMode.ReviewDue -> queue = SessionQueue(
                rows.values.filter { it.dueAt <= clock }.sortedBy { it.dueAt }.take(REVIEW_BATCH).mapNotNull { byId[it.wordId] }.shuffled(),
            )
            SessionMode.ReviewKnown -> queue = SessionQueue(
                rows.values.sortedBy { recall(it, clock) }.take(REVIEW_BATCH).mapNotNull { byId[it.wordId] }.shuffled(),
            )
        }
        if (intros.isNotEmpty()) showIntro() else showQuestion()
    }

    private fun recall(row: Progress, clock: Long): Double =
        Fsrs.retrievability((clock - row.lastReviewAt) / DAY_MILLIS, row.stability)

    private fun showIntro() {
        val word = intros[introIndex]
        setPhase(Phase.Intro(word, introIndex + 1, intros.size))
        player.play(word.id)
    }

    fun introNext() {
        introIndex++
        if (introIndex < intros.size) showIntro() else startTest()
    }

    /** The learner already knows this word, so it joins the review schedule without a test. */
    fun introKnown() {
        val word = intros[introIndex]
        viewModelScope.launch { persist(word, Rating.Easy) }
        intros = intros - word
        if (introIndex < intros.size) showIntro() else startTest()
    }

    private fun startTest() {
        queue = SessionQueue(intros.shuffled())
        showQuestion()
    }

    private fun showQuestion() {
        val word = queue.current
        slips = 0
        when {
            word != null -> setPhase(Phase.Question(word, typing = !speech.isAvailable))
            graded.isEmpty() -> setPhase(Phase.Empty)
            else -> setPhase(Phase.Finished(graded.size, firstTry))
        }
    }

    fun listen() {
        val question = question() ?: return
        player.stop()
        updateQuestion { it.copy(listening = true, heard = "", hint = "Listening…", options = null) }
        speech.start(
            object : SpeechListener {
                override fun onReady() = Unit
                override fun onPartial(text: String) = updateQuestion { it.copy(heard = text) }
                override fun onLevel(level: Float) = updateQuestion { it.copy(level = level) }
                override fun onResults(candidates: List<String>) = submit(question.word, candidates)
                override fun onFailure(failure: SpeechFailure) = showFailure(failure)
            },
        )
    }

    fun stopListening() {
        speech.stop()
    }

    fun micDenied() {
        updateQuestion { it.copy(typing = true, listening = false, hint = "Microphone access is off. Type your answer instead.") }
    }

    fun setTyping(typing: Boolean) {
        speech.destroy()
        updateQuestion { it.copy(typing = typing, listening = false, heard = "", hint = askFor(it.word), options = null) }
    }

    fun submitTyped(text: String) {
        val question = question() ?: return
        submit(question.word, listOf(text))
    }

    private fun submit(word: Word, candidates: List<String>) {
        when (val answer = AnswerMatcher.grade(word, candidates)) {
            Answer.Correct -> finish(word, if (slips == 0) Outcome.Correct else Outcome.CorrectWithHelp)
            is Answer.WrongArticle -> finish(word, Outcome.WrongArticle(answer.said))
            Answer.MissingArticle -> retry("I heard the noun. Now say it with its article.", candidates.first())
            is Answer.WrongWord -> {
                slips++
                retry("That's a different word. Try again or ask for a suggestion.", answer.heard)
            }
            Answer.Nothing -> retry("Nothing came through. Tap the microphone and try again.", "")
        }
    }

    private fun retry(hint: String, heard: String) {
        updateQuestion { it.copy(listening = false, level = 0f, heard = heard, hint = hint) }
    }

    private fun showFailure(failure: SpeechFailure) {
        val hint = when (failure) {
            SpeechFailure.NotUnderstood -> "I didn't catch that. Tap the microphone and try again."
            SpeechFailure.Offline -> "Speech recognition needs a connection here. Type your answer instead."
            SpeechFailure.Unavailable -> "German speech recognition isn't available on this phone. Type your answer instead."
            SpeechFailure.Busy -> "The microphone is busy. Try again in a moment."
            SpeechFailure.NoPermission -> "Microphone access is off. Type your answer instead."
        }
        val type = failure != SpeechFailure.NotUnderstood && failure != SpeechFailure.Busy
        updateQuestion { it.copy(listening = false, level = 0f, hint = hint, typing = type) }
    }

    fun suggest() {
        val question = question() ?: return
        speech.destroy()
        updateQuestion {
            it.copy(
                listening = false,
                typing = false,
                options = question.options ?: Suggestions.build(question.word, allWords),
                hint = "Pick the answer",
            )
        }
    }

    fun choose(choice: Choice) {
        val word = question()?.word ?: return
        finish(word, if (choice.isCorrectFor(word)) Outcome.CorrectWithHelp else Outcome.WrongChoice(choice))
    }

    fun reveal() {
        val word = question()?.word ?: return
        speech.destroy()
        finish(word, Outcome.Revealed)
    }

    private fun finish(word: Word, outcome: Outcome) {
        val rating = when (outcome) {
            Outcome.Correct -> Rating.Good
            Outcome.CorrectWithHelp -> Rating.Hard
            is Outcome.WrongArticle, is Outcome.WrongChoice, Outcome.Revealed -> Rating.Again
        }
        // Only the first answer in a session updates the schedule. Retries just clear the word from the queue.
        if (graded.add(word.id)) {
            if (outcome == Outcome.Correct) firstTry++
            viewModelScope.launch { persist(word, rating) }
        }
        if (rating == Rating.Again) queue.retryLater() else queue.pass()
        setPhase(Phase.Result(word, outcome))
        player.play(word.id)
    }

    private suspend fun persist(word: Word, rating: Rating) {
        progress.upsert(schedule(word.id, progress.get(word.id), rating, now()))
    }

    fun next() {
        player.stop()
        showQuestion()
    }

    fun play(word: Word) = player.play(word.id)

    fun imageUri(word: Word): String = dictionary.imageUri(word.id)

    private fun question(): Phase.Question? = mutable.value.phase as? Phase.Question

    private fun updateQuestion(change: (Phase.Question) -> Phase.Question) {
        mutable.update { state ->
            val phase = state.phase as? Phase.Question ?: return@update state
            state.copy(phase = change(phase))
        }
    }

    private fun setPhase(phase: Phase) {
        mutable.update { it.copy(phase = phase, completed = queue.completed, total = queue.size) }
    }

    override fun onCleared() {
        speech.destroy()
        player.stop()
    }

    companion object {
        fun factory(container: AppContainer, mode: SessionMode): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SessionViewModel(
                    mode,
                    container.dictionary,
                    container.progress,
                    container.speechFactory(),
                    container.player,
                )
            }
        }
    }
}
