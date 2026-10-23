package com.example.baselift.repository

import com.example.baselift.Model.local.dao.NutritionDao
import com.example.baselift.Model.repository.NutritionDay
import com.example.baselift.Model.repository.NutritionRepository
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class NutritionRepositoryTest {
    private val dao = mockk<NutritionDao>(relaxed = true)
    private fun instant(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun `explicit day query uses supplied inclusive bounds without sampling a second clock`() {
        val repository = NutritionRepository(dao, { error("must not sample clock") }, { error("must not sample zone") })
        val day = NutritionDay.at(instant("2026-10-17T12:00:00Z"), TimeZone.getTimeZone("UTC"))
        repository.getLogsForDay(day)
        verify(exactly = 1) { dao.getLogsForTimeRange(instant("2026-10-17T00:00:00Z"), instant("2026-10-18T00:00:00Z") - 1) }
    }

    @Test fun `reset samples clock and zone once so crossing midnight cannot combine two dates`() = runTest {
        var clocks = 0; var zones = 0
        val repository = NutritionRepository(dao, {
            clocks++; if (clocks == 1) instant("2026-10-17T23:59:59.999Z") else instant("2026-10-18T00:00:00Z")
        }, { zones++; TimeZone.getTimeZone("UTC") })
        repository.resetTodayLogs()
        assertEquals(1, clocks); assertEquals(1, zones)
        coVerify(exactly = 1) { dao.deleteLogsInTimeRange(instant("2026-10-17T00:00:00Z"), instant("2026-10-18T00:00:00Z") - 1) }
    }

    @Test fun `reset uses the current local date rather than a previous display snapshot`() = runTest {
        var now = instant("2026-10-17T23:59:59Z")
        val repository = NutritionRepository(dao, { now }, { TimeZone.getTimeZone("UTC") })
        repository.getLogsForDay(NutritionDay.at(now, TimeZone.getTimeZone("UTC")))
        now = instant("2026-10-18T00:00:00Z")
        repository.resetTodayLogs()
        coVerify(exactly = 1) { dao.deleteLogsInTimeRange(now, instant("2026-10-19T00:00:00Z") - 1) }
    }

    @Test fun `read and reset preserve a short DST day with exact millisecond cutoffs`() = runTest {
        val now = instant("2026-03-29T12:00:00Z")
        val zone = TimeZone.getTimeZone("Europe/Lisbon")
        val repository = NutritionRepository(dao, { now }, { zone })
        repository.getLogsForDay(NutritionDay.at(now, zone)); repository.resetTodayLogs()
        verify(exactly = 1) { dao.getLogsForTimeRange(instant("2026-03-29T00:00:00Z"), instant("2026-03-29T23:00:00Z") - 1) }
        coVerify(exactly = 1) { dao.deleteLogsInTimeRange(instant("2026-03-29T00:00:00Z"), instant("2026-03-29T23:00:00Z") - 1) }
    }
}
