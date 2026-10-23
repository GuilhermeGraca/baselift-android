package com.example.baselift.View.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** One subtle pulse per visible placeholder area; graphics layers avoid recomposing every block. */
@Composable
fun rememberSkeletonOpacity(): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeleton-pulse")
    return transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 0.84f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "opacity"
    )
}

@Composable
fun SkeletonBlock(
    modifier: Modifier,
    opacity: State<Float>,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    Box(modifier.graphicsLayer { alpha = opacity.value }.background(Color(0xFF414141), shape))
}
