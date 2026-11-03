package com.abcamiletto.wortschatz.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.data.SyncStatus
import com.abcamiletto.wortschatz.domain.ReviewSet
import com.abcamiletto.wortschatz.domain.Tone
import com.abcamiletto.wortschatz.ui.Brand
import com.abcamiletto.wortschatz.ui.Corner
import com.abcamiletto.wortschatz.ui.Gap
import com.abcamiletto.wortschatz.ui.LocalWide
import com.abcamiletto.wortschatz.ui.PHONE_COLUMN
import com.abcamiletto.wortschatz.ui.hand
import com.abcamiletto.wortschatz.ui.session.SessionMode
import com.abcamiletto.wortschatz.ui.theme.Hairline
import com.abcamiletto.wortschatz.ui.theme.Ink
import com.abcamiletto.wortschatz.ui.theme.Muted
import com.abcamiletto.wortschatz.ui.theme.Slate
import com.abcamiletto.wortschatz.ui.theme.Stage
import com.abcamiletto.wortschatz.ui.theme.Track
import com.abcamiletto.wortschatz.ui.theme.color
import com.abcamiletto.wortschatz.ui.withThousands

private val WIDE_CONTENT = 1000.dp
private val COLUMN_GAP = Gap.section

@Composable
fun HomeRoute(container: AppContainer, onStart: (SessionMode) -> Unit, onBrowse: () -> Unit) {
    val model: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    val status = container.sync?.status?.collectAsState()?.value
    val picture = container.dictionary::imageUri
    val wide = LocalWide.current

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!wide) PhoneHeader(status, onBrowse)
        if (!state.loaded) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (wide) {
                WideHome(state, picture, onStart)
            } else {
                Column(Modifier.widthIn(max = PHONE_COLUMN).fillMaxWidth().padding(start = Gap.page, end = Gap.page, bottom = Gap.section)) {
                    Actions(state, picture, onStart)
                    Practise(state, picture, onStart, Modifier.padding(top = Gap.section))
                    Forecast(state.forecast, Modifier.padding(top = Gap.section))
                    Levels(state.levels, Modifier.padding(top = Gap.section))
                }
            }
        }
    }
}

@Composable
private fun PhoneHeader(status: SyncStatus?, onBrowse: () -> Unit) {
    Row(
        Modifier.widthIn(max = PHONE_COLUMN).fillMaxWidth().padding(start = Gap.page, end = Gap.tiny).height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Brand(Modifier.weight(1f))
        when (status) {
            SyncStatus.Synced -> Icon(Icons.Outlined.CloudDone, contentDescription = "Synced", tint = Slate, modifier = Modifier.size(20.dp))
            SyncStatus.Offline -> Icon(Icons.Outlined.CloudOff, contentDescription = "Offline", tint = Slate, modifier = Modifier.size(20.dp))
            else -> Unit
        }
        IconButton(onClick = onBrowse, modifier = Modifier.padding(start = Gap.tiny).hand().testTag("browse")) {
            Icon(Icons.Outlined.Search, contentDescription = "All words", tint = Ink)
        }
    }
}

/** The actions run across the top. Below them, what can be practised sits beside what is coming and how far along it all is. */
@Composable
private fun WideHome(state: HomeState, picture: (String) -> String, onStart: (SessionMode) -> Unit) {
    Column(Modifier.widthIn(max = WIDE_CONTENT).fillMaxWidth().padding(horizontal = Gap.pageWide, vertical = Gap.section)) {
        Actions(state, picture, onStart)
        val hasPractise = state.suggestions.isNotEmpty()
        if (!hasPractise && state.forecast.all { it == 0 }) {
            Levels(state.levels, Modifier.padding(top = Gap.section))
            return
        }
        Row(Modifier.padding(top = Gap.section), horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
            Column(Modifier.weight(1f)) {
                if (hasPractise) Practise(state, picture, onStart) else Levels(state.levels)
            }
            Column(Modifier.weight(1f)) {
                Forecast(state.forecast)
                if (hasPractise) {
                    val gap = if (state.forecast.any { it > 0 }) Gap.section else 0.dp
                    Levels(state.levels, Modifier.padding(top = gap))
                }
            }
        }
    }
}

/** What to do next comes first and largest: words that are due, otherwise new ones. */
@Composable
private fun Actions(state: HomeState, picture: (String) -> String, onStart: (SessionMode) -> Unit) {
    val hasDue = state.due.count > 0
    val hasNew = state.newWords > 0
    val review: @Composable (Modifier) -> Unit = { modifier ->
        ActionCard(
            count = state.due.count.withThousands(),
            label = "Review",
            preview = state.due.preview.map(picture),
            primary = true,
            modifier = modifier.testTag("review"),
            onClick = { onStart(SessionMode.Due) },
        )
    }
    val learn: @Composable (Modifier) -> Unit = { modifier ->
        ActionCard(
            count = state.newBatch.toString(),
            label = "New words",
            preview = state.newPreview.map(picture),
            primary = !hasDue,
            modifier = modifier.testTag("learn"),
            onClick = { onStart(SessionMode.Learn) },
        )
    }
    if (LocalWide.current && hasDue && hasNew) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
            review(Modifier.weight(1f).fillMaxHeight())
            learn(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Gap.medium)) {
            if (hasDue) review(Modifier)
            if (hasNew) learn(Modifier)
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
    val foreground = if (primary) Color.White else Ink
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corner.large)
            .background(if (primary) Ink else Color.White)
            .then(if (primary) Modifier else Modifier.border(1.dp, Hairline, Corner.large))
            .hand()
            .clickable(onClick = onClick)
            .padding(horizontal = Gap.wide, vertical = Gap.wide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(count, style = MaterialTheme.typography.displayLarge, color = foreground)
            Text(label, style = MaterialTheme.typography.titleMedium, color = foreground, modifier = Modifier.padding(top = Gap.tiny))
        }
        PictureStack(preview, 72.dp)
    }
}

/** Overlapping word pictures, so a suggestion shows what it holds instead of describing it. */
@Composable
private fun PictureStack(pictures: List<String>, size: Dp) {
    val step = size * 2 / 3
    Box(Modifier.width(size + step * (pictures.size - 1).coerceAtLeast(0)).height(size)) {
        pictures.forEachIndexed { index, uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier
                    .offset(x = step * index)
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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Gap.medium))
}

@Composable
private fun Practise(state: HomeState, picture: (String) -> String, onStart: (SessionMode) -> Unit, modifier: Modifier = Modifier) {
    if (state.suggestions.isEmpty()) return
    Column(modifier) {
        SectionTitle("Practise")
        Column(Modifier.clip(Corner.medium).background(Color.White).border(1.dp, Hairline, Corner.medium)) {
            state.suggestions.forEachIndexed { index, option ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = Gap.large), color = Hairline)
                PractiseRow(option, picture) { onStart(option.set.mode) }
            }
        }
    }
}

@Composable
private fun PractiseRow(option: RepeatOption, picture: (String) -> String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .hand()
            .clickable(onClick = onClick)
            .padding(start = Gap.large, end = Gap.medium)
            .testTag("repeat${option.set.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(option.set.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        PictureStack(option.preview.map(picture), 36.dp)
        Text(
            option.count.withThousands(),
            style = MaterialTheme.typography.labelLarge,
            color = Slate,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Slate,
            modifier = Modifier.padding(start = Gap.tiny).size(20.dp),
        )
    }
}

/** Reviews coming up, so a busy day is visible before it arrives. */
@Composable
private fun Forecast(days: List<Int>, modifier: Modifier = Modifier) {
    if (days.all { it == 0 }) return
    val tallest = days.max().coerceAtLeast(1)
    Column(modifier) {
        SectionTitle("Coming up")
        Row(
            Modifier.fillMaxWidth().testTag("forecast"),
            horizontalArrangement = Arrangement.spacedBy(Gap.small),
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
                            .padding(top = Gap.tiny)
                            .fillMaxWidth()
                            .height(if (count == 0) 2.dp else 6.dp + 58.dp * count / tallest)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (count == 0) Track else if (day == 0) Ink else Muted),
                    )
                    Text(
                        if (day == 0) "Now" else "${day}d",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (day == 0) Ink else Slate,
                        modifier = Modifier.padding(top = Gap.small),
                    )
                }
            }
        }
    }
}

@Composable
private fun Levels(levels: List<LevelProgress>, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionTitle("Progress")
        Column(verticalArrangement = Arrangement.spacedBy(Gap.medium)) {
            levels.forEach { level ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(level.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(32.dp))
                    MixStrip(level, Modifier.weight(1f).height(8.dp))
                    Text(
                        "${level.known.withThousands()} / ${level.total.withThousands()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Slate,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(88.dp),
                    )
                }
            }
        }
    }
}

/** Progress drawn in word colours, so the bar also shows the mix of genders and parts of speech learned so far. */
@Composable
private fun MixStrip(level: LevelProgress, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(CircleShape)) {
        drawRoundRect(Track, cornerRadius = CornerRadius(size.height / 2))
        var x = 0f
        Tone.entries.forEach { tone ->
            val width = size.width * (level.byTone[tone] ?: 0) / level.total.coerceAtLeast(1)
            drawRect(tone.color, topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width
        }
    }
}
