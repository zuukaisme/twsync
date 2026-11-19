package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class TwsExtendedColors(
  val isDark: Boolean,
  val canvasBg: Color,
  val cardSurface: Color,
  val cardElevated: Color,
  val chromeBg: Color,
  val chromeSurface: Color,
  val chromeOnSurface: Color,
  val chromeBorder: Color,
  val border: Color,
  val cyan: Color,
  val cyanContainer: Color,
  val teal: Color,
  val tealContainer: Color,
  val amber: Color,
  val amberContainer: Color,
  val coral: Color,
  val coralContainer: Color,
  val inkPrimary: Color,
  val inkSecondary: Color,
  val inkMuted: Color,
)

private val LightTwsColors =
  TwsExtendedColors(
    isDark = false,
    canvasBg = LightCanvasBg,
    cardSurface = LightCardSurface,
    cardElevated = LightCardElevated,
    chromeBg = LightChromeDark,
    chromeSurface = LightChromeElevated,
    chromeOnSurface = Color(0xFFF8FAFC),
    chromeBorder = LightChromeBorder,
    border = LightBorder,
    cyan = LightCyan,
    cyanContainer = LightCyanContainer,
    teal = LightTeal,
    tealContainer = LightTealContainer,
    amber = LightAmber,
    amberContainer = LightAmberContainer,
    coral = LightCoral,
    coralContainer = LightCoralContainer,
    inkPrimary = LightInkPrimary,
    inkSecondary = LightInkSecondary,
    inkMuted = LightInkMuted,
  )

private val DarkTwsColors =
  TwsExtendedColors(
    isDark = true,
    canvasBg = DarkCanvasBg,
    cardSurface = DarkCardSurface,
    cardElevated = DarkCardElevated,
    chromeBg = DarkChromeDark,
    chromeSurface = DarkChromeElevated,
    chromeOnSurface = DarkInkPrimary,
    chromeBorder = DarkBorder,
    border = DarkBorder,
    cyan = DarkCyan,
    cyanContainer = DarkCyanContainer,
    teal = DarkTeal,
    tealContainer = DarkTealContainer,
    amber = DarkAmber,
    amberContainer = DarkAmberContainer,
    coral = DarkCoral,
    coralContainer = DarkCoralContainer,
    inkPrimary = DarkInkPrimary,
    inkSecondary = DarkInkSecondary,
    inkMuted = DarkInkMuted,
  )

val LocalTwsColors = staticCompositionLocalOf { LightTwsColors }

object TwsTheme {
  val colors: TwsExtendedColors
    @Composable get() = LocalTwsColors.current
}

private val TwsLightColorScheme =
  lightColorScheme(
    primary = LightChromeDark,
    onPrimary = Color(0xFFF8FAFC),
    primaryContainer = LightCyanContainer,
    onPrimaryContainer = LightCyan,
    secondary = LightTeal,
    onSecondary = Color.White,
    secondaryContainer = LightTealContainer,
    onSecondaryContainer = LightTeal,
    tertiary = LightAmber,
    onTertiary = Color.White,
    tertiaryContainer = LightAmberContainer,
    onTertiaryContainer = LightAmber,
    error = LightCoral,
    onError = Color.White,
    errorContainer = LightCoralContainer,
    onErrorContainer = LightCoral,
    background = LightCanvasBg,
    onBackground = LightInkPrimary,
    surface = LightCardSurface,
    onSurface = LightInkPrimary,
    surfaceVariant = LightCardElevated,
    onSurfaceVariant = LightInkSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
  )

private val TwsDarkColorScheme =
  darkColorScheme(
    primary = DarkCyan,
    onPrimary = Color(0xFF041C2C),
    primaryContainer = DarkCyanContainer,
    onPrimaryContainer = DarkCyan,
    secondary = DarkTeal,
    onSecondary = Color(0xFF04221E),
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = DarkTeal,
    tertiary = DarkAmber,
    onTertiary = DarkCanvasBg,
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = DarkAmber,
    error = DarkCoral,
    onError = DarkCanvasBg,
    errorContainer = DarkCoralContainer,
    onErrorContainer = DarkCoral,
    background = DarkCanvasBg,
    onBackground = DarkInkPrimary,
    surface = DarkCardSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkCardElevated,
    onSurfaceVariant = DarkInkSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
  )

private val TwsShapes =
  Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
  )

@Composable
fun TwsBridgeTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val extendedColors = if (darkTheme) DarkTwsColors else LightTwsColors
  val colorScheme = if (darkTheme) TwsDarkColorScheme else TwsLightColorScheme

  CompositionLocalProvider(LocalTwsColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = TwsTypography,
      shapes = TwsShapes,
      content = content,
    )
  }
}
