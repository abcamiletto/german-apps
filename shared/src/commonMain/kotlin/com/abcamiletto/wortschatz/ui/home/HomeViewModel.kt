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
import com.abcamiletto.wortschatz.domain.Tone
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.ui.session.LEARN_BATCH
import com.abcamiletto.wortschatz.ui.withThousands
import com.abcamiletto.wortschatz.ui.session.SessionMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock

data class LevelProgress(val name: String, val known: Int, val total: Int, val byTone: Map<Tone, Int>)

data class HomeState(
    val totalWords: Int = 0,
    val newWords: Int = 0,
    val dueWords: Int = 0,
    val knownWords: Int = 0,
    val nextLevel: String = "",
    val levels: List<LevelProgress> = emptyList(),
) {
    val reviewMode: SessionMode?
        get() = when {
            dueWords > 0 -> SessionMode.ReviewDue
            knownWords > 0 -> SessionMode.ReviewKnown
            else -> null
        }
    val headline: String
        get() = when {
            totalWords == 0 -> " "
            knownWords == 0 -> "Learn German nouns, verbs and adjectives, up to B1."
            else -> "${knownWords.withThousands()} of ${totalWords.withThousands()} words so far."
        }
    val reviewDetail: String
        get() = if (dueWords > 0) {
            if (dueWords == 1) "1 word is due" else "$dueWords words are due"
        } else {
            "Nothing is due. Practise the words you find hardest."
        }
    val learnDetail: String get() = "The next $LEARN_BATCH words from $nextLevel"
}

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

private fun summarize(words: List<Word>, rows: List<Progress>, now: Long): HomeState {
    val known = rows.mapTo(HashSet()) { it.wordId }
    val levels = words.groupBy { it.level }.entries.sortedBy { it.key }.map { (name, group) ->
        val learned = group.filter { it.id in known }
        LevelProgress(name, learned.size, group.size, learned.groupingBy { it.tone }.eachCount())
    }
    return HomeState(
        totalWords = words.size,
        newWords = words.size - known.size,
        dueWords = rows.count { it.dueAt <= now },
        knownWords = known.size,
        nextLevel = words.firstOrNull { it.id !in known }?.level.orEmpty(),
        levels = levels,
    )
}
