package com.camraw.app.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect

private val CamrawDark = darkColorScheme(
    background = Color.Black,
    surface = Color(0xFF09090A),
    primary = Color(0xFFEDEDEA),
    secondary = Color(0xFF8EDCF7),
    tertiary = Color(0xFFC7F7D0),
    onBackground = Color(0xFFF7F7F2),
    onSurface = Color(0xFFF7F7F2),
)

@Immutable
data class GlassPalette(
    val highlight: Color = Color.White.copy(alpha = 0.72f),
    val edge: Color = Color.White.copy(alpha = 0.28f),
    val shadow: Color = Color.Black.copy(alpha = 0.24f),
    val coolTint: Color = Color(0xFFB9ECFF),
    val warmTint: Color = Color(0xFFFFF2D0),
)

val LocalGlassPalette = staticCompositionLocalOf { GlassPalette() }

object MotionSpec {
    val responsive = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMedium,
    )
    val expressive = spring<Float>(
        dampingRatio = 0.66f,
        stiffness = Spring.StiffnessLow,
    )
    val snap = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessHigh,
    )
}

@Composable
fun LiquidGlassTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = CamrawDark,
        typography = MaterialTheme.typography,
        content = content,
    )
}

fun platformBlur(radius: Float) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    RenderEffect
        .createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        .asComposeRenderEffect()
} else {
    null
}
