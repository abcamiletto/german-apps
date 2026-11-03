package com.abcamiletto.wortschatz.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.data.SyncStatus
import com.abcamiletto.wortschatz.domain.ReviewSet
import com.abcamiletto.wortschatz.domain.Tone
import com.abcamiletto.wortschatz.ui.session.SessionMode
import com.abcamiletto.wortschatz.ui.theme.Hairline
import com.abcamiletto.wortschatz.ui.theme.Ink
import com.abcamiletto.wortschatz.ui.theme.Slate
import com.abcamiletto.wortschatz.ui.theme.Stage
import com.abcamiletto.wortschatz.ui.theme.Track
import com.abcamiletto.wortschatz.ui.theme.color
import com.abcamiletto.wortschatz.ui.withThousands

private val TWO_COLUMNS_FROM = 820.dp
private val MAX_WIDTH = 980.dp
private val SECTION_GAP = 28.dp

@Composable
fun HomeRoute(container: AppContainer, onStart: (SessionMode) -> Unit, onBrowse: () -> Unit) {
    val model: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    val syncStatus = container.sync?.status?.collectAsState()?.value
    val picture = container.dictionary::imageUri

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Header(syncStatus, onBrowse, Modifier.widthIn(max = MAX_WIDTH))
        if (state.loaded) {
            BoxWithConstraints(Modifier.widthIn(max = MAX_WIDTH).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val scroll = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                if (maxWidth >= TWO_COLUMNS_FROM) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(scroll.weight(1f)) {
                            Actions(state, picture, onStart)
                            Suggestions(state, picture, onStart)
                        }
                        Column(scroll.weight(1f)) {
                            Forecast(state.forecast, top = 0.dp)
                            Levels(state.levels, top = if (state.forecast.any { it > 0 }) SECTION_GAP else 0.dp)
                        }
                    }
                } else {
                    Column(scroll.widthIn(max = 560.dp).fillMaxWidth()) {
                        Actions(state, picture, onStart)
                        Suggestions(state, picture, onStart)
                        Forecast(state.forecast)
                        Levels(state.levels)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(status: SyncStatus?, onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Wortschatz", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        when (status) {
            SyncStatus.Synced -> Icon(Icons.Outlined.CloudDone, contentDescription = "Synced", tint = Slate, modifier = Modifier.testTag("synced"))
            SyncStatus.Offline -> Icon(Icons.Outlined.CloudOff, contentDescription = "Offline", tint = Slate)
            else -> Unit
        }
        IconButton(onClick = onBrowse, modifier = Modifier.padding(start = 4.dp).testTag("browse")) {
            Icon(Icons.Outlined.Search, contentDescription = "All words", tint = Slate)
        }
    }
}

/** What to do next comes first and largest: words that are due, otherwise new ones. */
@Composable
private fun Actions(state: HomeState, picture: (String) -> String, onStart: (SessionMode) -> Unit) {
    val hasDue = state.due.count > 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasDue) {
            ActionCard(
                count = state.due.count.withThousands(),
                label = "Review",
                preview = state.due.preview.map(picture),
                primary = true,
                modifier = Modifier.testTag("review"),
                onClick = { onStart(SessionMode.Due) },
            )
        }
        if (state.newWords > 0) {
            ActionCard(
                count = state.newBatch.toString(),
                label = "New words",
                preview = state.newPreview.map(picture),
                primary = !hasDue,
                modifier = Modifier.testTag("learn"),
                onClick = { onStart(SessionMode.Learn) },
            )
        }
    }
}

@Composable
private fun ActionCard(
    count: String,
    label: String,
    preview: List<String>,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val foreground = if (primary) Color.White else Ink
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (primary) Ink else Color.White)
            .then(if (primary) Modifier else Modifier.border(1.dp, Hairline, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = if (primary) 24.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                count,
                style = if (primary) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge,
                color = foreground,
            )
            Text(label, style = MaterialTheme.typography.titleMedium, color = foreground)
        }
        PictureStack(preview, if (primary) 76.dp else 56.dp)
    }
}

/** Overlapping word pictures, so a suggestion shows what it holds instead of describing it. */
@Composable
private fun PictureStack(pictures: List<String>, size: Dp) {
    val overlap = size / 3
    Box(Modifier.width(size + (size - overlap) * (pictures.size - 1).coerceAtLeast(0)).height(size)) {
        pictures.forEachIndexed { index, uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier
                    .offset(x = (size - overlap) * index)
                    .size(size)
                    .clip(CircleShape)
                    .background(Stage)
                    .border(2.dp, Color.White, CircleShape)
                    .padding(size / 7),
            )
        }
    }
}

private val ReviewSet.label: String
    get() = when (this) {
        ReviewSet.Due -> "Due"
        ReviewSet.Tricky -> "Tricky words"
        ReviewSet.Soon -> "Due soon"
        ReviewSet.Recent -> "New this week"
    }

private val ReviewSet.mode: SessionMode
    get() = when (this) {
        ReviewSet.Due -> SessionMode.Due
        ReviewSet.Tricky -> SessionMode.Tricky
        ReviewSet.Soon -> SessionMode.Soon
        ReviewSet.Recent -> SessionMode.Recent
    }

@Composable
private fun Suggestions(state: HomeState, picture: (String) -> String, onStart: (SessionMode) -> Unit) {
    if (state.suggestions.isEmpty()) return
    SectionTitle("Practise")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        state.suggestions.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onStart(option.set.mode) }
                    .padding(vertical = 8.dp)
                    .testTag("repeat${option.set.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option.set.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                PictureStack(option.preview.map(picture), 36.dp)
                Text(
                    option.count.withThousands(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Slate,
                    modifier = Modifier.padding(start = 12.dp).widthIn(min = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, top: Dp = SECTION_GAP) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = top, bottom = 10.dp))
}

/** Reviews coming up, so a busy day is visible before it arrives. */
@Composable
private fun Forecast(days: List<Int>, top: Dp = SECTION_GAP) {
    if (days.all { it == 0 }) return
    val tallest = days.max().coerceAtLeast(1)
    SectionTitle("Coming up", top)
    Row(
        Modifier.fillMaxWidth().testTag("forecast"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { day, count ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (count > 0) count.withThousands() else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day == 0) Ink else Slate,
                )
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(if (count == 0) 3.dp else 4.dp + 60.dp * count / tallest)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (count == 0) Track else if (day == 0) Ink else Ink.copy(alpha = 0.28f)),
                )
                Text(
                    if (day == 0) "Now" else "${day}d",
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Levels(levels: List<LevelProgress>, top: Dp = SECTION_GAP) {
    SectionTitle("Progress", top)
    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        levels.forEach { level ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(level.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(36.dp))
                MixStrip(level, Modifier.weight(1f).height(10.dp))
                Text(
                    "${level.known.withThousands()}/${level.total.withThousands()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate,
                    modifier = Modifier.padding(start = 12.dp).widthIn(min = 64.dp),
                )
            }
        }
    }
}

/** Progress drawn in word colours, so the bar also shows the mix of genders and parts of speech learned so far. */
@Composable
private fun MixStrip(level: LevelProgress, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(5.dp))) {
        drawRoundRect(Track, cornerRadius = CornerRadius(size.height / 2))
        var x = 0f
        Tone.entries.forEach { tone ->
            val width = size.width * (level.byTone[tone] ?: 0) / level.total.coerceAtLeast(1)
            drawRect(tone.color, topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width
        }
    }
}
