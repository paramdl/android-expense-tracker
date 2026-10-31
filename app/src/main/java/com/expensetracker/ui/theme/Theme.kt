package com.expensetracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// A restrained two-accent palette: deep teal-green for money in, warm clay for money out.
private val Green = Color(0xFF12805C)
private val GreenLight = Color(0xFF6FD3AC)
private val Clay = Color(0xFFC2553F)
private val ClayLight = Color(0xFFFFB4A1)
private val Ink = Color(0xFF16191C)

val PositiveLight = Color(0xFF12805C)
val PositiveDark = Color(0xFF6FD3AC)
val NegativeLight = Color(0xFFC2553F)
val NegativeDark = Color(0xFFFF9E85)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2E1),
    onPrimaryContainer = Color(0xFF00301F),
    secondary = Color(0xFF4C6355),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9D7),
    onSecondaryContainer = Color(0xFF092014),
    tertiary = Clay,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD1),
    onTertiaryContainer = Color(0xFF3F0B02),
    background = Color(0xFFFBFAF8),
    onBackground = Ink,
    surface = Color(0xFFFBFAF8),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEFEB),
    onSurfaceVariant = Color(0xFF474A46),
    outline = Color(0xFFB6BAB4),
    outlineVariant = Color(0xFFDCDFD9),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFF8CF0C7),
    secondary = Color(0xFFB3CCBC),
    onSecondary = Color(0xFF1E3529),
    secondaryContainer = Color(0xFF344B3E),
    onSecondaryContainer = Color(0xFFCEE9D7),
    tertiary = ClayLight,
    onTertiary = Color(0xFF5F1608),
    tertiaryContainer = Color(0xFF7D2C1B),
    onTertiaryContainer = Color(0xFFFFDAD1),
    background = Color(0xFF101312),
    onBackground = Color(0xFFE2E3DF),
    surface = Color(0xFF101312),
    onSurface = Color(0xFFE2E3DF),
    surfaceVariant = Color(0xFF1D211F),
    onSurfaceVariant = Color(0xFFC2C8C3),
    outline = Color(0xFF5A605B),
    outlineVariant = Color(0xFF2B302D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp
    )
)

@Composable
fun ExpenseTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}

/** Green for money in, clay for money out, resolved for the active scheme. */
@Composable
fun positiveColor(): Color = if (isDarkNow()) PositiveDark else PositiveLight

@Composable
fun negativeColor(): Color = if (isDarkNow()) NegativeDark else NegativeLight

@Composable
private fun isDarkNow(): Boolean = MaterialTheme.colorScheme.background.luminanceIsDark()

private fun Color.luminanceIsDark(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

@Suppress("unused")
@Composable
fun currentContext() = LocalContext.current
