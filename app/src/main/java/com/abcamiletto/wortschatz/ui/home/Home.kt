package com.abcamiletto.wortschatz.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.domain.Article
import com.abcamiletto.wortschatz.ui.session.SessionMode
import com.abcamiletto.wortschatz.ui.theme.Hairline
import com.abcamiletto.wortschatz.ui.theme.Ink
import com.abcamiletto.wortschatz.ui.theme.Slate
import com.abcamiletto.wortschatz.ui.theme.Track
import com.abcamiletto.wortschatz.ui.theme.color

@Composable
fun HomeRoute(container: AppContainer, onStart: (SessionMode) -> Unit, onBrowse: () -> Unit) {
    val model: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    Home(state, onStart, onBrowse)
}

@Composable
private fun Home(state: HomeState, onStart: (SessionMode) -> Unit, onBrowse: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text("Wortschatz", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 20.dp))
            Text(
                state.headline,
                style = MaterialTheme.typography.bodyLarge,
                color = Slate,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
            )

            state.reviewMode?.let { reviewMode ->
                ActionBlock(
                    title = "Review",
                    detail = state.reviewDetail,
                    background = Ink,
                    foreground = Color.White,
                    modifier = Modifier.testTag("review"),
                    onClick = { onStart(reviewMode) },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (state.newWords > 0) {
                val primary = state.reviewMode == null
                ActionBlock(
                    title = "Learn new words",
                    detail = state.learnDetail,
                    background = if (primary) Ink else Color.White,
                    foreground = if (primary) Color.White else Ink,
                    bordered = !primary,
                    modifier = Modifier.testTag("learn"),
                    onClick = { onStart(SessionMode.Learn) },
                )
            }

            Column(Modifier.padding(top = 36.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                state.levels.forEach { LevelRow(it) }
            }
            ArticleLegend(Modifier.padding(top = 18.dp))
        }
        TextButton(
            onClick = onBrowse,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp).testTag("browse"),
        ) {
            Text("Browse all ${"%,d".format(state.totalWords)} words", color = Ink)
        }
    }
}

@Composable
private fun ActionBlock(
    title: String,
    detail: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, Hairline, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = foreground)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = foreground.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun LevelRow(level: LevelProgress) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(level.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${level.known} of ${level.total}", style = MaterialTheme.typography.labelMedium, color = Slate)
        }
        GenderStrip(level, Modifier.padding(top = 6.dp).fillMaxWidth().height(10.dp))
    }
}

/** Progress drawn in article colours, so the bar also shows the mix of genders learned so far. */
@Composable
private fun GenderStrip(level: LevelProgress, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(5.dp))) {
        drawRoundRect(Track, cornerRadius = CornerRadius(size.height / 2))
        var x = 0f
        Article.entries.forEach { article ->
            val width = size.width * (level.byArticle[article] ?: 0) / level.total.coerceAtLeast(1)
            drawRect(article.color, topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width
        }
    }
}

@Composable
private fun ArticleLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Article.entries.forEach { article ->
            Text(article.text, style = MaterialTheme.typography.labelLarge, color = article.color)
        }
    }
}
