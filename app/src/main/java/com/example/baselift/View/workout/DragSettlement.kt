package com.example.baselift.View.workout

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.withTimeoutOrNull

internal data class DraggedItemPlacement(val index: Int, val offset: Int, val size: Int)

// Wait for actual layout frames, including expansion of cards above the dropped item.
// A missing item is no longer visible; never scroll to a replacement at its old index.
internal suspend fun awaitStableDraggedItemOffset(
    expectedIndex: Int,
    placement: () -> DraggedItemPlacement?
): Int? = withTimeoutOrNull(500L) {
    var previous: DraggedItemPlacement? = null
    var stableOffset: Int? = null
    while (stableOffset == null) {
        withFrameNanos { }
        val current = placement() ?: return@withTimeoutOrNull null
        if (current.index == expectedIndex && current.size > 0) {
            if (current == previous) stableOffset = current.offset
            previous = current
        } else {
            previous = null
        }
    }
    stableOffset
}
