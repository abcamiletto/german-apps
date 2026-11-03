package com.abcamiletto.wortschatz.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.domain.WordKind
import com.abcamiletto.wortschatz.ui.fold
import com.abcamiletto.wortschatz.ui.theme.Ink
import com.abcamiletto.wortschatz.ui.theme.Slate
import com.abcamiletto.wortschatz.ui.theme.Stage
import com.abcamiletto.wortschatz.ui.theme.color

private val LEVELS = listOf("A1", "A2", "B1")
private val KINDS = listOf(WordKind.Noun to "Nouns", WordKind.Verb to "Verbs", WordKind.Adjective to "Adjectives")

@Composable
fun LibraryRoute(container: AppContainer, onBack: () -> Unit) {
    val words by produceState(emptyList<Word>()) { value = container.dictionary.words() }
    var query by remember { mutableStateOf("") }
    var level by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf<WordKind?>(null) }
    val needle = remember(query) { fold(query.trim()) }
    val shown = remember(words, needle, level, kind) {
        words
            .filter { (level == null || it.level == level) && (kind == null || it.kind == kind) }
            .filter { needle.isEmpty() || listOf(it.lemma, it.forms, it.gloss).any { text -> fold(text).contains(needle) } }
            .sortedBy { fold(it.lemma) }
    }
    LaunchedEffect(Unit) { /* Keep playback from a previous screen from running on. */ container.player.stop() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text("All words", style = MaterialTheme.typography.headlineSmall)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search German or English") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Slate) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search"),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LEVELS.forEach { name ->
                Filter(name, level == name, "level$name") { level = if (level == name) null else name }
            }
            KINDS.forEach { (option, name) ->
                Filter(name, kind == option, "kind$name") { kind = if (kind == option) null else option }
            }
        }
        LazyColumn(
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            modifier = Modifier.fillMaxSize().padding(top = 8.dp).testTag("words"),
        ) {
            items(shown, key = { it.id }) { word -> WordRow(word, container.dictionary.imageUri(word.id)) { container.player.play(word.id) } }
        }
    }
}

@Composable
private fun Filter(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Color.White),
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun WordRow(word: Word, imageUri: String, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Stage).padding(5.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
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
            )
            Text(word.gloss, style = MaterialTheme.typography.bodyMedium, color = Slate, maxLines = 1)
        }
        Text(word.level, style = MaterialTheme.typography.labelMedium, color = Slate, modifier = Modifier.padding(end = 8.dp))
        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "Listen", tint = Slate, modifier = Modifier.size(20.dp))
    }
}
