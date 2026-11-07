package com.lamp.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LampColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    tertiary = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0B0B0B),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    outline = Color.White,
)

@Composable
fun LampTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LampColors, content = content)
}

/** The look from the dock: black fill, rounded corners, white outline. */
fun Modifier.lampFrame(radius: Dp, stroke: Dp = 2.dp, fill: Color = Color.Black): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(fill, shape).border(stroke, Color.White, shape)
}
