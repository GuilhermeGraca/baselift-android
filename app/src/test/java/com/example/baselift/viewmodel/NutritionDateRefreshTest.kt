package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.NutritionDay
import com.example.baselift.ViewModel.nutrition.NutritionViewModel
import com.example.baselift.fakes.FakeNutritionRepository
import com.example.baselift.fakes.FakeUserRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionDateRefreshTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val store = ViewModelStore()
    private lateinit var vm: NutritionViewModel
    private lateinit var repository: FakeNutritionRepository
    private var base = 0L
    private var zone = TimeZone.getTimeZone("UTC")
    private var clockReads = 0
    private var activeQueries = 0
    private var templateStarts = 0
    private val days = mutableListOf<NutritionDay>()
    private val meal = MealTemplateEntity(1, "Lunch", "restaurant", 600, 40.0, 70.0, 10.0)
    private fun instant(value: String) = Instant.parse(value).toEpochMilli()

    @After fun tearDown() { store.clear() }
    private suspend fun TestScope.prepare(time: String, zoneId: String = "UTC") {
        base = instant(time); zone = TimeZone.getTimeZone(zoneId)
        val clock = { base + testScheduler.currentTime }
        repository = FakeNutritionRepository(clock, { zone })
        repository.insertMealTemplate(meal)
        val users = FakeUserRepository().also { it.saveUser(UserEntity(dailyCaloriesGoal = 2500)) }
        val source = object : INutritionRepository by repository {
            override fun getLogsForDay(day: NutritionDay): Flow<List<NutritionLogEntity>> = flow {
                days += day; activeQueries++
                try { emitAll(repository.getLogsForDay(day)) } finally { activeQueries-- }
            }
            override fun getAllMealTemplates() = flow {
                templateStarts++; emitAll(repository.getAllMealTemplates())
            }
        }
        vm = NutritionViewModel(source, users, { clockReads++; clock() }, { zone })
        store.put("nutrition", vm)
    }
    private fun TestScope.watch() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    private suspend fun seed(name: String, time: String, calories: Int) = repository.insertNutritionLog(
        NutritionLogEntity(name = name, timestamp = instant(time), calories = calories, protein = 10.0, carbs = 20.0, fats = 5.0))

    @Test fun `same day resume events share one query and live writes still publish`() = runTest {
        prepare("2026-10-17T12:00:00Z"); watch(); watch(); runCurrent()
        repeat(5) { vm.refreshDate(); runCurrent() }
        advanceTimeBy(30_000); runCurrent(); vm.refreshDate(); runCurrent()
        assertEquals(1, days.size); assertEquals(1, activeQueries); assertEquals(1, templateStarts)
        vm.addQuickLog(100, isCaloriesOnly = true); runCurrent()
        assertEquals(100, vm.uiState.value.consumedCalories); assertEquals(1, days.size)
    }

    @Test fun `visible midnight changes the day once with fresh logs totals and retained targets templates`() = runTest {
        prepare("2026-10-17T23:59:59.500Z")
        seed("Old", "2026-10-17T23:59:59.999Z", 100)
        seed("New", "2026-10-18T00:00:00Z", 700)
        watch(); runCurrent(); assertEquals(100, vm.uiState.value.consumedCalories)
        advanceTimeBy(499); runCurrent(); assertEquals(1, days.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, days.size); assertEquals(1, activeQueries)
        assertEquals(listOf("New"), vm.uiState.value.todayLogs.map { it.name })
        assertEquals(700, vm.uiState.value.consumedCalories)
        assertEquals(10.0, vm.uiState.value.consumedProtein, 0.000001); assertEquals(20.0, vm.uiState.value.consumedCarbs, 0.000001)
        assertEquals(5.0, vm.uiState.value.consumedFats, 0.000001); assertEquals(2500, vm.uiState.value.targetCalories)
        assertEquals(listOf(meal), vm.uiState.value.mealTemplates); assertEquals(1, templateStarts)
        vm.refreshDate(); runCurrent(); assertEquals(2, days.size)
    }

    @Test fun `midnight without logs publishes zero totals rather than retaining yesterday`() = runTest {
        prepare("2026-10-17T23:59:59Z"); seed("Old", "2026-10-17T12:00:00Z", 100)
        watch(); runCurrent(); advanceTimeBy(1_000); runCurrent()
        assertTrue(vm.uiState.value.todayLogs.isEmpty())
        assertEquals(0, vm.uiState.value.consumedCalories); assertEquals(0.0, vm.uiState.value.consumedProtein, 0.000001)
        assertEquals(0.0, vm.uiState.value.consumedCarbs, 0.000001); assertEquals(0.0, vm.uiState.value.consumedFats, 0.000001)
    }

    @Test fun `clock correction within the same day reschedules midnight without querying again`() = runTest {
        prepare("2026-10-17T08:00:00Z"); watch(); runCurrent()
        base += 12 * 3_600_000L; vm.refreshDate(); runCurrent()
        assertEquals(1, days.size)
        advanceTimeBy(4 * 3_600_000L); runCurrent()
        assertEquals(2, days.size); assertEquals(instant("2026-10-18T00:00:00Z"), days.last().startMillis)
    }

    @Test fun `backward clock correction extends the wait rather than querying at obsolete midnight`() = runTest {
        prepare("2026-10-17T20:00:00Z"); watch(); runCurrent()
        base -= 12 * 3_600_000L; vm.refreshDate(); runCurrent()
        advanceTimeBy(4 * 3_600_000L); runCurrent(); assertEquals(1, days.size)
        advanceTimeBy(12 * 3_600_000L); runCurrent(); assertEquals(2, days.size)
    }

    @Test fun `forward and backward date jumps replace only the daily query`() = runTest {
        prepare("2026-10-17T12:00:00Z"); watch(); runCurrent()
        base += 2 * 86_400_000L; vm.refreshDate(); runCurrent()
        assertEquals(2, days.size); assertEquals(instant("2026-10-19T00:00:00Z"), days.last().startMillis)
        base -= 3 * 86_400_000L; vm.refreshDate(); runCurrent()
        assertEquals(3, days.size); assertEquals(instant("2026-10-16T00:00:00Z"), days.last().startMillis)
        assertEquals(1, templateStarts); assertEquals(1, activeQueries)
    }

    @Test fun `timezone event changes bounds immediately and does not repeat an equal query`() = runTest {
        prepare("2026-10-17T00:30:00Z")
        seed("UTC yesterday", "2026-10-16T23:30:00Z", 100)
        seed("UTC today", "2026-10-17T00:10:00Z", 700)
        watch(); runCurrent(); assertEquals(700, vm.uiState.value.consumedCalories)
        zone = TimeZone.getTimeZone("Pacific/Honolulu"); vm.refreshDate(); runCurrent()
        assertEquals(800, vm.uiState.value.consumedCalories)
        assertEquals("Pacific/Honolulu", days.last().zoneId); assertEquals(2, days.size)
        repeat(3) { vm.refreshDate(); runCurrent() }; assertEquals(2, days.size)
    }

    @Test fun `stopping presentation cancels timer and return samples current date despite dropped refresh events`() = runTest {
        prepare("2026-10-17T12:00:00Z"); val observer = watch(); runCurrent()
        observer.cancel(); runCurrent(); advanceTimeBy(1_000); runCurrent()
        val reads = clockReads
        advanceTimeBy(3 * 86_400_000L); runCurrent(); vm.refreshDate(); runCurrent()
        assertEquals(reads, clockReads); assertEquals(1, days.size); assertEquals(0, activeQueries)
        watch(); runCurrent()
        assertEquals(2, days.size); assertEquals(instant("2026-10-20T00:00:00Z"), days.last().startMillis)
    }

    @Test fun `a short tab gap across midnight reuses the once refreshed query`() = runTest {
        prepare("2026-10-17T23:59:59.800Z"); val observer = watch(); runCurrent()
        observer.cancel(); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(2, days.size); watch(); vm.refreshDate(); runCurrent()
        assertEquals(2, days.size); assertEquals(1, templateStarts)
    }

    @Test fun `spring DST midnight wait follows the 23 hour local day`() = runTest {
        prepare("2026-03-29T00:00:00Z", "Europe/Lisbon"); watch(); runCurrent()
        advanceTimeBy(23 * 3_600_000L - 1); runCurrent(); assertEquals(1, days.size)
        advanceTimeBy(1); runCurrent(); assertEquals(2, days.size)
        assertEquals(instant("2026-03-29T23:00:00Z"), days.last().startMillis)
    }

    @Test fun `autumn DST midnight wait follows the 25 hour local day`() = runTest {
        prepare("2026-10-24T23:00:00Z", "Europe/Lisbon"); watch(); runCurrent()
        advanceTimeBy(24 * 3_600_000L); runCurrent(); assertEquals(1, days.size)
        advanceTimeBy(3_600_000L); runCurrent(); assertEquals(2, days.size)
    }

    @Test fun `accepted quick logs and templates use the current clock on each side of midnight`() = runTest {
        prepare("2026-10-17T23:59:59Z"); watch(); runCurrent()
        vm.addQuickLog(100, isCaloriesOnly = true); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        vm.logMealTemplate(meal); runCurrent()
        assertEquals(600, vm.uiState.value.consumedCalories)
        val stored = repository.getAllNutritionLogs().first()
        assertEquals(setOf(instant("2026-10-17T23:59:59Z"), instant("2026-10-18T00:00:00Z")), stored.map { it.timestamp }.toSet())
        vm.resetTodayLogs(); runCurrent()
        assertTrue(vm.uiState.value.todayLogs.isEmpty()); assertEquals(1, repository.getAllNutritionLogs().first().size)
    }
}
