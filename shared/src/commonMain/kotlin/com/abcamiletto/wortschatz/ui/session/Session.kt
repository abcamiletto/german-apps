package com.abcamiletto.wortschatz.ui.session

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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.domain.Choice
import com.abcamiletto.wortschatz.domain.Word
import com.abcamiletto.wortschatz.domain.WordKind
import com.abcamiletto.wortschatz.ui.Corner
import com.abcamiletto.wortschatz.ui.Gap
import com.abcamiletto.wortschatz.ui.LocalWide
import com.abcamiletto.wortschatz.ui.PHONE_COLUMN
import com.abcamiletto.wortschatz.ui.PrimaryButton
import com.abcamiletto.wortschatz.ui.SecondaryButton
import com.abcamiletto.wortschatz.ui.hand
import com.abcamiletto.wortschatz.ui.theme.Hairline
import com.abcamiletto.wortschatz.ui.theme.Ink
import com.abcamiletto.wortschatz.ui.theme.Slate
import com.abcamiletto.wortschatz.ui.theme.Stage as StageColor
import com.abcamiletto.wortschatz.ui.theme.Track
import com.abcamiletto.wortschatz.ui.theme.color

private val WIDE_CONTENT = 1180.dp
private val SIDE_PANEL = 420.dp
// The pictures are 512 pixels, so a larger stage leaves them space around them rather than stretching them.
private val LARGEST_PICTURE = 440.dp
private val NUMBER_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)

@Composable
fun SessionRoute(container: AppContainer, mode: SessionMode, onExit: () -> Unit) {
    val model: SessionViewModel = viewModel(factory = SessionViewModel.factory(container, mode))
    val state by model.state.collectAsStateWithLifecycle()
    val phase = state.phase
    val startListening = container.microphone.rememberRequest { granted ->
        if (granted) model.listen() else model.micDenied()
    }

    // Keys work whatever has focus, so the keyboard can run a whole session. A text field keeps Enter and digits.
    val focus = remember { FocusRequester() }
    val typing = phase is Phase.Question && phase.typing
    LaunchedEffect(phase::class, typing) { if (!typing) focus.requestFocus() }

    fun onKey(key: Key): Boolean {
        val enter = key == Key.Enter || key == Key.NumPadEnter
        when {
            key == Key.Escape -> onExit()
            phase is Phase.Intro && enter -> model.introNext()
            phase is Phase.Result && enter -> model.next()
            phase is Phase.Finished && enter -> if (phase.canContinue) model.continueSession() else onExit()
            phase is Phase.Question && phase.options != null && key in NUMBER_KEYS ->
                phase.options.getOrNull(NUMBER_KEYS.indexOf(key))?.let(model::choose)
            phase is Phase.Question && phase.options == null && !phase.typing && key == Key.Spacebar ->
                if (phase.listening) model.stopListening() else startListening()
            else -> return false
        }
        return true
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && onKey(event.key) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(state, onExit)
        when (phase) {
            Phase.Loading -> Unit
            is Phase.Finished -> FinishedSession(phase, mode, model::continueSession, onExit)
            is Phase.Intro -> WordStage(model.imageUri(phase.word), phase.word) { word ->
                WordSheet(
                    word = word,
                    message = "New word",
                    icon = Icons.Outlined.AutoAwesome,
                    onPlay = { model.play(word) },
                    primary = if (phase.position == phase.total) "Test me" else "Next",
                    onPrimary = model::introNext,
                    secondary = "I know it",
                    onSecondary = model::introKnown,
                )
            }
            is Phase.Question -> QuestionStage(phase, model, startListening)
            is Phase.Result -> WordStage(model.imageUri(phase.word), phase.word) { word ->
                WordSheet(
                    word = word,
                    message = phase.outcome.message(),
                    icon = phase.outcome.icon(),
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
    Outcome.CorrectWithHelp -> "Correct, with help"
    is Outcome.WrongArticle -> "You said ${said.text}"
    is Outcome.WrongChoice -> "You picked ${choice.display}"
    Outcome.Revealed -> "The answer"
}

private fun Outcome.icon(): ImageVector = when (this) {
    Outcome.Correct, Outcome.CorrectWithHelp -> Icons.Outlined.CheckCircle
    is Outcome.WrongArticle, is Outcome.WrongChoice -> Icons.Outlined.Cancel
    Outcome.Revealed -> Icons.Outlined.Visibility
}

@Composable
private fun TopBar(state: SessionState, onExit: () -> Unit) {
    val wide = LocalWide.current
    Row(
        Modifier
            .widthIn(max = if (wide) WIDE_CONTENT else PHONE_COLUMN)
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = if (wide) Gap.medium else Gap.tiny, end = if (wide) Gap.wide else Gap.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit, modifier = Modifier.hand().testTag("close")) {
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
                modifier = Modifier.padding(start = Gap.tiny).weight(1f).height(6.dp).clip(CircleShape),
            )
            Text(
                "${state.completed}/${state.total}",
                style = MaterialTheme.typography.labelMedium,
                color = Slate,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp),
            )
        }
    }
}

@Composable
private fun Illustration(imageUri: String, modifier: Modifier = Modifier) {
    // The tinted card keeps white subjects such as a plate or a snowflake visible.
    Box(modifier.clip(Corner.large).background(StageColor), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Picture of the word",
            modifier = Modifier.padding(Gap.wide).sizeIn(maxWidth = LARGEST_PICTURE, maxHeight = LARGEST_PICTURE).fillMaxSize().testTag("illustration"),
        )
    }
}

/** The picture beside the panel on a wide screen, and above it on a phone. */
@Composable
private fun Stage(picture: @Composable (Modifier) -> Unit, panel: @Composable (Modifier) -> Unit) {
    if (LocalWide.current) {
        Row(
            Modifier.widthIn(max = WIDE_CONTENT).fillMaxSize().navigationBarsPadding().imePadding()
                .padding(start = Gap.wide, end = Gap.wide, bottom = Gap.wide),
            horizontalArrangement = Arrangement.spacedBy(Gap.wide),
        ) {
            picture(Modifier.weight(1f).fillMaxHeight())
            panel(Modifier.width(SIDE_PANEL).fillMaxHeight())
        }
    } else {
        Column(Modifier.widthIn(max = PHONE_COLUMN).fillMaxSize().imePadding()) {
            picture(Modifier.weight(1f).fillMaxWidth().padding(start = Gap.large, end = Gap.large, bottom = Gap.medium))
            panel(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WordStage(imageUri: String, word: Word, sheet: @Composable (Word) -> Unit) {
    val wide = LocalWide.current
    Stage(
        picture = { Illustration(imageUri, it) },
        panel = { modifier ->
            AnimatedContent(
                targetState = word.id,
                transitionSpec = {
                    if (wide) {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                    } else {
                        (slideInVertically(tween(320)) { it } + fadeIn(tween(120))) togetherWith fadeOut(tween(100))
                    }
                },
                label = "sheet",
                modifier = modifier,
            ) { sheet(word) }
        },
    )
}

@Composable
private fun WordSheet(
    word: Word,
    message: String,
    icon: ImageVector,
    onPlay: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    val wide = LocalWide.current
    Column(
        Modifier
            .then(if (wide) Modifier.fillMaxSize().clip(Corner.large) else Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)))
            .background(word.tone.color)
            .then(if (wide) Modifier else Modifier.navigationBarsPadding())
            .padding(Gap.wide)
            .testTag("sheet"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.small)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.testTag("message"))
        }
        if (wide) Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top = Gap.large), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                word.article?.let { article ->
                    Text(article.text, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Text(
                    word.lemma,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = lemmaSize(word.lemma), lineHeight = lemmaSize(word.lemma) * 1.08f),
                    color = Color.White,
                    modifier = Modifier.testTag("lemma"),
                )
            }
            IconButton(
                onClick = onPlay,
                modifier = Modifier.padding(start = Gap.small).size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).hand().testTag("play"),
            ) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "Listen", tint = Color.White)
            }
        }
        Text(word.gloss, style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.padding(top = Gap.small))
        val details = buildList {
            word.plural?.let { add("die $it") }
            if (word.forms.isNotEmpty()) add(word.forms)
            if (word.pluralOnly) add("Plural only")
            regionName(word.region)?.let { add(it) }
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Gap.tiny),
            )
        }
        if (wide) Spacer(Modifier.weight(1f))
        Row(Modifier.padding(top = Gap.wide), horizontalArrangement = Arrangement.spacedBy(Gap.medium)) {
            if (secondary != null) {
                SecondaryButton(
                    secondary,
                    onClick = onSecondary,
                    content = Color.White,
                    border = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f).testTag("secondary"),
                )
            }
            PrimaryButton(
                primary,
                onClick = onPrimary,
                container = Color.White,
                content = word.tone.color,
                modifier = Modifier.weight(1f).testTag("primary"),
            )
        }
    }
}

private fun lemmaSize(lemma: String) = when {
    lemma.length <= 10 -> 44.sp
    lemma.length <= 15 -> 36.sp
    lemma.length <= 21 -> 28.sp
    else -> 22.sp
}

private fun regionName(region: String): String? = when (region) {
    "A" -> "Austria"
    "CH" -> "Switzerland"
    "A,CH" -> "Austria and Switzerland"
    else -> null
}

@Composable
private fun QuestionStage(question: Phase.Question, model: SessionViewModel, startListening: () -> Unit) {
    val wide = LocalWide.current
    Stage(
        picture = { Illustration(model.imageUri(question.word), it) },
        panel = { modifier ->
            // On a wide screen the panel has the same frame as the answer that replaces it, so nothing jumps.
            val frame = if (wide) {
                Modifier.clip(Corner.large).background(Color.White).border(1.dp, Hairline, Corner.large).padding(vertical = Gap.large)
            } else {
                Modifier.navigationBarsPadding()
            }
            Column(modifier.then(frame)) {
                if (wide) Spacer(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = Gap.wide),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (question.heard.isNotEmpty()) {
                        Text(
                            question.heard,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = Gap.tiny).testTag("heard"),
                        )
                    }
                    Text(
                        question.hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("hint"),
                    )
                }
                Box(Modifier.padding(top = Gap.medium)) {
                    when {
                        question.options != null -> OptionList(question.options, model::choose)
                        question.typing -> TypedAnswer(
                            kind = question.word.kind,
                            onSubmit = model::submitTyped,
                            onSpeak = { model.setTyping(false) },
                            canSpeak = model.speechAvailable,
                        )
                        else -> SpeechControls(question, startListening, model::stopListening, onType = { model.setTyping(true) })
                    }
                }
                if (wide) Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth().padding(start = Gap.large, end = Gap.large, top = Gap.small, bottom = if (wide) 0.dp else Gap.small),
                    horizontalArrangement = Arrangement.spacedBy(Gap.small),
                ) {
                    if (question.options == null) {
                        AssistButton("Suggest", Icons.Outlined.Lightbulb, "suggest", Modifier.weight(1f), model::suggest)
                    }
                    AssistButton("Reveal", Icons.Outlined.Visibility, "reveal", Modifier.weight(1f), model::reveal)
                }
            }
        },
    )
}

@Composable
private fun AssistButton(label: String, icon: ImageVector, tag: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = Corner.small, modifier = modifier.height(48.dp).hand().testTag(tag)) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Gap.small))
        Text(label, color = Ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SpeechControls(question: Phase.Question, onStart: () -> Unit, onStop: () -> Unit, onType: () -> Unit) {
    val pulse by animateFloatAsState(if (question.listening) 1f + question.level * 0.35f else 1f, tween(90), label = "pulse")
    Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(88.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(if (question.listening) Ink.copy(alpha = 0.14f) else Color.Transparent),
        )
        IconButton(
            onClick = if (question.listening) onStop else onStart,
            modifier = Modifier.size(72.dp).clip(CircleShape).background(Ink).hand().testTag("mic"),
        ) {
            Icon(
                if (question.listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = if (question.listening) "Stop listening" else "Speak your answer",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        if (LocalWide.current && !question.listening) {
            // The space bar works the microphone, so a keyboard user can see that without trying it.
            Text(
                "Space",
                style = MaterialTheme.typography.labelMedium,
                color = Slate,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = Gap.section),
            )
        }
        IconButton(
            onClick = onType,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = Gap.wide).hand().testTag("type"),
        ) {
            Icon(Icons.Outlined.Keyboard, contentDescription = "Type instead", tint = Slate)
        }
    }
}

@Composable
private fun TypedAnswer(kind: WordKind, onSubmit: (String) -> Unit, onSpeak: () -> Unit, canSpeak: Boolean) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(start = Gap.large, end = if (canSpeak) Gap.tiny else Gap.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap.small),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = {
                Text(
                    when (kind) {
                        WordKind.Noun -> "der, die or das + noun"
                        WordKind.Verb -> "Verb"
                        WordKind.Adjective -> "Adjective"
                    },
                    maxLines = 1,
                )
            },
            singleLine = true,
            shape = Corner.medium,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done, hintLocales = LocaleList(Locale("de-DE"))),
            keyboardActions = KeyboardActions(onDone = { onSubmit(text) }),
            modifier = Modifier.weight(1f).focusRequester(focus).testTag("answer"),
        )
        PrimaryButton("Check", onClick = { onSubmit(text) }, modifier = Modifier.height(56.dp).testTag("check"))
        if (canSpeak) {
            IconButton(onClick = onSpeak, modifier = Modifier.hand().testTag("speak")) {
                Icon(Icons.Outlined.Mic, contentDescription = "Speak instead", tint = Slate)
            }
        }
    }
}

@Composable
private fun OptionList(options: List<Choice>, onChoose: (Choice) -> Unit) {
    val wide = LocalWide.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Gap.large),
        verticalArrangement = Arrangement.spacedBy(Gap.small),
    ) {
        options.forEachIndexed { index, choice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(Corner.small)
                    .background(Color.White)
                    .border(1.dp, Hairline, Corner.small)
                    .hand()
                    .clickable { onChoose(choice) }
                    .padding(horizontal = Gap.large)
                    .testTag("option$index"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The number keys pick an answer on a keyboard, so a wide screen shows which is which.
                if (wide) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = Slate, modifier = Modifier.width(Gap.wide))
                }
                Text(choice.display, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FinishedSession(phase: Phase.Finished, mode: SessionMode, onContinue: () -> Unit, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(Gap.wide), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
            Text(
                if (phase.answered == 0) "All set" else "${phase.firstTry} of ${phase.answered}",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.testTag("finished"),
            )
            if (phase.answered > 0) {
                Text("right first time", style = MaterialTheme.typography.bodyLarge, color = Slate, modifier = Modifier.padding(top = Gap.tiny))
            }
            Spacer(Modifier.height(Gap.section))
            if (phase.canContinue) {
                PrimaryButton(
                    if (mode == SessionMode.Learn) "$LEARN_BATCH more" else "Keep going",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().testTag("continue"),
                )
                Spacer(Modifier.height(Gap.small))
                SecondaryButton("Done", onClick = onDone, border = Hairline, modifier = Modifier.fillMaxWidth().testTag("done"))
            } else {
                PrimaryButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("done"))
            }
        }
    }
}
