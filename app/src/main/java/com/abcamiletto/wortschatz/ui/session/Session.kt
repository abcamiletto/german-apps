package com.abcamiletto.wortschatz.ui.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.data.Dictionary
import com.abcamiletto.wortschatz.domain.Choice
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.ui.theme.Hairline
import com.abcamiletto.wortschatz.ui.theme.Ink
import com.abcamiletto.wortschatz.ui.theme.Slate
import com.abcamiletto.wortschatz.ui.theme.Track
import com.abcamiletto.wortschatz.ui.theme.color

@Composable
fun SessionRoute(container: AppContainer, mode: SessionMode, onExit: () -> Unit) {
    val model: SessionViewModel = viewModel(factory = SessionViewModel.factory(container, mode))
    val state by model.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(state, onExit)
        when (val phase = state.phase) {
            Phase.Loading -> Unit
            Phase.Empty -> EmptySession(onExit)
            is Phase.Finished -> FinishedSession(phase, onExit)
            is Phase.Intro -> WordCard(phase.word, phase.word) { word ->
                WordSheet(
                    word = word,
                    message = "New word",
                    onPlay = { model.play(word) },
                    primary = if (phase.position == phase.total) "Start the test" else "Next word",
                    onPrimary = model::introNext,
                    secondary = "I already know this",
                    onSecondary = model::introKnown,
                )
            }
            is Phase.Question -> QuestionCard(phase, model)
            is Phase.Result -> WordCard(phase.word, phase.word) { word ->
                WordSheet(
                    word = word,
                    message = phase.outcome.message(),
                    onPlay = { model.play(word) },
                    primary = "Next",
                    onPrimary = model::next,
                )
            }
        }
    }
}

private fun Outcome.message(): String = when (this) {
    Outcome.Correct -> "Correct"
    Outcome.CorrectWithHelp -> "Correct, with a little help"
    is Outcome.WrongArticle -> "You said ${said.text}. It comes back later."
    is Outcome.WrongChoice -> "You picked ${choice.display}. It comes back later."
    Outcome.Revealed -> "Here's the answer. It comes back later."
}

@Composable
private fun TopBar(state: SessionState, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onExit, modifier = Modifier.testTag("close")) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Ink)
        }
        if (state.total > 0) {
            val fraction by animateFloatAsState(state.completed.toFloat() / state.total, tween(300), label = "progress")
            LinearProgressIndicator(
                progress = { fraction },
                color = Ink,
                trackColor = Track,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
            )
            Text(
                "${state.completed} of ${state.total}",
                style = MaterialTheme.typography.labelMedium,
                color = Slate,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun Illustration(word: Word, modifier: Modifier = Modifier) {
    AsyncImage(
        model = Dictionary.imageUri(word.id),
        contentDescription = "Picture of the word",
        modifier = modifier.padding(horizontal = 32.dp, vertical = 12.dp).testTag("illustration"),
    )
}

/** Keeps the picture in place while the coloured answer sheet rises over the lower half of the screen. */
@Composable
private fun WordCard(word: Word, key: Any, sheet: @Composable (Word) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Illustration(word, Modifier.weight(1f).fillMaxWidth())
        AnimatedContent(
            targetState = key,
            transitionSpec = {
                (slideInVertically(tween(320)) { it } + fadeIn(tween(120))) togetherWith fadeOut(tween(100))
            },
            label = "sheet",
        ) { sheet(word) }
    }
}

@Composable
private fun WordSheet(
    word: Word,
    message: String,
    onPlay: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(word.article.color)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 22.dp)
            .testTag("sheet"),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f), modifier = Modifier.testTag("message"))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(word.article.text, style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.9f))
                Text(
                    word.noun,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = nounSize(word.noun), lineHeight = nounSize(word.noun) * 1.08f),
                    color = Color.White,
                    modifier = Modifier.testTag("noun"),
                )
            }
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).testTag("play"),
            ) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "Listen", tint = Color.White)
            }
        }
        val details = buildString {
            append(word.gloss)
            word.plural?.let { append("\nPlural: die $it") }
            if (word.pluralOnly) append("\nOnly used in the plural")
            regionName(word.region)?.let { append("\nUsed in $it") }
        }
        Text(details, style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (secondary != null) {
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.weight(1f).height(54.dp).testTag("secondary"),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text(secondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
            }
            Button(
                onClick = onPrimary,
                modifier = Modifier.weight(1f).height(54.dp).testTag("primary"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = word.article.color),
            ) { Text(primary, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

private fun nounSize(noun: String) = when {
    noun.length <= 10 -> 46.sp
    noun.length <= 15 -> 36.sp
    noun.length <= 21 -> 27.sp
    else -> 22.sp
}

private fun regionName(region: String): String? = when (region) {
    "A" -> "Austria"
    "CH" -> "Switzerland"
    "A,CH" -> "Austria and Switzerland"
    else -> null
}

@Composable
private fun QuestionCard(question: Phase.Question, model: SessionViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.listen() else model.micDenied()
    }
    val startListening = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) model.listen() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Illustration(question.word, Modifier.weight(1f).fillMaxWidth())
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (question.heard.isNotEmpty()) {
                Text(
                    question.heard,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("heard"),
                )
            }
            Text(
                question.hint,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp).testTag("hint"),
            )
        }
        when {
            question.options != null -> OptionList(question.options, model::choose)
            question.typing -> TypedAnswer(
                onSubmit = model::submitTyped,
                onSpeak = { model.setTyping(false) },
                canSpeak = model.speechAvailable,
            )
            else -> SpeechControls(question, startListening, model::stopListening, onType = { model.setTyping(true) })
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (question.options == null) {
                AssistButton("Suggest", Icons.Outlined.Lightbulb, "suggest", Modifier.weight(1f), model::suggest)
            }
            AssistButton("Reveal", Icons.Outlined.Visibility, "reveal", Modifier.weight(1f), model::reveal)
        }
    }
}

@Composable
private fun AssistButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier.height(48.dp).testTag(tag)) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SpeechControls(question: Phase.Question, onStart: () -> Unit, onStop: () -> Unit, onType: () -> Unit) {
    val pulse by animateFloatAsState(if (question.listening) 1f + question.level * 0.35f else 1f, tween(90), label = "pulse")
    Box(Modifier.fillMaxWidth().height(116.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(88.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(if (question.listening) Ink.copy(alpha = 0.14f) else Color.Transparent),
        )
        IconButton(
            onClick = if (question.listening) onStop else onStart,
            modifier = Modifier.size(76.dp).clip(CircleShape).background(Ink).testTag("mic"),
        ) {
            Icon(
                if (question.listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = if (question.listening) "Stop listening" else "Speak your answer",
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
        IconButton(
            onClick = onType,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).testTag("type"),
        ) {
            Icon(Icons.Outlined.Keyboard, contentDescription = "Type instead", tint = Slate)
        }
    }
}

@Composable
private fun TypedAnswer(onSubmit: (String) -> Unit, onSpeak: () -> Unit, canSpeak: Boolean) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("der, die or das + noun") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done, hintLocales = LocaleList(Locale("de-DE"))),
            keyboardActions = KeyboardActions(onDone = { onSubmit(text) }),
            modifier = Modifier.weight(1f).testTag("answer"),
        )
        Button(
            onClick = { onSubmit(text) },
            modifier = Modifier.height(56.dp).testTag("check"),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Check") }
        if (canSpeak) {
            IconButton(onClick = onSpeak, modifier = Modifier.testTag("speak")) {
                Icon(Icons.Outlined.Mic, contentDescription = "Speak instead", tint = Slate)
            }
        }
    }
}

@Composable
private fun OptionList(options: List<Choice>, onChoose: (Choice) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, choice ->
            Text(
                choice.display,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(500)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                    .clickable { onChoose(choice) }
                    .padding(horizontal = 18.dp, vertical = 13.dp)
                    .testTag("option$index"),
            )
        }
    }
}

@Composable
private fun EmptySession(onExit: () -> Unit) {
    CenteredMessage("Nothing to practise yet", "Learn some new words first, and they'll appear here.", onExit)
}

@Composable
private fun FinishedSession(phase: Phase.Finished, onExit: () -> Unit) {
    val title = if (phase.firstTry == phase.answered) "All right the first time" else "Session complete"
    CenteredMessage(title, "${phase.firstTry} of ${phase.answered} right the first time, without help.", onExit)
}

@Composable
private fun CenteredMessage(title: String, body: String, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("finished"))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Slate, modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("done")) { Text("Done") }
    }
}
