package com.abcamiletto.wortschatz.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.data.Dictionary
import com.abcamiletto.wortschatz.data.Progress
import com.abcamiletto.wortschatz.data.ProgressStore
import com.abcamiletto.wortschatz.domain.ReviewPlan
import com.abcamiletto.wortschatz.domain.ReviewSet
import com.abcamiletto.wortschatz.domain.Tone
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.domain.nextToLearn
import com.abcamiletto.wortschatz.ui.session.LEARN_BATCH
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock

private const val PREVIEW = 3

data class LevelProgress(val name: String, val known: Int, val total: Int, val byTone: Map<Tone, Int>)

/** A suggested group of words to repeat, with a few of them to picture. */
data class RepeatOption(val set: ReviewSet, val count: Int, val preview: List<String>)

data class HomeState(
    val loaded: Boolean = false,
    val due: RepeatOption = RepeatOption(ReviewSet.Due, 0, emptyList()),
    val suggestions: List<RepeatOption> = emptyList(),
    val newWords: Int = 0,
    val newBatch: Int = LEARN_BATCH,
    val newPreview: List<String> = emptyList(),
    val forecast: List<Int> = emptyList(),
    val levels: List<LevelProgress> = emptyList(),
)

class HomeViewModel(dictionary: Dictionary, progress: ProgressStore, now: () -> Long) : ViewModel() {
    val state: StateFlow<HomeState> = combine(flow { emit(dictionary.words()) }, progress.observeAll()) { words, rows ->
        summarize(words, rows, now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(container.dictionary, container.progress) { Clock.System.now().toEpochMilliseconds() }
            }
        }
    }
}

private fun summarize(words: List<Word>, stored: List<Progress>, now: Long): HomeState {
    // Another device may be on a word list with words this one no longer has.
    val ids = words.mapTo(HashSet()) { it.id }
    val rows = stored.filter { it.wordId in ids }
    val known = rows.mapTo(HashSet()) { it.wordId }
    val levels = words.groupBy { it.level }.entries.sortedBy { it.key }.map { (name, group) ->
        val learned = group.filter { it.id in known }
        LevelProgress(name, learned.size, group.size, learned.groupingBy { it.tone }.eachCount())
    }
    fun option(set: ReviewSet) = RepeatOption(
        set,
        ReviewPlan.members(set, rows, now).size,
        ReviewPlan.session(set, rows, words, now).take(PREVIEW).map { it.id },
    )
    val batch = nextToLearn(words, known, LEARN_BATCH)
    return HomeState(
        loaded = true,
        due = option(ReviewSet.Due),
        suggestions = listOf(ReviewSet.Tricky, ReviewSet.Soon, ReviewSet.Recent).map(::option).filter { it.count > 0 },
        newWords = words.size - known.size,
        newBatch = batch.size,
        newPreview = batch.take(PREVIEW).map { it.id },
        forecast = ReviewPlan.forecast(rows, now),
        levels = levels,
    )
}
