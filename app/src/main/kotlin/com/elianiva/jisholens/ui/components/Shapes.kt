package com.elianiva.jisholens.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/** Turns off looping decoration, for screenshots and for people who ask the system for less motion. */
val LocalDecorativeMotion = compositionLocalOf { true }

/**
 * A decorative container cut to one of the Material shape-library polygons. The shape turns slowly
 * on its own; the content stays upright.
 */
@Composable
fun ShapeBadge(
    polygon: RoundedPolygon,
    containerColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    spinSeconds: Int = 0,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animate = LocalDecorativeMotion.current && spinSeconds > 0
    val rotation = if (animate) {
        val transition = rememberInfiniteTransition(label = "badge-spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(spinSeconds * 1000, easing = LinearEasing), RepeatMode.Restart),
            label = "badge-angle",
        )
        angle
    } else {
        0f
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .clip(polygon.toShape())
                .background(containerColor),
        )
        content()
    }
}

/**
 * A corner radius that gives way when pressed, so a tile reads as something you squeeze.
 * Uses the theme's fast spatial spring, which overshoots slightly.
 */
@Composable
fun pressedCornerShape(
    interactionSource: MutableInteractionSource,
    rest: Dp = 32.dp,
    pressed: Dp = 14.dp,
): RoundedCornerShape {
    val isPressed by interactionSource.collectIsPressedAsState()
    val radius by animateDpAsState(
        targetValue = if (isPressed) pressed else rest,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressed-corner",
    )
    return RoundedCornerShape(radius.coerceAtLeast(0.dp))
}

/** Shapes used to tell dictionaries apart at a glance, in rotation. */
val DictionaryShapes: List<RoundedPolygon>
    get() = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Sunny,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Flower,
        MaterialShapes.SoftBurst,
        MaterialShapes.Pentagon,
    )

@Composable
fun WithoutDecorativeMotion(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDecorativeMotion provides false, content = content)
}
