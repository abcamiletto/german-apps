package com.abcamiletto.wortschatz.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
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

private val WIDE_CONTENT = 1040.dp
// The panel keeps its height on a wide screen, so the picture stays still from the question to the answer.
private val WIDE_PANEL = 208.dp
// The pictures are 512 pixels, so a larger stage leaves them space around them rather than stretching them.
private val LARGEST_PICTURE = 440.dp
private val WIDE_ACTION = 168.dp
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
    // Suggest removes the button that was just clicked and focus goes with it, so it's taken back here too.
    val choosing = phase is Phase.Question && phase.options != null
    LaunchedEffect(phase::class, typing, choosing) { if (!typing) focus.requestFocus() }

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
        TopBar(state, mode, onExit)
        when (phase) {
            Phase.Loading -> Unit
            is Phase.Finished -> FinishedSession(phase, mode, model::continueSession, onExit)
            is Phase.Intro -> WordStage(model.imageUri(phase.word), phase.word) { word ->
                WordSheet(
                    word = word,
                    message = "New word",
                    icon = Icons.Outlined.AutoAwesome,
                    onPlay = { model.play(word) },
                    primary = if (phase.position == phase.total) "Start guessing" else "Next",
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
private fun TopBar(state: SessionState, mode: SessionMode, onExit: () -> Unit) {
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
        if (mode == SessionMode.Learn) {
            LearnSteps(state, Modifier.padding(start = Gap.tiny).weight(1f))
        } else if (state.total > 0) {
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

/** A session of new words has two steps, so both stay in view: first discover the words, then guess them. */
@Composable
private fun LearnSteps(state: SessionState, modifier: Modifier) {
    val phase = state.phase
    val discovering = phase is Phase.Intro
    val words = if (phase is Phase.Intro) phase.total else state.total
    if (words == 0) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Gap.large)) {
        Step("Discover", if (phase is Phase.Intro) phase.position else words, words, discovering, Modifier.weight(1f).testTag("discover"))
        Step("Guess", state.completed, words, phase is Phase.Question || phase is Phase.Result, Modifier.weight(1f).testTag("guess"))
    }
}

@Composable
private fun Step(label: String, done: Int, total: Int, active: Boolean, modifier: Modifier) {
    val labelColor by animateColorAsState(if (active) Ink else Slate, tween(200), label = "step")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Gap.tiny)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium), color = labelColor)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(total) { index ->
                val color by animateColorAsState(if (index < done) Ink else Track, tween(200), label = "segment")
                Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(color))
            }
        }
    }
}

/** What is being asked stays put on every screen: the picture, and the word in English. */
@Composable
private fun Prompt(imageUri: String, word: Word, modifier: Modifier = Modifier) {
    // The tinted card keeps white subjects such as a plate or a snowflake visible.
    Column(
        modifier.clip(Corner.large).background(StageColor).padding(Gap.wide),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Picture of the word",
            modifier = Modifier.weight(1f, fill = false).sizeIn(maxWidth = LARGEST_PICTURE, maxHeight = LARGEST_PICTURE).aspectRatio(1f).testTag("illustration"),
        )
        val size = promptSize(word.gloss, LocalWide.current)
        Text(
            word.gloss,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = size, lineHeight = size * 1.15f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Gap.large).testTag("prompt"),
        )
    }
}

private fun promptSize(gloss: String, wide: Boolean) = when {
    gloss.length <= 24 -> if (wide) 34.sp else 30.sp
    gloss.length <= 40 -> if (wide) 28.sp else 24.sp
    else -> if (wide) 24.sp else 20.sp
}

/** The picture above, and below it the panel where the learner answers. */
@Composable
private fun Stage(picture: @Composable (Modifier) -> Unit, panel: @Composable (Modifier) -> Unit) {
    if (LocalWide.current) {
        Column(
            Modifier.widthIn(max = WIDE_CONTENT).fillMaxSize().navigationBarsPadding()
                .padding(start = Gap.wide, end = Gap.wide, bottom = Gap.wide),
            verticalArrangement = Arrangement.spacedBy(Gap.large),
        ) {
            picture(Modifier.weight(1f).fillMaxWidth())
            panel(Modifier.fillMaxWidth().height(WIDE_PANEL))
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
        picture = { Prompt(imageUri, word, it) },
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
    val heading: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.small)) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(message, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.testTag("message"))
            }
            word.article?.let { article ->
                Text(article.text, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(top = Gap.medium))
            }
            Text(
                word.lemma,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = lemmaSize(word.lemma), lineHeight = lemmaSize(word.lemma) * 1.08f),
                color = Color.White,
                modifier = Modifier.padding(top = if (word.article == null) Gap.medium else 0.dp).testTag("lemma"),
            )
            val details = word.details()
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = Gap.tiny),
                )
            }
        }
    }
    val play: @Composable () -> Unit = {
        IconButton(
            onClick = onPlay,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).hand().testTag("play"),
        ) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "Listen", tint = Color.White)
        }
    }
    val actions: @Composable (Modifier) -> Unit = { modifier ->
        if (secondary != null) {
            SecondaryButton(
                secondary,
                onClick = onSecondary,
                content = Color.White,
                border = Color.White.copy(alpha = 0.6f),
                modifier = modifier.testTag("secondary"),
            )
        }
        PrimaryButton(
            primary,
            onClick = onPrimary,
            container = Color.White,
            content = word.tone.color,
            modifier = modifier.testTag("primary"),
        )
    }
    if (LocalWide.current) {
        Row(
            Modifier.fillMaxSize().clip(Corner.large).background(word.tone.color).padding(horizontal = Gap.section, vertical = Gap.wide).testTag("sheet"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gap.medium),
        ) {
            heading(Modifier.weight(1f))
            play()
            Spacer(Modifier.width(Gap.medium))
            actions(Modifier.width(WIDE_ACTION))
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(word.tone.color)
                .navigationBarsPadding()
                .padding(Gap.wide)
                .testTag("sheet"),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                heading(Modifier.weight(1f))
                Box(Modifier.padding(start = Gap.small)) { play() }
            }
            Row(Modifier.padding(top = Gap.wide), horizontalArrangement = Arrangement.spacedBy(Gap.medium)) {
                actions(Modifier.weight(1f))
            }
        }
    }
}

private fun Word.details(): List<String> = buildList {
    plural?.let { add("die $it") }
    if (forms.isNotEmpty()) add(forms)
    if (pluralOnly) add("Plural only")
    regionName(region)?.let { add(it) }
}

private fun lemmaSize(lemma: String) = when {
    // One long word can't wrap, so it has to fit the width of a phone.
    lemma.split(' ').maxOf { it.length } > 24 -> 18.sp
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
    Stage(
        picture = { Prompt(model.imageUri(question.word), question.word, it) },
        panel = { modifier ->
            if (LocalWide.current) {
                WideAnswerPanel(question, model, startListening, modifier)
            } else {
                PhoneAnswerPanel(question, model, startListening, modifier)
            }
        },
    )
}

@Composable
private fun PhoneAnswerPanel(question: Phase.Question, model: SessionViewModel, startListening: () -> Unit, modifier: Modifier) {
    Column(modifier.navigationBarsPadding()) {
        Feedback(question, Alignment.CenterHorizontally, Modifier.fillMaxWidth().padding(horizontal = Gap.wide))
        Box(Modifier.padding(top = Gap.medium)) {
            when {
                question.options != null -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = Gap.large),
                    verticalArrangement = Arrangement.spacedBy(Gap.small),
                ) {
                    question.options.forEachIndexed { index, choice ->
                        OptionButton(index, choice, Modifier.fillMaxWidth(), model::choose)
                    }
                }
                question.typing -> TypedAnswer(
                    kind = question.word.kind,
                    onSubmit = model::submitTyped,
                    onSpeak = { model.setTyping(false) },
                    canSpeak = model.speechAvailable,
                    modifier = Modifier.fillMaxWidth().padding(start = Gap.large, end = if (model.speechAvailable) Gap.tiny else Gap.large),
                )
                else -> Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                    Microphone(question, startListening, model::stopListening)
                    TypeInstead(model, Modifier.align(Alignment.CenterEnd).padding(end = Gap.wide))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Gap.large, vertical = Gap.small),
            horizontalArrangement = Arrangement.spacedBy(Gap.small),
        ) {
            Assists(question, model, Modifier.weight(1f))
        }
    }
}

/** Three columns on a wide screen: what was heard, then the answer in the middle, then the ways to get help. */
@Composable
private fun WideAnswerPanel(question: Phase.Question, model: SessionViewModel, startListening: () -> Unit, modifier: Modifier) {
    val frame = modifier.clip(Corner.large).background(Color.White).border(1.dp, Hairline, Corner.large).padding(horizontal = Gap.section)
    if (question.options != null) {
        Column(frame, verticalArrangement = Arrangement.spacedBy(Gap.small, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Feedback(question, Alignment.Start, Modifier.weight(1f))
                Assists(question, model, Modifier)
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Gap.small),
                verticalArrangement = Arrangement.spacedBy(Gap.small),
            ) {
                question.options.forEachIndexed { index, choice -> OptionButton(index, choice, Modifier.weight(1f), model::choose) }
            }
        }
        return
    }
    Row(frame, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.wide)) {
        Feedback(question, Alignment.Start, Modifier.weight(1f))
        if (question.typing) {
            TypedAnswer(
                kind = question.word.kind,
                onSubmit = model::submitTyped,
                onSpeak = { model.setTyping(false) },
                canSpeak = model.speechAvailable,
                modifier = Modifier.weight(1.6f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) { Assists(question, model, Modifier) }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    Microphone(question, startListening, model::stopListening)
                }
                // The space bar works the microphone, so a keyboard user can see that without trying it.
                Text("Space", style = MaterialTheme.typography.labelMedium, color = if (question.listening) Color.Transparent else Slate)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                TypeInstead(model, Modifier.padding(end = Gap.tiny))
                Assists(question, model, Modifier)
            }
        }
    }
}

/** What the recogniser heard, and what to do next. */
@Composable
private fun Feedback(question: Phase.Question, alignment: Alignment.Horizontal, modifier: Modifier) {
    val textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center
    Column(modifier.heightIn(min = 40.dp), horizontalAlignment = alignment, verticalArrangement = Arrangement.Center) {
        if (question.heard.isNotEmpty()) {
            Text(
                question.heard,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = textAlign,
                modifier = Modifier.padding(bottom = Gap.tiny).testTag("heard"),
            )
        }
        Text(
            question.hint,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate,
            textAlign = textAlign,
            modifier = Modifier.testTag("hint"),
        )
    }
}

@Composable
private fun Assists(question: Phase.Question, model: SessionViewModel, modifier: Modifier) {
    if (question.options == null) {
        AssistButton("Suggest", Icons.Outlined.Lightbulb, "suggest", modifier, model::suggest)
    }
    AssistButton("Reveal", Icons.Outlined.Visibility, "reveal", modifier, model::reveal)
}

@Composable
private fun TypeInstead(model: SessionViewModel, modifier: Modifier) {
    IconButton(onClick = { model.setTyping(true) }, modifier = modifier.hand().testTag("type")) {
        Icon(Icons.Outlined.Keyboard, contentDescription = "Type instead", tint = Slate)
    }
}

@Composable
private fun AssistButton(label: String, icon: ImageVector, tag: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = Corner.small, modifier = modifier.height(48.dp).hand().testTag(tag)) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Gap.small))
        Text(label, color = Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun Microphone(question: Phase.Question, onStart: () -> Unit, onStop: () -> Unit) {
    val pulse by animateFloatAsState(if (question.listening) 1f + question.level * 0.35f else 1f, tween(90), label = "pulse")
    Box(contentAlignment = Alignment.Center) {
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
    }
}

@Composable
private fun TypedAnswer(kind: WordKind, onSubmit: (String) -> Unit, onSpeak: () -> Unit, canSpeak: Boolean, modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier,
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
private fun OptionButton(index: Int, choice: Choice, modifier: Modifier, onChoose: (Choice) -> Unit) {
    val wide = LocalWide.current
    Row(
        modifier
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
            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = Slate, modifier = Modifier.padding(end = Gap.medium))
        }
        Text(choice.display, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
