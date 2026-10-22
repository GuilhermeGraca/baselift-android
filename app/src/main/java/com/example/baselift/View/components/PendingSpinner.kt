package com.example.baselift.View.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** A rotating pending indicator. Its lifetime is controlled by the caller's real operation state. */
@Composable
fun PendingSpinner(
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp
) {
    val transition = rememberInfiniteTransition(label = "pending-spinner")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        drawArc(
            color = color,
            startAngle = rotation.value - 90f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/**
 * Leave fast operations visually immediate. If work is still pending after the short reveal
 * threshold, fade in the rotating arc; disposing this composable never delays completion.
 */
@Composable
fun DelayedPendingSpinner(
    color: Color,
    modifier: Modifier = Modifier,
    spinnerModifier: Modifier = Modifier,
    spinnerSize: Dp = 20.dp,
    strokeWidth: Dp = 2.dp,
    placeholder: @Composable () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(180)
        visible = true
    }
    Box(modifier) {
        Crossfade(
            targetState = visible,
            animationSpec = tween(durationMillis = 120),
            modifier = Modifier.fillMaxSize(),
            label = "pending-indicator-reveal"
        ) { showSpinner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (showSpinner) {
                    PendingSpinner(color, Modifier.size(spinnerSize).then(spinnerModifier), strokeWidth)
                } else {
                    placeholder()
                }
            }
        }
    }
}
