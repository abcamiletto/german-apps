package com.abcamiletto.wortschatz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.abcamiletto.wortschatz.R
import com.abcamiletto.wortschatz.domain.Article

val Paper = Color(0xFFF6F7FB)
val Ink = Color(0xFF14213D)
val Slate = Color(0xFF5B6478)
val Hairline = Color(0xFFDDE1EA)
val Track = Color(0xFFE7EAF1)
val Stage = Color(0xFFE3E8F2)
val DerBlue = Color(0xFF2F5BEA)
val DieRed = Color(0xFFD92C5C)
val DasGreen = Color(0xFF0B8A62)

/** The learner's strongest memory cue: each article always appears in its own colour. */
val Article.color: Color
    get() = when (this) {
        Article.Der -> DerBlue
        Article.Die -> DieRed
        Article.Das -> DasGreen
    }

private fun variable(resource: Int, weight: Int) = Font(
    resId = resource,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(
    variable(R.font.bricolage_grotesque, 500),
    variable(R.font.bricolage_grotesque, 700),
    variable(R.font.bricolage_grotesque, 800),
)
val Body = FontFamily(
    variable(R.font.instrument_sans, 400),
    variable(R.font.instrument_sans, 500),
    variable(R.font.instrument_sans, 600),
)

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 46.sp, lineHeight = 50.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 24.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 18.sp),
)

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
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
