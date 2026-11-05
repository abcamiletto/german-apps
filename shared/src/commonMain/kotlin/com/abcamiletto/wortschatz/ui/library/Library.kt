package com.abcamiletto.wortschatz.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.data.Progress
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.domain.WordKind
import com.abcamiletto.wortschatz.ui.Corner
import com.abcamiletto.wortschatz.ui.Gap
import com.abcamiletto.wortschatz.ui.LocalWide
import com.abcamiletto.wortschatz.ui.PHONE_COLUMN
import com.abcamiletto.wortschatz.ui.fold
import com.abcamiletto.wortschatz.ui.hand
import com.abcamiletto.wortschatz.ui.theme.Ink
import com.abcamiletto.wortschatz.ui.theme.Slate
import com.abcamiletto.wortschatz.ui.theme.Stage
import com.abcamiletto.wortschatz.ui.theme.Track
import com.abcamiletto.wortschatz.ui.theme.color
import com.abcamiletto.wortschatz.ui.withThousands

private val LEVELS = listOf("A1", "A2", "B1")
private val KINDS = listOf(WordKind.Noun to "Nouns", WordKind.Verb to "Verbs", WordKind.Adjective to "Adjectives", WordKind.Other to "Other")
private val STRENGTH_DAYS = listOf(0.0, 7.0, 30.0)
private val WIDE_CONTENT = 1120.dp
private val CELL_WIDTH = 300.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryRoute(container: AppContainer, onBack: () -> Unit) {
    val words by produceState(emptyList<Word>()) { value = container.dictionary.words() }
    var query by remember { mutableStateOf("") }
    var level by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf<WordKind?>(null) }
    var learnedOnly by remember { mutableStateOf(false) }
    val rows by remember { container.progress.observeAll() }.collectAsState(emptyList())
    val progress = remember(rows) { rows.associateBy { it.wordId } }
    val needle = remember(query) { fold(query.trim()) }
    val shown = remember(words, needle, level, kind, learnedOnly, progress) {
        words
            .filter { (level == null || it.level == level) && (kind == null || it.kind == kind) }
            .filter { !learnedOnly || it.id in progress }
            .filter { needle.isEmpty() || listOf(it.lemma, it.forms, it.gloss).any { text -> fold(text).contains(needle) } }
            .sortedBy { fold(it.lemma) }
    }
    LaunchedEffect(Unit) { /* Keep playback from a previous screen from running on. */ container.player.stop() }
    val wide = LocalWide.current
    val margin = if (wide) Gap.pageWide else Gap.large

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = if (wide) WIDE_CONTENT else PHONE_COLUMN).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = if (wide) margin else Gap.tiny, end = margin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!wide) {
                    IconButton(onClick = onBack, modifier = Modifier.hand().testTag("back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
                    }
                }
                Text(
                    "All words",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).padding(start = if (wide) 0.dp else Gap.tiny),
                )
                Text(shown.size.withThousands(), style = MaterialTheme.typography.labelMedium, color = Slate)
            }
            val search: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("German or English") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Slate) },
                    singleLine = true,
                    shape = Corner.medium,
                    modifier = modifier.testTag("search"),
                )
            }
            val filters: @Composable () -> Unit = {
                Filter("Learned", learnedOnly, "learned") { learnedOnly = !learnedOnly }
                LEVELS.forEach { name -> Filter(name, level == name, "level$name") { level = if (level == name) null else name } }
                KINDS.forEach { (option, name) -> Filter(name, kind == option, "kind$name") { kind = if (kind == option) null else option } }
            }
            if (wide) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = margin),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Gap.large),
                ) {
                    search(Modifier.width(280.dp))
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Gap.small)) { filters() }
                }
            } else {
                search(Modifier.fillMaxWidth().padding(horizontal = margin))
                // One line that scrolls: on a phone the list deserves the height that wrapped filters would take.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = margin),
                    horizontalArrangement = Arrangement.spacedBy(Gap.small),
                ) { filters() }
            }
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Gap.large
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CELL_WIDTH),
                contentPadding = PaddingValues(start = margin - Gap.small, end = margin - Gap.small, top = Gap.small, bottom = bottom),
                horizontalArrangement = Arrangement.spacedBy(Gap.tiny),
                modifier = Modifier.fillMaxSize().testTag("words"),
            ) {
                items(shown, key = { it.id }) { word ->
                    WordRow(word, progress[word.id], container.dictionary.imageUri(word.id)) { container.player.play(word.id) }
                }
            }
        }
    }
}

@Composable
private fun Filter(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = Ink,
            selectedContainerColor = Ink,
            selectedLabelColor = Color.White,
        ),
        modifier = Modifier.hand().testTag(tag),
    )
}

@Composable
private fun WordRow(word: Word, progress: Progress?, imageUri: String, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Corner.medium)
            .hand()
            .clickable(onClick = onPlay)
            .padding(Gap.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(Corner.small).background(Stage).padding(6.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = Gap.medium)) {
            Text(
                buildAnnotatedString {
                    val tone = SpanStyle(color = word.tone.color)
                    if (word.article != null) {
                        withStyle(tone) { append(word.article.text) }
                        append(" ${word.lemma}")
                    } else {
                        withStyle(tone) { append(word.lemma) }
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(word.gloss, style = MaterialTheme.typography.bodyMedium, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (progress != null) Strength(progress.stability)
    }
}

/** How well a learned word is held, drawn as three bars. */
@Composable
private fun Strength(stabilityDays: Double) {
    Row(
        Modifier.padding(end = Gap.tiny).height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        STRENGTH_DAYS.forEachIndexed { index, days ->
            Box(
                Modifier
                    .width(4.dp)
                    .height(6.dp + 5.dp * index)
                    .clip(Corner.small)
                    .background(if (stabilityDays >= days) Ink else Track),
            )
        }
    }
}
