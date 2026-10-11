package com.example.baselift.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.View.components.*
import com.example.baselift.View.insights.components.WeightTrendSection
import com.example.baselift.View.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ChartInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val day = 86_400_000L

    private class ReadCountingList<T>(private val data: List<T>) : AbstractList<T>() {
        val reads = AtomicInteger()
        override val size get() = data.size
        override fun get(index: Int): T { reads.incrementAndGet(); return data[index] }
    }

    private fun points(count: Int): List<ChartDataPoint> {
        val now = System.currentTimeMillis()
        return (1..count).map { ChartDataPoint(now - (count - it) * day, it * 10f, "Point $it") }
    }

    private fun pixelsEqual(first: ImageBitmap, second: ImageBitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        for (y in 0 until first.height step 2) for (x in 0 until first.width step 2) {
            if (a[x, y] != b[x, y]) return false
        }
        return true
    }

    @Test
    fun goalAndScaleChangesReuseLineSamplingForUnchangedHistory() {
        val data = ReadCountingList(points(1_000))
        val target = mutableStateOf<Float?>(null)
        compose.setContent { MaterialTheme {
            InteractiveChartWithControls("History", data, target.value, { target.value = it })
        } }
        compose.waitForIdle()
        data.reads.set(0)
        compose.runOnIdle { target.value = 1_000f }
        compose.waitForIdle()
        assertTrue("Goal edits should not resample 1,000 points", data.reads.get() < 20)
        data.reads.set(0)
        compose.onNodeWithContentDescription("Toggle Scale").performClick()
        assertTrue("Scale changes should reuse the same sample", data.reads.get() < 20)
    }

    @Test
    fun summaryChangesReuseBarAggregationForUnchangedHistory() {
        val data = ReadCountingList(points(1_000))
        val summary = mutableStateOf("Summary")
        compose.setContent { MaterialTheme {
            BarChartWithControls("Volume", data, formatYLabel = { it.toString() }, formatXLabel = { it.toString() },
                summaryValue = summary.value, summarySubtitle = null)
        } }
        compose.waitForIdle()
        data.reads.set(0)
        compose.runOnIdle { summary.value = "Updated summary" }
        compose.onNodeWithText("Updated summary").assertIsDisplayed()
        assertEquals("Summary edits must not aggregate the raw history again", 0, data.reads.get())
    }

    @Test
    fun targetChangesDoNotRemapWeightHistoryLabels() {
        val data = ReadCountingList((1..1_000).map { WeightLogEntity(it, 80f, it * day) })
        val target = mutableStateOf<Float?>(null)
        compose.setContent { MaterialTheme { WeightTrendSection(data, target.value, { target.value = it }) } }
        compose.waitForIdle()
        data.reads.set(0)
        compose.runOnIdle { target.value = 75f }
        compose.waitForIdle()
        assertEquals("A target edit must not format 1,000 weight labels again", 0, data.reads.get())
    }

    @Test
    fun selectedLineTooltipClearsOnScaleChangeAndHistoryShortening() {
        val data = mutableStateOf(points(3))
        val chronological = mutableStateOf(true)
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxWidth().height(250.dp).testTag("chart")) {
                CustomCanvasChart(data.value, null, NeonGreen, ElectricBlue, chronological.value,
                    formatYLabel = { it.toString() }, formatXLabel = { it.toString() })
            }
        } }
        val chart = compose.onNodeWithTag("chart")
        val beforeSelection = chart.captureToImage()
        // Last point of the padded plot: y-range 6..34 for the values 10,20,30.
        chart.performTouchInput {
            val density = (height / 250f)
            val innerHeight = height - 32 * density
            click(Offset(width - 24 * density - 2, 16 * density + 40 + (innerHeight - 80) / 7))
        }
        assertFalse("The tap must select a point before testing invalidation", pixelsEqual(beforeSelection, chart.captureToImage()))
        compose.runOnIdle { chronological.value = false }
        // Equally spaced dates have identical geometry in either scale.
        assertTrue("Changing scale must clear the old tooltip", pixelsEqual(beforeSelection, chart.captureToImage()))
        chart.performTouchInput {
            val density = height / 250f
            click(Offset(width - 24 * density - 2, 16 * density + 40 + (height - 32 * density - 80) / 7))
        }
        assertFalse(pixelsEqual(beforeSelection, chart.captureToImage()))
        compose.runOnIdle { data.value = data.value.take(1) }
        compose.waitForIdle() // A retained index 2 previously crashed this draw.
        chart.captureToImage()
        compose.runOnIdle { data.value = emptyList() }
        compose.onNodeWithText("No data available yet.").assertIsDisplayed()
    }

    @Test
    fun barTooltipClearsWhenPlottedValuesChangeWithoutChangingThePointCount() {
        val data = mutableStateOf(points(3))
        val generation = mutableStateOf(0)
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxWidth().testTag("chart")) {
                key(generation.value) {
                    BarChartWithControls("", data.value, formatYLabel = { it.toString() },
                        formatXLabel = { it.toString() }, summaryValue = null, summarySubtitle = null,
                        showTimeFilters = false)
                }
            }
        } }
        val chart = compose.onNodeWithTag("chart")
        val beforeSelection = chart.captureToImage()
        chart.performTouchInput {
            val density = height / 250f
            val innerWidth = width - 32 * density
            click(Offset(8 * density + 80 + (innerWidth - 80) * 5 / 6, height / 2f))
        }
        assertFalse("The tap must select a bar before testing invalidation", pixelsEqual(beforeSelection, chart.captureToImage()))
        compose.runOnIdle { data.value = data.value.map { it.copy(yValue = it.yValue + 1f, tooltipLabel = "Updated") } }
        val refreshed = chart.captureToImage()
        compose.runOnIdle { generation.value++ }
        assertTrue("Refreshed data should look like a fresh chart with no tooltip", pixelsEqual(refreshed, chart.captureToImage()))
    }

    @Test
    fun barRendererIgnoresASelectionThatNoLongerExists() {
        val data = mutableStateOf(points(3))
        val selected = mutableStateOf<Int?>(2)
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxWidth().height(250.dp).testTag("chart")) {
                CustomCanvasBarChart(data.value, NeonGreen, { it.toString() }, { it.toString() },
                    selected.value, Offset(100f, 100f), onSelectionChange = { index, _ -> selected.value = index })
            }
        } }
        val chart = compose.onNodeWithTag("chart")
        chart.captureToImage()
        compose.runOnIdle { data.value = data.value.take(1) }
        val missingSelection = chart.captureToImage()
        compose.runOnIdle { selected.value = null }
        assertTrue("An obsolete selection must neither crash nor dim all bars", pixelsEqual(missingSelection, chart.captureToImage()))
        compose.runOnIdle { selected.value = -1 }
        assertTrue(pixelsEqual(missingSelection, chart.captureToImage()))
    }
}
