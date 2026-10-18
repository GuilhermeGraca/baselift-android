package com.example.baselift.viewmodel

import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.ViewModel.dashboard.DashboardAnalytics
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class WeeklyVolumeAnalyticsTest {
    private lateinit var originalZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before fun setup() {
        originalZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Lisbon"))
        Locale.setDefault(Locale.US)
    }

    @After fun teardown() {
        TimeZone.setDefault(originalZone)
        Locale.setDefault(originalLocale)
    }

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        GregorianCalendar(TimeZone.getDefault(), Locale.US).apply {
            clear(); set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun daysAfter(start: Long, days: Int, hour: Int = 0): Long =
        GregorianCalendar(TimeZone.getDefault(), Locale.US).apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis

    private fun set(id: Int, time: Long, weight: Float = 100f, reps: Int = 1) = SetLogEntity(
        id = id, sessionId = 1, exerciseId = id % 2 + 1, setNumber = id,
        weight = weight, reps = reps, timestamp = time, isCompleted = true
    )

    @Test fun `December and January stay together for both ISO week one and week fifty three`() {
        listOf(Triple(2014, 12, 29), Triple(2015, 12, 28), Triple(2018, 12, 31),
            Triple(2020, 12, 28), Triple(2024, 12, 30)).forEach { (year, month, day) ->
            val monday = timestamp(year, month, day)
            val nextMonday = daysAfter(monday, 7)
            val logs = (0..6).map { set(it + 1, daysAfter(monday, it, 12)) } +
                set(8, monday - 1, 90f) + set(9, nextMonday, 110f)
            val volumes = DashboardAnalytics { nextMonday }.setHistory(logs.reversed()).weeklyVolumes
            assertEquals("Year $year", listOf(daysAfter(monday, -7), monday, nextMonday),
                volumes.map { it.weekStartTimestamp })
            assertEquals("Year $year", listOf(90f, 700f, 110f), volumes.map { it.totalVolume })
        }
    }

    @Test fun `week boundaries follow local Monday midnight rather than UTC`() {
        listOf("Europe/Lisbon", "America/Los_Angeles", "Pacific/Kiritimati", "Asia/Kathmandu").forEach { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            val logs = listOf(
                set(1, timestamp(2024, 12, 29, 23, 59), 10f),
                set(2, timestamp(2024, 12, 30), 20f),
                set(3, timestamp(2025, 1, 5, 23, 59), 30f),
                set(4, timestamp(2025, 1, 6), 40f)
            )
            val volumes = DashboardAnalytics { logs.last().timestamp }.setHistory(logs).weeklyVolumes
            assertEquals(zone, listOf(timestamp(2024, 12, 23), timestamp(2024, 12, 30), timestamp(2025, 1, 6)),
                volumes.map { it.weekStartTimestamp })
            assertEquals(zone, listOf(10f, 50f, 40f), volumes.map { it.totalVolume })
        }
    }

    @Test fun `device locale cannot change Monday to Sunday week membership`() {
        val monday = timestamp(2024, 12, 30)
        val logs = listOf(set(1, monday, 20f), set(2, timestamp(2025, 1, 5, 23, 59), 30f))
        listOf(Locale.US, Locale.UK, Locale("pt", "PT"), Locale("ar", "SA"), Locale("th", "TH")).forEach { locale ->
            Locale.setDefault(locale)
            val volumes = DashboardAnalytics { monday }.setHistory(logs).weeklyVolumes
            assertEquals(locale.toString(), 1, volumes.size)
            assertEquals(locale.toString(), monday, volumes.single().weekStartTimestamp)
            assertEquals(locale.toString(), 50f, volumes.single().totalVolume, 0f)
        }
    }

    @Test fun `short and long daylight saving weeks use calendar boundaries and conserve volume`() {
        listOf(Triple(3, 23, 167), Triple(10, 19, 169)).forEach { (month, day, hours) ->
            val monday = timestamp(2026, month, day)
            val nextMonday = daysAfter(monday, 7)
            assertEquals(hours * 3_600_000L, nextMonday - monday)
            val logs = listOf(set(1, monday, 10f), set(2, nextMonday - 1, 20f), set(3, nextMonday, 30f))
            val volumes = DashboardAnalytics { nextMonday }.setHistory(logs).weeklyVolumes
            assertEquals(listOf(monday, nextMonday), volumes.map { it.weekStartTimestamp })
            assertEquals(listOf(30f, 30f), volumes.map { it.totalVolume })
        }
    }

    @Test fun `unordered sets and duplicate timestamps preserve totals unique buckets and daily trends`() {
        val monday = timestamp(2024, 12, 30)
        val logs = listOf(
            set(1, timestamp(2025, 1, 6), 30f, 10),
            set(2, timestamp(2024, 1, 1), 40f, 10),
            set(3, monday, 12.5f, 8),
            set(4, monday, 25f, 4),
            set(5, timestamp(2025, 1, 5), 20f, 5)
        )
        val analytics = DashboardAnalytics { monday }
        val history = analytics.setHistory(logs)
        val reversed = analytics.setHistory(logs.reversed())
        assertEquals(listOf(400f, 300f, 300f), history.weeklyVolumes.map { it.totalVolume })
        assertEquals(history.weeklyVolumes, reversed.weeklyVolumes)
        assertEquals(history.weeklyVolumes.size, history.weeklyVolumes.map { it.weekStartTimestamp }.distinct().size)
        assertEquals(1_000.0, history.weeklyVolumes.sumOf { it.totalVolume.toDouble() }, 0.0)
        assertEquals(1_000.0, history.dailyVolumes.sumOf { it.volume }, 0.0)
        assertEquals(4, analytics.exerciseCharts(logs).volumeTrend.size)
    }
}
