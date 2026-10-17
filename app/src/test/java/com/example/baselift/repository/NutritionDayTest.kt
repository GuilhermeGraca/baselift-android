package com.example.baselift.repository

import com.example.baselift.Model.repository.NutritionDay
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class NutritionDayTest {
    private fun instant(value: String) = Instant.parse(value).toEpochMilli()
    private fun day(value: String, zone: String) = NutritionDay.at(instant(value), TimeZone.getTimeZone(zone))

    @Test fun `same local day has one stable key and adjacent days have exclusive boundaries`() {
        val first = day("2026-10-17T00:00:00Z", "UTC")
        assertEquals(first, day("2026-10-17T23:59:59.999Z", "UTC"))
        val next = day("2026-10-18T00:00:00Z", "UTC")
        assertEquals(first.endExclusiveMillis, next.startMillis)
        assertEquals(86_400_000L, first.endExclusiveMillis - first.startMillis)
        assertNotEquals(first, next)
    }

    @Test fun `zone changes alter query identity even at the same instant`() {
        val utc = day("2026-10-17T00:30:00Z", "UTC")
        val hawaii = day("2026-10-17T00:30:00Z", "Pacific/Honolulu")
        assertNotEquals(utc, hawaii)
        assertEquals(instant("2026-10-16T10:00:00Z"), hawaii.startMillis)
        assertEquals(instant("2026-10-17T10:00:00Z"), hawaii.endExclusiveMillis)
    }

    @Test fun `day lengths follow calendar transitions rather than a fixed 24 hours`() {
        assertEquals(23 * 3_600_000L, day("2026-03-29T12:00:00Z", "Europe/Lisbon").let { it.endExclusiveMillis - it.startMillis })
        assertEquals(25 * 3_600_000L, day("2026-10-25T12:00:00Z", "Europe/Lisbon").let { it.endExclusiveMillis - it.startMillis })
        assertEquals(23.5 * 3_600_000L, day("2026-10-04T12:00:00Z", "Australia/Lord_Howe").let { (it.endExclusiveMillis - it.startMillis).toDouble() }, 0.0)
    }

    @Test fun `missing and repeated midnights include the complete local date`() {
        // java.time is a JVM-test oracle; production stays compatible with Android API 24.
        listOf("America/Sao_Paulo" to "2018-11-04", "America/Havana" to "2026-11-01",
            "Pacific/Apia" to "2011-12-29", "Europe/Lisbon" to "2026-10-25").forEach { (zone, date) ->
            val local = LocalDate.parse(date)
            val expectedStart = local.atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()
            val expectedEnd = local.plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()
            val actual = NutritionDay.at(expectedStart + 12 * 3_600_000L, TimeZone.getTimeZone(zone))
            assertEquals("$zone start", expectedStart, actual.startMillis)
            assertEquals("$zone end", expectedEnd, actual.endExclusiveMillis)
        }
    }

    @Test fun `date and zone samples are not mutated by boundary calculations`() {
        val zone = TimeZone.getTimeZone("Europe/Lisbon")
        val original = zone.clone()
        val value = NutritionDay.at(instant("2026-10-17T12:00:00Z"), zone)
        assertEquals(original, zone)
        zone.rawOffset = 0
        assertEquals(instant("2026-10-16T23:00:00Z"), value.startMillis)
    }
}
