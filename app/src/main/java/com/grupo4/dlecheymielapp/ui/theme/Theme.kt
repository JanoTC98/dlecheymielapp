package com.grupo4.dlecheymielapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand tokens, identical to `--nh-*` in the web theme. */
object Brand {
    val Ink = Color(0xFF2D1E4A)
    val Violet = Color(0xFF6E508C)
    val Lila = Color(0xFFA981FE)
    val Lilac = Color(0xFFE0AFFF)
    val Mist = Color(0xFFF1D7FF)
    val Background = Color(0xFFFBF7FF)
    val Error = Color(0xFFB3261E)
    val ErrorContainer = Color(0xFFFDECEA)
}

private val Colors = lightColorScheme(
    primary = Brand.Violet,
    onPrimary = Color.White,
    primaryContainer = Brand.Mist,
    onPrimaryContainer = Brand.Ink,
    secondary = Brand.Lila,
    onSecondary = Color.White,
    tertiary = Brand.Lilac,
    background = Brand.Background,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    onSurfaceVariant = Brand.Violet,
    outline = Brand.Lilac,
    outlineVariant = Brand.Mist,
    error = Brand.Error,
    errorContainer = Brand.ErrorContainer,
    onErrorContainer = Brand.Error,
)

// The web uses Montserrat; until the font is bundled the system sans-serif carries the same heavy-title look.
private val Display = FontFamily.SansSerif

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp),
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun DLecheTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, shapes = AppShapes, content = content)
}
