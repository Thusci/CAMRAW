package com.camraw.app.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Immutable
data class PreviewBackdrop(
    val luminance: Float = 0.42f,
    val temperature: Float = 0.5f,
)

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    backdrop: PreviewBackdrop,
    shape: Shape = RoundedCornerShape(28.dp),
    blurRadius: Dp = 24.dp,
    tonalOpacity: Float = 0.42f,
    materialOpacity: Float = 0.30f,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalGlassPalette.current
    val animatedLuma by animateFloatAsState(
        targetValue = backdrop.luminance.coerceIn(0f, 1f),
        animationSpec = MotionSpec.responsive,
        label = "glass-luma",
    )
    val blur by animateFloatAsState(
        targetValue = blurRadius.value,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 220f),
        label = "glass-blur",
    )
    val cool = palette.coolTint.copy(alpha = (0.12f + animatedLuma * 0.10f) * tonalOpacity)
    val warm = palette.warmTint.copy(alpha = (0.10f + (1f - animatedLuma) * 0.08f) * tonalOpacity)
    val base = Color.White.copy(alpha = (0.10f + animatedLuma * 0.08f) * tonalOpacity)

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val noiseStep = 9.dp.toPx().coerceAtLeast(6f)
                onDrawWithContent {
                    drawRect(
                        color = Color(0xFF070A0D).copy(
                            alpha = (materialOpacity + (1f - animatedLuma) * 0.08f).coerceIn(0f, 0.86f),
                        ),
                    )
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(cool, base, warm),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    )
                    var x = 0f
                    while (x < size.width) {
                        var y = ((x / noiseStep).roundToInt() % 3) * noiseStep / 3f
                        while (y < size.height) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.018f),
                                radius = 0.65.dp.toPx(),
                                center = Offset(x, y),
                                blendMode = BlendMode.Screen,
                            )
                            y += noiseStep
                        }
                        x += noiseStep
                    }
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                palette.highlight.copy(alpha = 0.24f),
                                Color.Transparent,
                                palette.shadow.copy(alpha = 0.18f),
                            ),
                        ),
                        blendMode = BlendMode.Softlight,
                    )
                    drawRoundRect(
                        color = palette.edge,
                        size = Size(size.width, size.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                    )
                    drawLine(
                        color = palette.highlight.copy(alpha = 0.52f),
                        start = Offset(10.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 10.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 0.8.dp.toPx(),
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    renderEffect = platformBlur(blur)
                    alpha = 0.52f
                }
                .background(Color.White.copy(alpha = 0.05f)),
        )
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun GlassControl(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    backdrop: PreviewBackdrop = PreviewBackdrop(),
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = MotionSpec.snap,
        label = "control-scale",
    )
    LiquidGlassSurface(
        modifier = modifier
            .defaultMinSize(52.dp, 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        backdrop = backdrop,
        shape = RoundedCornerShape(18.dp),
        blurRadius = if (selected) 30.dp else 20.dp,
        tonalOpacity = if (selected) 0.62f else 0.42f,
        materialOpacity = if (selected) 0.46f else 0.36f,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) Color(0xFFB9ECFF) else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun GlassTextButton(
    text: String,
    backdrop: PreviewBackdrop,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LiquidGlassSurface(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        backdrop = backdrop,
        shape = RoundedCornerShape(999.dp),
        blurRadius = 18.dp,
        materialOpacity = 0.42f,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GlassBottomSheet(
    visible: Boolean,
    backdrop: PreviewBackdrop,
    modifier: Modifier = Modifier,
    enter: EnterTransition = androidx.compose.animation.slideInVertically(
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        initialOffsetY = { it / 2 },
    ) + androidx.compose.animation.fadeIn(),
    exit: ExitTransition = androidx.compose.animation.slideOutVertically(
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 300f),
        targetOffsetY = { it / 2 },
    ) + androidx.compose.animation.fadeOut(),
    content: @Composable BoxScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        LiquidGlassSurface(
            backdrop = backdrop,
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            blurRadius = 34.dp,
            tonalOpacity = 0.64f,
            materialOpacity = 0.72f,
            contentPadding = PaddingValues(20.dp),
            content = content,
        )
    }
}

@Composable
fun ShutterButton(
    busy: Boolean,
    backdrop: PreviewBackdrop,
    onClick: () -> Unit,
) {
    val pulse by animateFloatAsState(
        targetValue = if (busy) 0.82f else 1f,
        animationSpec = MotionSpec.expressive,
        label = "shutter-pulse",
    )
    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.72f)),
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .defaultMinSize(82.dp, 82.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            backdrop = backdrop,
            shape = RoundedCornerShape(999.dp),
            blurRadius = 30.dp,
            tonalOpacity = 0.82f,
            materialOpacity = 0.44f,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = if (busy) 0.42f else 0.84f),
                    radius = size.minDimension * 0.29f,
                    center = center,
                )
            }
        }
    }
}
