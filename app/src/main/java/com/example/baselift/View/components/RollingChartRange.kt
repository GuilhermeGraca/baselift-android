package com.example.baselift.View.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private const val DAY_MILLIS = 24L * 60 * 60 * 1_000

// Composition-local memoization for immutable chart snapshots. Refresh on the same
// recompositions as before, but only scan again when a point expires or time moves back.
internal class RollingChartRange(
    private val data: List<ChartDataPoint>,
    private val days: Int?
) {
    private var preparedAt: Long? = null
    private var nextExpiry: Long? = null
    private var cached: List<ChartDataPoint> = emptyList()

    fun pointsAt(now: Long): List<ChartDataPoint> {
        val window = days?.let { it * DAY_MILLIS } ?: return data
        val previous = preparedAt
        val expiresAt = nextExpiry
        if (previous != null && now >= previous && (expiresAt == null || now < expiresAt)) {
            preparedAt = now
            return cached
        }
        cached = data.filter { now - it.xValue <= window }
        // The boundary is inclusive: a point expires one millisecond after x + window.
        nextExpiry = cached.minOfOrNull { it.xValue + window + 1 }
        preparedAt = now
        return cached
    }
}

@Composable
internal fun rememberChartRange(data: List<ChartDataPoint>, days: Int?): List<ChartDataPoint> {
    val range = remember(data, days) { RollingChartRange(data, days) }
    return range.pointsAt(System.currentTimeMillis())
}
