package com.example.baselift.charts

import com.example.baselift.View.components.ChartDataPoint
import com.example.baselift.View.components.RollingChartRange
import com.example.baselift.View.components.aggregateBarData
import com.example.baselift.View.components.downsampleLTTB
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class ChartPreparationTest {
    private val day = 86_400_000L
    private val start = 1_767_225_600_000L // Jan 1, 2026 UTC
    private lateinit var previousZone: TimeZone

    @Before fun setup() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }
    @After fun teardown() = TimeZone.setDefault(previousZone)
    private fun point(timestamp: Long, value: Float = 1f) = ChartDataPoint(timestamp, value, "point")

    @Test
    fun `cached ranges keep inclusive boundaries input order and future points`() {
        for (days in listOf(7, 30, 90, 365)) {
            val oldest = point(start - days * day)
            val future = point(start + day)
            val current = point(start)
            val data = listOf(future, oldest, point(oldest.xValue - 1), current)
            val range = RollingChartRange(data, days)
            assertEquals(listOf(future, oldest, current), range.pointsAt(start))
            assertEquals(listOf(future, current), range.pointsAt(start + 1))
            // A clock correction must allow previously expired points back into the range.
            assertEquals(listOf(future, oldest, current), range.pointsAt(start))
        }
    }

    @Test
    fun `unchanged membership does not rescan a long history`() {
        var reads = 0
        val data = object : AbstractList<ChartDataPoint>() {
            override val size = 1_000
            override fun get(index: Int): ChartDataPoint {
                reads++
                return point(start + index * day)
            }
        }
        val range = RollingChartRange(data, 7)
        val initial = range.pointsAt(start)
        reads = 0
        for (milliseconds in 1L..100L) assertSame(initial, range.pointsAt(start + milliseconds))
        assertEquals(0, reads)
        val expired = range.pointsAt(start + 7 * day + 1)
        assertEquals(999, expired.size)
        assertTrue(reads > 0)
        reads = 0
        assertSame(expired, range.pointsAt(start + 7 * day + 2))
        assertEquals(0, reads)
    }

    @Test
    fun `all and empty ranges retain their results without stale clock corrections`() {
        val data = listOf(point(start))
        val all = RollingChartRange(data, null)
        assertSame(data, all.pointsAt(start + 1_000 * day))
        assertSame(data, all.pointsAt(start - day))
        val range = RollingChartRange(data, 7)
        val empty = range.pointsAt(start + 8 * day)
        assertTrue(empty.isEmpty())
        assertSame(empty, range.pointsAt(start + 9 * day))
        assertEquals(data, range.pointsAt(start + 7 * day))
    }

    @Test
    fun `line sampling retains endpoints and the existing triangle winners`() {
        val data = listOf(0f, 1f, 10f, 1f, 5f, 0f).mapIndexed { index, value -> point(start + index * day, value) }
        assertSame(data, downsampleLTTB(data, 20))
        assertEquals(listOf(data[0], data[2], data[3], data[5]), downsampleLTTB(data, 4))
        val dense = (0..99).map { point(start + it * day, (it % 7).toFloat()) }
        val sampled = downsampleLTTB(dense, 20)
        assertEquals(20, sampled.size)
        assertEquals(dense.first(), sampled.first())
        assertEquals(dense.last(), sampled.last())
        assertTrue(sampled.zipWithNext().all { (a, b) -> a.xValue < b.xValue })
        assertTrue(downsampleLTTB(emptyList(), 20).isEmpty())
    }

    @Test
    fun `bar aggregation preserves averages dates and sparse tooltips`() {
        val data = (0..69).map { point(start + it * day, it.toFloat()) }
        val bars = aggregateBarData(data, 35)
        assertEquals(35, bars.size)
        assertEquals(start + day / 2, bars.first().xValue)
        assertEquals(0.5f, bars.first().yValue, 0f)
        assertEquals(68.5f, bars.last().yValue, 0f)
        assertEquals("Avg: 0.5\nJan 01 - Jan 02", bars.first().tooltipLabel)
        val sparse = data.take(2)
        assertSame(sparse, aggregateBarData(sparse, 35))
        assertTrue(aggregateBarData(emptyList(), 35).isEmpty())
    }
}
