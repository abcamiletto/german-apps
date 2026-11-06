package com.abcamiletto.wortschatz.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.audio.PronunciationPlayer
import com.abcamiletto.wortschatz.data.Dictionary
import com.abcamiletto.wortschatz.data.ProgressStore
import com.abcamiletto.wortschatz.data.schedule
import com.abcamiletto.wortschatz.domain.Answer
import com.abcamiletto.wortschatz.domain.AnswerMatcher
import com.abcamiletto.wortschatz.domain.Article
import com.abcamiletto.wortschatz.domain.Choice
import com.abcamiletto.wortschatz.domain.Rating
import com.abcamiletto.wortschatz.domain.ReviewPlan
import com.abcamiletto.wortschatz.domain.ReviewSet
import com.abcamiletto.wortschatz.domain.SessionQueue
import com.abcamiletto.wortschatz.domain.Suggestions
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.domain.WordKind
import com.abcamiletto.wortschatz.domain.nextToLearn
import com.abcamiletto.wortschatz.speech.SpeechEngine
import com.abcamiletto.wortschatz.speech.SpeechFailure
import com.abcamiletto.wortschatz.speech.SpeechListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.time.Clock

const val LEARN_BATCH = 5

enum class SessionMode(val set: ReviewSet?) {
    Learn(null),
    Due(ReviewSet.Due),
    Tricky(ReviewSet.Tricky),
    Soon(ReviewSet.Soon),
    Recent(ReviewSet.Recent),
}

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
    data class Finished(val answered: Int, val firstTry: Int, val canContinue: Boolean) : Phase
}

data class SessionState(val phase: Phase = Phase.Loading, val completed: Int = 0, val total: Int = 0)

private fun askFor(word: Word): String = when (word.kind) {
    WordKind.Noun -> "Say it with the article"
    WordKind.Verb -> "Say the verb"
    WordKind.Adjective -> "Say the adjective"
    WordKind.Other -> "Say it in German"
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
    private var known = HashSet<String>()
    private var intros: List<Word> = emptyList()
    private var introIndex = 0
    private var queue = SessionQueue<Word>(emptyList())
    private val graded = HashSet<String>()
    private var firstTry = 0
    private var slips = 0
    private var saves = emptyList<Job>()
    private var prefersTyping = false

    val speechAvailable: Boolean get() = speech.isAvailable

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        allWords = dictionary.words()
        val rows = progress.all()
        val set = mode.set
        if (set == null) {
            known = rows.mapTo(HashSet()) { it.wordId }
            intros = nextToLearn(allWords, known, LEARN_BATCH)
        } else {
            queue = SessionQueue(ReviewPlan.session(set, rows, allWords, now()))
        }
        if (intros.isNotEmpty()) showIntro() else showQuestion()
    }

    /** Starts another session in the same mode, straight from the end of this one. */
    fun continueSession() {
        intros = emptyList()
        introIndex = 0
        graded.clear()
        firstTry = 0
        saves = emptyList()
        queue = SessionQueue(emptyList())
        setPhase(Phase.Loading)
        viewModelScope.launch { load() }
    }

    private suspend fun moreToDo(): Boolean {
        val rows = progress.all()
        val set = mode.set
        return if (set == null) {
            val known = rows.mapTo(HashSet()) { it.wordId }
            allWords.any { it.id !in known }
        } else {
            ReviewPlan.session(set, rows, allWords, now()).isNotEmpty()
        }
    }

    private fun showIntro() {
        val word = intros[introIndex]
        setPhase(Phase.Intro(word, introIndex + 1, intros.size))
        player.play(word.id)
    }

    fun introNext() {
        introIndex++
        if (introIndex < intros.size) showIntro() else startTest()
    }

    /** The learner already knows this word, so it joins the review schedule and the next new word takes its place. */
    fun introKnown() {
        val word = intros[introIndex]
        save(word, Rating.Easy)
        known.add(word.id)
        intros = nextToLearn(allWords, known, LEARN_BATCH, partial = intros - word)
        if (introIndex < intros.size) showIntro() else startTest()
    }

    private fun startTest() {
        queue = SessionQueue(intros.shuffled())
        showQuestion()
    }

    private fun showQuestion() {
        val word = queue.current
        slips = 0
        if (word != null) {
            setPhase(Phase.Question(word, typing = prefersTyping || !speech.isAvailable))
            return
        }
        viewModelScope.launch {
            saves.joinAll()
            setPhase(Phase.Finished(graded.size, firstTry, moreToDo()))
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
                override fun onResults(candidates: List<String>) = submit(question.word, candidates, spoken = true)
                override fun onFailure(failure: SpeechFailure) = showFailure(failure)
            },
        )
    }

    fun stopListening() {
        speech.stop()
    }

    fun micDenied() {
        prefersTyping = true
        updateQuestion { it.copy(typing = true, listening = false, hint = "Microphone is off. Type instead.") }
    }

    fun setTyping(typing: Boolean) {
        speech.cancel()
        prefersTyping = typing
        updateQuestion { it.copy(typing = typing, listening = false, heard = "", hint = askFor(it.word), options = null) }
    }

    fun submitTyped(text: String) {
        val question = question() ?: return
        submit(question.word, listOf(text), spoken = false)
    }

    private fun submit(word: Word, candidates: List<String>, spoken: Boolean) {
        when (val answer = AnswerMatcher.grade(word, candidates, spoken)) {
            Answer.Correct -> finish(word, if (slips == 0) Outcome.Correct else Outcome.CorrectWithHelp)
            is Answer.WrongArticle -> finish(word, Outcome.WrongArticle(answer.said))
            Answer.MissingArticle -> retry("Add the article", candidates.first())
            is Answer.WrongWord -> if (AnswerMatcher.isSynonym(word, candidates, allWords)) {
                retry("Right meaning, another word", answer.heard)
            } else {
                slips++
                retry("Not that word. Try again.", answer.heard)
            }
            Answer.Nothing -> retry("Didn't catch that. Try again.", "")
        }
    }

    private fun retry(hint: String, heard: String) {
        updateQuestion { it.copy(listening = false, level = 0f, heard = heard, hint = hint) }
    }

    private fun showFailure(failure: SpeechFailure) {
        val hint = when (failure) {
            SpeechFailure.NotUnderstood -> "Didn't catch that. Try again."
            SpeechFailure.Offline -> "Speech needs a connection. Type instead."
            SpeechFailure.Unavailable -> "No German speech here. Type instead."
            SpeechFailure.Busy -> "Microphone busy. Try again."
            SpeechFailure.NoPermission -> "Microphone is off. Type instead."
        }
        val type = failure != SpeechFailure.NotUnderstood && failure != SpeechFailure.Busy
        if (type) prefersTyping = true
        updateQuestion { it.copy(listening = false, level = 0f, hint = hint, typing = type) }
    }

    fun suggest() {
        val question = question() ?: return
        speech.cancel()
        updateQuestion {
            it.copy(
                listening = false,
                typing = false,
                options = question.options ?: Suggestions.build(question.word, allWords),
                hint = "Pick one",
            )
        }
    }

    fun choose(choice: Choice) {
        val word = question()?.word ?: return
        finish(word, if (choice.isCorrectFor(word)) Outcome.CorrectWithHelp else Outcome.WrongChoice(choice))
    }

    fun reveal() {
        val word = question()?.word ?: return
        speech.cancel()
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
            save(word, rating)
        }
        if (rating == Rating.Again) queue.retryLater() else queue.pass()
        setPhase(Phase.Result(word, outcome))
        player.play(word.id)
    }

    private fun save(word: Word, rating: Rating) {
        saves = saves + viewModelScope.launch {
            progress.upsert(schedule(word.id, progress.get(word.id), rating, now()))
        }
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
