package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
  primary = DarkPrimary,
  secondary = DarkSecondary,
  tertiary = DarkTertiary,
  background = DarkBackground,
  surface = DarkSurface,
  onPrimary = DarkBackground,
  onSecondary = DarkBackground,
  onTertiary = DarkBackground,
  onBackground = DarkOnBackground,
  onSurface = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
  primary = LightPrimary,
  secondary = LightSecondary,
  tertiary = LightTertiary,
  background = LightBackground,
  surface = LightSurface,
  onPrimary = LightSurface,
  onSecondary = LightSurface,
  onTertiary = LightSurface,
  onBackground = LightOnBackground,
  onSurface = LightOnSurface
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Disable to enforce our custom, hand-crafted comfort colors
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
