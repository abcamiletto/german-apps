package com.abcamiletto.wortschatz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abcamiletto.wortschatz.ui.theme.Ink

/** The width from which a screen stops being a phone column and uses the space beside it. */
val WIDE_FROM = 840.dp
val PHONE_COLUMN = 560.dp

val LocalWide = compositionLocalOf { false }

/** One scale for every gap in the app, so that spacing stays consistent from screen to screen. */
object Gap {
    val tiny = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val page = 20.dp
    val wide = 24.dp
    val section = 32.dp
    val pageWide = 40.dp
}

/** Larger surfaces get larger corners, so a button inside a card never looks rounder than the card. */
object Corner {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(24.dp)
}

val BUTTON_HEIGHT = 52.dp

/** A browser shows the pointing hand over anything that can be clicked. */
fun Modifier.hand(): Modifier = pointerHoverIcon(PointerIcon.Hand)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Ink,
    content: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Corner.medium,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        modifier = modifier.height(BUTTON_HEIGHT).hand(),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: Color = Ink, border: Color = content) {
    OutlinedButton(
        onClick = onClick,
        shape = Corner.medium,
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
        modifier = modifier.height(BUTTON_HEIGHT).hand(),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
