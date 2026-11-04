package com.abcamiletto.wortschatz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import com.abcamiletto.wortschatz.resources.Res
import com.abcamiletto.wortschatz.resources.atkinson_hyperlegible_next
import com.abcamiletto.wortschatz.domain.Tone

val Paper = Color(0xFFF6F7FB)
val Ink = Color(0xFF14213D)
val Slate = Color(0xFF5B6478)
val Hairline = Color(0xFFDDE1EA)
val Track = Color(0xFFE7EAF1)
val Stage = Color(0xFFE3E8F2)
val Muted = Color(0xFFB9BFCC)
val DerBlue = Color(0xFF2F5BEA)
val DieRed = Color(0xFFD92C5C)
val DasGreen = Color(0xFF0B8A62)
val VerbViolet = Color(0xFF7239C9)
val AdjectiveAmber = Color(0xFFB45309)

/** The learner's strongest memory cue: each article, and each other part of speech, always has its own colour. */
val Tone.color: Color
    get() = when (this) {
        Tone.Der -> DerBlue
        Tone.Die -> DieRed
        Tone.Das -> DasGreen
        Tone.Verb -> VerbViolet
        Tone.Adjective -> AdjectiveAmber
    }

@Composable
private fun variable(resource: FontResource, weight: Int) = Font(
    resource = resource,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

// Atkinson Hyperlegible keeps similar letters apart, such as I and l, which matters when learning how words are spelled.
@Composable
private fun typography(): Typography {
    val family = FontFamily(listOf(400, 500, 600, 700).map { variable(Res.font.atkinson_hyperlegible_next, it) })
    return Typography(
        displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight(700), fontSize = 46.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp),
        headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight(700), fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.25).sp),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight(600), fontSize = 24.sp, lineHeight = 30.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight(600), fontSize = 18.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight(400), fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 18.sp),
    )
}

private val colors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = Slate,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    onSurfaceVariant = Slate,
    surfaceVariant = Track,
    surfaceContainerHighest = Track,
    outline = Hairline,
    outlineVariant = Hairline,
)

@Composable
fun WortschatzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography(), content = content)
}
