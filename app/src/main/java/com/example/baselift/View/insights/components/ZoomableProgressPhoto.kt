package com.example.baselift.View.insights.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.View.theme.DarkSurface

@Composable
internal fun ZoomableProgressPhoto(photo: PhotoLogEntity, modifier: Modifier = Modifier) {
    var scale by remember(photo.id, photo.photoUri) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id, photo.photoUri) { mutableStateOf(Offset.Zero) }
    val uri = remember(photo.photoUri) { Uri.parse(photo.photoUri) }
    Box(
        modifier = modifier.fillMaxWidth().height(420.dp).zIndex(1f)
            .background(DarkSurface, RoundedCornerShape(8.dp))
            .pointerInput(photo.id, photo.photoUri) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    val fractionalX = centroid.x - size.width / 2
                    val fractionalY = centroid.y - size.height / 2
                    offset = Offset(
                        offset.x * (scale / oldScale) + pan.x - fractionalX * (scale - oldScale),
                        offset.y * (scale / oldScale) + pan.y - fractionalY * (scale - oldScale)
                    )
                }
            }
            .pointerInput(photo.id, photo.photoUri) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    scale = 1f
                    offset = Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Zoomed Progress Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Reads here update only the layer, not the photo/dialog composition.
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        )
    }
}
