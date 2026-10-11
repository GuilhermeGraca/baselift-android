package com.example.baselift.insights

import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.View.insights.components.PhotoWeightIndex
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class PhotoWeightIndexTest {
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private fun weight(id: Int, timestamp: String) = WeightLogEntity(id, 80f + id, time(timestamp))
    private val lisbon = TimeZone.getTimeZone("Europe/Lisbon")

    @Test
    fun `lookup preserves the first same-day match and equal timestamp ties`() {
        val first = weight(1, "2026-10-11T07:00:00Z")
        val tied = first.copy(id = 2, weightValue = 100f)
        val later = weight(3, "2026-10-11T15:00:00Z")
        val index = PhotoWeightIndex(listOf(first, tied, later), lisbon)
        assertEquals(first, index.weightAt(time("2026-10-11T20:00:00Z")))
        assertNull(index.weightAt(time("2026-10-12T12:00:00Z")))
        assertNull(PhotoWeightIndex(emptyList(), lisbon).weightAt(first.timestamp))
        // Preserve input-order semantics even for a differently ordered test snapshot.
        assertEquals(later, PhotoWeightIndex(listOf(later, first), lisbon).weightAt(first.timestamp))
    }

    @Test
    fun `spring daylight saving uses the local date across its 23-hour day`() {
        val first = weight(1, "2026-03-29T00:30:00Z")
        val next = weight(2, "2026-03-29T23:30:00Z")
        val index = PhotoWeightIndex(listOf(first, next), lisbon)
        assertEquals(first, index.weightAt(time("2026-03-29T22:59:59Z")))
        assertEquals(next, index.weightAt(time("2026-03-29T23:00:00Z")))
    }

    @Test
    fun `autumn daylight saving matches both repeated hours and the 25-hour day`() {
        val first = weight(1, "2026-10-24T23:30:00Z")
        val index = PhotoWeightIndex(listOf(first), lisbon)
        assertEquals(first, index.weightAt(time("2026-10-25T00:30:00Z")))
        assertEquals(first, index.weightAt(time("2026-10-25T01:30:00Z")))
        assertEquals(first, index.weightAt(time("2026-10-25T23:59:59Z")))
        assertNull(index.weightAt(time("2026-10-26T00:00:00Z")))
    }

    @Test
    fun `year boundaries and timezone changes do not mix calendar days`() {
        val lastYear = weight(1, "2025-12-31T12:00:00Z")
        val newYear = weight(2, "2026-01-01T12:00:00Z")
        val index = PhotoWeightIndex(listOf(lastYear, newYear), lisbon)
        assertEquals(lastYear, index.weightAt(time("2025-12-31T23:59:59Z")))
        assertEquals(newYear, index.weightAt(time("2026-01-01T00:00:00Z")))
        val midnight = weight(3, "2026-10-11T00:30:00Z")
        val photo = time("2026-10-11T09:30:00Z")
        assertEquals(midnight, PhotoWeightIndex(listOf(midnight), TimeZone.getTimeZone("Pacific/Honolulu")).weightAt(photo))
        assertNull(PhotoWeightIndex(listOf(midnight), lisbon).weightAt(photo - 24 * 60 * 60 * 1_000L))
        val precedingUtcDay = time("2026-10-10T23:30:00Z")
        assertEquals(midnight, PhotoWeightIndex(listOf(midnight), TimeZone.getTimeZone("Pacific/Honolulu")).weightAt(precedingUtcDay))
        assertNull(PhotoWeightIndex(listOf(midnight), TimeZone.getTimeZone("UTC")).weightAt(precedingUtcDay))
    }

    @Test
    fun `prepared lookup does not revisit raw history and new snapshots reflect edits`() {
        var reads = 0
        val first = weight(1, "2026-10-11T07:00:00Z")
        val data = object : AbstractList<WeightLogEntity>() {
            override val size = 1_000
            override fun get(index: Int): WeightLogEntity {
                reads++
                return first.copy(id = index + 1, timestamp = first.timestamp + index * 86_400_000L)
            }
        }
        val index = PhotoWeightIndex(data, lisbon)
        assertEquals(1_000, reads)
        reads = 0
        repeat(100) { assertEquals(first, index.weightAt(first.timestamp)) }
        assertEquals(0, reads)
        val edited = first.copy(weightValue = 75f)
        assertEquals(edited, PhotoWeightIndex(listOf(edited), lisbon).weightAt(first.timestamp))
        assertNull(PhotoWeightIndex(emptyList(), lisbon).weightAt(first.timestamp))
        assertEquals(first, index.weightAt(first.timestamp))
    }

    @Test
    fun `index owns its timezone snapshot`() {
        val zone = TimeZone.getTimeZone("UTC")
        val first = weight(1, "2026-10-11T00:30:00Z")
        val index = PhotoWeightIndex(listOf(first), zone)
        zone.rawOffset = -10 * 60 * 60 * 1_000
        assertEquals(first, index.weightAt(time("2026-10-11T12:00:00Z")))
    }
}
