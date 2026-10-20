package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.ViewModel.dashboard.DashboardViewModel
import com.example.baselift.fakes.FakeNutritionRepository
import com.example.baselift.fakes.FakeWorkoutRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var workoutRepository: FakeWorkoutRepository
    private lateinit var nutritionRepository: FakeNutritionRepository
    private lateinit var viewModel: DashboardViewModel
    private val viewModelStore = ViewModelStore()

    @Before
    fun setup() {
        workoutRepository = FakeWorkoutRepository()
        nutritionRepository = FakeNutritionRepository()
        viewModel = DashboardViewModel(
            workoutRepository,
            nutritionRepository,
            analyticsDispatcher = mainDispatcherRule.testDispatcher
        )
        viewModelStore.put("dashboard", viewModel)
        requestDetails(viewModel)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    private fun requestDetails(dashboard: DashboardViewModel, month: Long = System.currentTimeMillis()) {
        dashboard.setCalendarMonth(month)
        dashboard.setRoutineExpanded(1, true)
        dashboard.setRoutineExpanded(2, true)
    }

    private class SubscriptionTracker {
        var activeCollectors = 0
            private set
        var totalStarts = 0
            private set

        fun <T> track(source: Flow<T>): Flow<T> = flow {
            activeCollectors++
            totalStarts++
            try {
                emitAll(source)
            } finally {
                activeCollectors--
            }
        }
    }

    private fun createTrackedViewModel(tracker: SubscriptionTracker): DashboardViewModel {
        val trackedWorkouts = object : IWorkoutRepository by workoutRepository {
            override val allWorkouts = tracker.track(workoutRepository.allWorkouts)
            override val allExercises = tracker.track(workoutRepository.allExercises)
            override fun getAllCompletedSessions() = tracker.track(workoutRepository.getAllCompletedSessions())
            override fun getAllCompletedSetLogs() = tracker.track(workoutRepository.getAllCompletedSetLogs())
        }
        val trackedNutrition = object : INutritionRepository by nutritionRepository {
            override fun getAllNutritionLogs() = tracker.track(nutritionRepository.getAllNutritionLogs())
        }
        return DashboardViewModel(trackedWorkouts, trackedNutrition, mainDispatcherRule.testDispatcher).also {
            viewModelStore.put("tracked-dashboard", it)
            requestDetails(it)
        }
    }

    private fun getTimestampDaysAgo(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return cal.timeInMillis
    }

    private class ObservedHistory<T>(private val values: List<T>) : AbstractList<T>() {
        var reads = 0
        override val size get() = values.size
        override fun get(index: Int): T {
            reads++
            return values[index]
        }
    }

    private inner class HistorySources(val now: Long) {
        val nutrition = ObservedHistory((0 until 1_095).map {
            NutritionLogEntity(id = it + 1, calories = 100, protein = 0, carbs = 0, fats = 0, timestamp = now - it * 86_400_000L)
        })
        val sessions = ObservedHistory((0 until 200).map {
            WorkoutSessionEntity(id = it + 1, workoutId = if (it % 2 == 0) 1 else 2,
                timestamp = now - it * 86_400_000L, isCompleted = true)
        })
        val setValues = (0 until 4_800).map {
            val day = it / 24
            SetLogEntity(id = it + 1, sessionId = day + 1,
                exerciseId = if (day % 2 == 0) it % 2 + 1 else 3, setNumber = it % 4 + 1,
                weight = 100f, reps = 10, isCompleted = true, timestamp = now - day * 86_400_000L)
        }
        val sets = ObservedHistory(setValues)
        val nutritionFlow = MutableStateFlow<List<NutritionLogEntity>>(nutrition)
        val sessionFlow = MutableStateFlow<List<WorkoutSessionEntity>>(sessions)
        val setFlow = MutableSharedFlow<List<SetLogEntity>>(replay = 1).apply { tryEmit(sets) }
        val workoutFlow = MutableStateFlow(listOf(WorkoutEntity(id = 1, name = "Push"), WorkoutEntity(id = 2, name = "Legs")))
        val exerciseFlow = MutableStateFlow(listOf(
            ExerciseEntity(id = 1, workoutId = 1, name = "Bench", equipment = "", muscleGroups = ""),
            ExerciseEntity(id = 2, workoutId = 1, name = "Press", equipment = "", muscleGroups = ""),
            ExerciseEntity(id = 3, workoutId = 2, name = "Squat", equipment = "", muscleGroups = "")
        ))
        val workouts = object : IWorkoutRepository by workoutRepository {
            override val allWorkouts = workoutFlow
            override val allExercises = exerciseFlow
            override fun getAllCompletedSessions(): Flow<List<WorkoutSessionEntity>> = sessionFlow
            override fun getAllCompletedSetLogs(): Flow<List<SetLogEntity>> = setFlow
        }
        val meals = object : INutritionRepository by nutritionRepository {
            override fun getAllNutritionLogs(): Flow<List<NutritionLogEntity>> = nutritionFlow
        }

        fun dashboard(clock: () -> Long = { now }) = DashboardViewModel(
            workouts, meals, mainDispatcherRule.testDispatcher, clock
        ).also { viewModelStore.put("observed-dashboard", it); requestDetails(it, now) }

        fun resetReads() {
            nutrition.reads = 0
            sessions.reads = 0
            sets.reads = 0
        }
    }

    @Test
    fun `closed details retain full streaks and weekly volume without preparing detail output`() = runTest {
        val sources = HistorySources(System.currentTimeMillis())
        val dashboard = DashboardViewModel(sources.workouts, sources.meals, mainDispatcherRule.testDispatcher) { sources.now }
        viewModelStore.put("demand-dashboard", dashboard)
        dashboard.setRestDays(7)
        dashboard.setNutritionRestDays(7)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        val closed = dashboard.uiState.value
        assertEquals(200, closed.workoutStreak)
        assertEquals(1_095, closed.nutritionStreak)
        assertTrue(closed.historicalCalendarData.isEmpty())
        assertTrue(closed.workoutVolumeTrends.isEmpty())
        assertEquals(4_800_000.0, closed.weeklyVolumes.sumOf { it.totalVolume.toDouble() }, 0.0)

        dashboard.setRoutineExpanded(1, true)
        runCurrent()
        assertEquals(setOf(1), dashboard.uiState.value.workoutVolumeTrends.keys)
        dashboard.setRoutineExpanded(2, true)
        runCurrent()
        val expanded = dashboard.uiState.value
        assertSame(closed.weeklyVolumes, expanded.weeklyVolumes)
        assertEquals(setOf(1, 2), expanded.workoutVolumeTrends.keys)
        assertEquals(4_800_000.0, expanded.workoutVolumeTrends.values.flatten().sumOf { it.yValue.toDouble() }, 0.0)
        dashboard.setRoutineExpanded(1, false)
        runCurrent()
        assertEquals(setOf(2), dashboard.uiState.value.workoutVolumeTrends.keys)
        dashboard.setRoutineExpanded(2, false)
        runCurrent()
        assertTrue(dashboard.uiState.value.workoutVolumeTrends.isEmpty())
        assertSame(closed.weeklyVolumes, dashboard.uiState.value.weeklyVolumes)
        assertEquals(closed.workoutStreak, dashboard.uiState.value.workoutStreak)
    }

    @Test
    fun `calendar prepares only requested month and closes without truncating streak history`() = runTest {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 20, 12, 0, 0) }.timeInMillis
        val sources = HistorySources(now)
        val dashboard = DashboardViewModel(sources.workouts, sources.meals, mainDispatcherRule.testDispatcher) { now }
        viewModelStore.put("month-dashboard", dashboard)
        dashboard.setRestDays(7)
        dashboard.setNutritionRestDays(7)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        dashboard.setCalendarMonth(now)
        runCurrent()
        val october = dashboard.uiState.value
        assertEquals(20, october.historicalCalendarData.size)
        assertTrue(october.historicalCalendarData.keys.all { it.startsWith("2026-10-") })
        assertEquals(100, october.historicalCalendarData.getValue("2026-10-20").nutritionCalories)
        sources.nutritionFlow.value += NutritionLogEntity(id = 2_000, calories = 250, protein = 10,
            carbs = 20, fats = 5, timestamp = now)
        runCurrent()
        assertEquals(350, dashboard.uiState.value.historicalCalendarData.getValue("2026-10-20").nutritionCalories)
        val september = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }.timeInMillis
        dashboard.setCalendarMonth(september)
        runCurrent()
        assertEquals(30, dashboard.uiState.value.historicalCalendarData.size)
        assertTrue(dashboard.uiState.value.historicalCalendarData.keys.all { it.startsWith("2026-09-") })
        dashboard.setCalendarMonth(null)
        runCurrent()
        val closed = dashboard.uiState.value
        assertTrue(closed.historicalCalendarData.isEmpty())
        assertEquals(200, closed.workoutStreak)
        assertEquals(1_095, closed.nutritionStreak)
    }

    @Test
    fun `nutrition updates and rest day changes reuse workout history and charts`() = runTest {
        val sources = HistorySources(System.currentTimeMillis())
        val dashboard = sources.dashboard()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        val before = dashboard.uiState.value
        sources.resetReads()

        sources.nutritionFlow.value = sources.nutritionFlow.value + NutritionLogEntity(
            id = 2_000, calories = 500, protein = 0, carbs = 0, fats = 0, timestamp = sources.now
        )
        runCurrent()
        assertEquals(0, sources.sessions.reads)
        assertEquals(0, sources.sets.reads)
        val afterMeal = dashboard.uiState.value
        assertEquals(before.nutritionDaysThisWeek.values.sumOf { it.calories } + 500,
            afterMeal.nutritionDaysThisWeek.values.sumOf { it.calories })
        assertSame(before.weeklyVolumes, afterMeal.weeklyVolumes)
        assertSame(before.workoutVolumeTrends, afterMeal.workoutVolumeTrends)

        sources.resetReads()
        dashboard.setRestDays(7)
        dashboard.setNutritionRestDays(7)
        runCurrent()
        val afterSettings = dashboard.uiState.value
        assertEquals(200, afterSettings.workoutStreak)
        assertEquals(1_095, afterSettings.nutritionStreak)
        assertEquals(0, sources.nutrition.reads)
        assertEquals(0, sources.sessions.reads)
        assertEquals(0, sources.sets.reads)
        assertSame(before.workoutVolumeTrends, afterSettings.workoutVolumeTrends)
        assertSame(afterMeal.nutritionDaysThisWeek, afterSettings.nutritionDaysThisWeek)
        assertSame(before.workoutDaysThisWeek, afterSettings.workoutDaysThisWeek)
    }

    @Test
    fun `metadata edits reuse set history and membership changes regroup only prepared daily totals`() = runTest {
        val sources = HistorySources(System.currentTimeMillis())
        val dashboard = sources.dashboard()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        val before = dashboard.uiState.value
        sources.resetReads()

        sources.workoutFlow.value = sources.workoutFlow.value.map { it.copy(name = "Renamed ${it.id}") }
        sources.exerciseFlow.value = sources.exerciseFlow.value.map { it.copy(name = "Edited ${it.id}", setCount = 5, orderIndex = 3) }
        runCurrent()
        val edited = dashboard.uiState.value
        assertEquals("Edited 1", edited.exercises.getValue(1).first().name)
        assertTrue(edited.historicalCalendarData.values.filter { it.hasWorkout }.all {
            it.workoutNames.all { name -> name.startsWith("Renamed") }
        })
        assertEquals(0, sources.nutrition.reads)
        assertEquals(0, sources.sessions.reads)
        assertEquals(0, sources.sets.reads)
        assertSame(before.workoutVolumeTrends, edited.workoutVolumeTrends)

        sources.exerciseFlow.value = sources.exerciseFlow.value.map {
            if (it.id == 1) it.copy(workoutId = 2) else it
        }
        runCurrent()
        val moved = dashboard.uiState.value
        assertEquals(0, sources.sets.reads)
        assertSame(before.weeklyVolumes, moved.weeklyVolumes)
        assertEquals(before.workoutVolumeTrends.getValue(1).sumOf { it.yValue.toDouble() } / 2,
            moved.workoutVolumeTrends.getValue(1).sumOf { it.yValue.toDouble() }, 0.0)
        assertEquals(before.workoutVolumeTrends.values.flatten().sumOf { it.yValue.toDouble() },
            moved.workoutVolumeTrends.values.flatten().sumOf { it.yValue.toDouble() }, 0.0)
    }

    @Test
    fun `set changes update charts without reading nutrition or session history and reset clears results`() = runTest {
        val sources = HistorySources(System.currentTimeMillis())
        val dashboard = sources.dashboard()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        val before = dashboard.uiState.value
        sources.resetReads()
        sources.setFlow.emit(sources.setValues + sources.setValues.first().copy(id = 5_000, weight = 150f, reps = 6))
        runCurrent()
        val changed = dashboard.uiState.value
        assertEquals(0, sources.nutrition.reads)
        assertEquals(0, sources.sessions.reads)
        assertSame(before.nutritionDaysThisWeek, changed.nutritionDaysThisWeek)
        assertSame(before.workoutDaysThisWeek, changed.workoutDaysThisWeek)
        assertEquals(before.weeklyVolumes.sumOf { it.totalVolume.toDouble() } + 900,
            changed.weeklyVolumes.sumOf { it.totalVolume.toDouble() }, 0.0)

        sources.nutritionFlow.value = emptyList()
        sources.sessionFlow.value = emptyList()
        sources.setFlow.emit(emptyList())
        runCurrent()
        val cleared = dashboard.uiState.value
        assertTrue(cleared.historicalCalendarData.isEmpty())
        assertTrue(cleared.weeklyVolumes.isEmpty())
        assertTrue(cleared.workoutVolumeTrends.isEmpty())
        assertEquals(0, cleared.workoutStreak)
        assertEquals(0, cleared.nutritionStreak)
    }

    @Test
    fun `equal set snapshots are compared once without rebuilding chart history`() = runTest {
        val sources = HistorySources(System.currentTimeMillis())
        val dashboard = sources.dashboard()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        val identicalSnapshot = ObservedHistory(sources.setValues)
        sources.resetReads()
        sources.setFlow.emit(identicalSnapshot)
        runCurrent()
        assertEquals(sources.setValues.size, identicalSnapshot.reads)
        assertEquals(sources.setValues.size, sources.sets.reads)
    }

    @Test
    fun `Monday boundary refreshes weekly summaries without rereading set history`() = runTest {
        val sunday = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 18, 23, 59, 59)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sources = HistorySources(sunday)
        val dashboard = sources.dashboard { sunday + testScheduler.currentTime }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        val before = dashboard.uiState.value
        assertTrue(before.nutritionDaysThisWeek.containsKey(7))
        assertTrue(before.workoutSessionsThisWeek > 0)
        sources.resetReads()
        advanceTimeBy(1_000)
        runCurrent()
        val monday = dashboard.uiState.value
        assertTrue(monday.nutritionDaysThisWeek.isEmpty())
        assertEquals(0, monday.workoutSessionsThisWeek)
        assertEquals(0, sources.sets.reads)
        assertSame(before.workoutVolumeTrends, monday.workoutVolumeTrends)
    }

    @Test
    fun `all rest settings preserve ten workout days and allow nutrition gap in reported October calendar`() = runTest {
        fun october(day: Int) = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val workoutId = workoutRepository.createWorkout("Workout", 0)
        (1..10).forEach { day ->
            val session = workoutRepository.startOrGetSession(workoutId)
            workoutRepository.finalizeSession(session.copy(timestamp = october(day)))
        }
        // A second session on the same day must not inflate the day streak.
        val duplicate = workoutRepository.startOrGetSession(workoutId)
        workoutRepository.finalizeSession(duplicate.copy(timestamp = october(10)))
        // An unfinished session today does not count as completed activity.
        workoutRepository.startOrGetSession(workoutId)
        ((1..9).toList() + listOf(11, 11)).forEachIndexed { i, day ->
            nutritionRepository.insertNutritionLog(NutritionLogEntity(
                id = i + 1, calories = 100, protein = 0, carbs = 0, fats = 0, timestamp = october(day)
            ))
        }
        val dashboard = DashboardViewModel(workoutRepository, nutritionRepository,
            mainDispatcherRule.testDispatcher, clockMillis = { october(11) })
        viewModelStore.put("calendar-regression", dashboard)
        dashboard.setCalendarMonth(october(11))
        dashboard.setRestDays(0)
        dashboard.setNutritionRestDays(0)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()

        val state = dashboard.uiState.value
        assertEquals(10, state.workoutStreak)
        assertEquals(1, state.nutritionStreak)
        assertTrue(state.historicalCalendarData.getValue("2026-10-10").hasWorkout)
        assertFalse(state.historicalCalendarData.getValue("2026-10-10").hasNutrition)
        assertFalse(state.historicalCalendarData.getValue("2026-10-11").hasWorkout)
        assertTrue(state.historicalCalendarData.getValue("2026-10-11").hasNutrition)

        (0..7).forEach { restDays ->
            val nutritionBefore = dashboard.uiState.value.nutritionStreak
            dashboard.setRestDays(restDays)
            runCurrent()
            assertEquals("Workout allowance $restDays", 10, dashboard.uiState.value.workoutStreak)
            assertEquals(nutritionBefore, dashboard.uiState.value.nutritionStreak)
            dashboard.setNutritionRestDays(restDays)
            runCurrent()
            assertEquals("Nutrition allowance $restDays", if (restDays == 0) 1 else 10,
                dashboard.uiState.value.nutritionStreak)
            assertEquals(10, dashboard.uiState.value.workoutStreak)
        }
        // Tightening the allowance must recalculate, rather than retaining a maximum.
        (7 downTo 0).forEach { restDays ->
            dashboard.setRestDays(restDays)
            dashboard.setNutritionRestDays(restDays)
            runCurrent()
            assertEquals(10, dashboard.uiState.value.workoutStreak)
            assertEquals(if (restDays == 0) 1 else 10, dashboard.uiState.value.nutritionStreak)
        }
    }

    @Test
    fun `midnight expires a missed daily streak without rereading history or rebuilding charts`() = runTest {
        val tuesday = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 13, 23, 59, 59)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sources = HistorySources(tuesday)
        val dashboard = sources.dashboard { tuesday + testScheduler.currentTime }
        dashboard.setRestDays(0)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        val before = dashboard.uiState.value
        assertEquals(200, before.workoutStreak)
        assertEquals(1_095, before.nutritionStreak)
        sources.resetReads()

        // At Wednesday's start, Tuesday can still anchor the streak.
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(200, dashboard.uiState.value.workoutStreak)
        assertEquals(1_095, dashboard.uiState.value.nutritionStreak)
        // No Wednesday logs: Thursday must show zero without any repository emission.
        advanceTimeBy(86_400_000)
        runCurrent()
        val expired = dashboard.uiState.value
        assertEquals(0, expired.workoutStreak)
        assertEquals(0, expired.nutritionStreak)
        assertEquals(0, sources.nutrition.reads)
        assertEquals(0, sources.sessions.reads)
        assertEquals(0, sources.sets.reads)
        assertSame(before.workoutVolumeTrends, expired.workoutVolumeTrends)
    }

    @Test
    fun `upstream processing stops after the last subscriber leaves`() = runTest {
        val tracker = SubscriptionTracker()
        val dashboard = createTrackedViewModel(tracker)
        runCurrent()
        assertEquals(0, tracker.activeCollectors)

        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        assertEquals(5, tracker.activeCollectors)
        assertEquals(5, tracker.totalStarts)

        first.cancel()
        runCurrent()
        assertEquals(5, tracker.activeCollectors)

        second.cancel()
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertEquals(5, tracker.activeCollectors)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(0, tracker.activeCollectors)
    }

    @Test
    fun `brief subscriber gap keeps existing upstream processing`() = runTest {
        val tracker = SubscriptionTracker()
        val dashboard = createTrackedViewModel(tracker)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()

        first.cancel()
        runCurrent()
        advanceTimeBy(500)
        val returning = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        assertEquals(5, tracker.activeCollectors)
        assertEquals(5, tracker.totalStarts)

        returning.cancel()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, tracker.activeCollectors)
    }

    @Test
    fun `last state is retained offscreen and refreshed on return`() = runTest {
        val tracker = SubscriptionTracker()
        val dashboard = createTrackedViewModel(tracker)
        val timestamp = System.currentTimeMillis()
        nutritionRepository.insertNutritionLog(
            NutritionLogEntity(calories = 500, protein = 30, carbs = 50, fats = 10, timestamp = timestamp)
        )
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        val cachedState = dashboard.uiState.value
        assertEquals(500, cachedState.historicalCalendarData.values.single().nutritionCalories)

        first.cancel()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, tracker.activeCollectors)

        nutritionRepository.insertNutritionLog(
            NutritionLogEntity(calories = 250, protein = 15, carbs = 25, fats = 5, timestamp = timestamp)
        )
        workoutRepository.createWorkout("Offscreen workout", 0)
        dashboard.setRestDays(2)
        runCurrent()
        assertEquals(cachedState, dashboard.uiState.value)
        assertEquals(0, tracker.activeCollectors)
        assertEquals(5, tracker.totalStarts)

        val returning = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        val refreshedState = dashboard.uiState.value
        assertFalse(refreshedState.isLoading)
        assertEquals(750, refreshedState.historicalCalendarData.values.single().nutritionCalories)
        assertEquals("Offscreen workout", refreshedState.workouts.single().name)
        assertEquals(2, refreshedState.restDays)
        assertEquals(5, tracker.activeCollectors)
        assertEquals(10, tracker.totalStarts)
        returning.cancel()
    }

    @Test
    fun `history aggregation runs on the analytics dispatcher`() = runTest {
        val timestamp = Calendar.getInstance().apply {
            set(2025, Calendar.JULY, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val logs = listOf(
            NutritionLogEntity(id = 1, calories = 500, protein = 30, carbs = 50, fats = 10, timestamp = timestamp),
            NutritionLogEntity(id = 2, calories = 250, protein = 15, carbs = 25, fats = 5, timestamp = timestamp)
        )
        val historyReadThreads = ConcurrentLinkedQueue<Thread>()
        // Observe consumption by the analytics transform, rather than Flow emission.
        val history = object : AbstractList<NutritionLogEntity>() {
            override val size: Int = logs.size

            override fun get(index: Int): NutritionLogEntity {
                historyReadThreads.add(Thread.currentThread())
                return logs[index]
            }
        }
        val repository = object : INutritionRepository by nutritionRepository {
            override fun getAllNutritionLogs(): Flow<List<NutritionLogEntity>> = flowOf(history)
        }

        val analyticsThread = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dashboard-analytics-test").also { analyticsThread.set(it) }
        }.asCoroutineDispatcher().use { dispatcher ->
            val backgroundViewModel = DashboardViewModel(workoutRepository, repository, dispatcher)
            requestDetails(backgroundViewModel, timestamp)
            val store = ViewModelStore().apply { put("dashboard", backgroundViewModel) }
            try {
                val state = backgroundViewModel.uiState.first { !it.isLoading }
                val summary = state.historicalCalendarData.values.single()
                assertEquals(750, summary.nutritionCalories)
                assertEquals(45, summary.nutritionProtein)
                assertEquals(75, summary.nutritionCarbs)
                assertEquals(15, summary.nutritionFats)
                assertTrue(historyReadThreads.isNotEmpty())
                assertTrue(historyReadThreads.all { it === analyticsThread.get() })
            } finally {
                store.clear()
            }
        }
    }

    @Test
    fun `initial uiState computes correctly with empty data`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(0, state.nutritionStreak)
        assertEquals(0, state.workoutStreak)
        assertTrue(state.weeklyVolumes.isEmpty())
        assertTrue(state.historicalCalendarData.isEmpty())
        assertFalse(state.isLoading)
        
        job.cancel()
    }

    @Test
    fun `setRestDays and setNutritionRestDays updates state`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        viewModel.setRestDays(2)
        viewModel.setNutritionRestDays(1)
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(2, state.restDays)
        assertEquals(1, state.nutritionRestDays)
        
        job.cancel()
    }

    @Test
    fun `nutritionStreak calculates correctly`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        // Needs 7 days (0 rest days by default)
        viewModel.setNutritionRestDays(0)
        
        // Log for today
        nutritionRepository.insertNutritionLog(NutritionLogEntity(name = "A", calories = 100, protein = 10, carbs = 10, fats = 10, timestamp = getTimestampDaysAgo(0)))
        
        // If we only have today, streak is 1 (if requiredDays = 7, it counts the current active days)
        runCurrent()
        
        assertEquals(1, viewModel.uiState.value.nutritionStreak)
        
        job.cancel()
    }

    @Test
    fun `workoutStreak calculates correctly`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        viewModel.setRestDays(4) // 3 days required
        
        val workoutId = workoutRepository.createWorkout("Workout", 0)
        val session = workoutRepository.startOrGetSession(workoutId)
        workoutRepository.finalizeSession(session)
        
        runCurrent()
        
        // current week has 1
        assertEquals(1, viewModel.uiState.value.workoutStreak)
        
        job.cancel()
    }

    @Test
    fun `weeklyVolumes and charts calculate correctly`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        val today = getTimestampDaysAgo(0)
        
        // create workout and exercise
        workoutRepository.createWorkout("Test Workout", 0)
        workoutRepository.createExercise(1, "Test Ex", "Bar", "Chest", 0)
        
        // Finalize session
        val session = workoutRepository.startOrGetSession(1)
        workoutRepository.finalizeSession(session)
        
        // log set
        workoutRepository.logSet(1, 1, 1, 100f, 10, true, 0)
        // Set timestamp to today
        val setLogs = workoutRepository.getAllCompletedSetLogs().first()
        // we can't easily change timestamp after logSet with fake repository since it generates it,
        // but it generates it as System.currentTimeMillis() which is today.
        
        runCurrent()
        
        val state = viewModel.uiState.value
        
        // 100 * 10 = 1000 volume
        assertEquals(1, state.weeklyVolumes.size)
        assertEquals(1000f, state.weeklyVolumes[0].totalVolume, 0f)
        
        assertEquals(1, state.workoutVolumeTrends.size)
        val exerciseCharts = viewModel.exerciseCharts(1).first { !it.isLoading }
        assertEquals(1, exerciseCharts.volumeTrend.size)
        assertEquals(1, exerciseCharts.maxWeightTrend.size)
        
        job.cancel()
    }

    @Test
    fun `historicalCalendarData calculates correctly`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        val today = System.currentTimeMillis()
        
        nutritionRepository.insertNutritionLog(NutritionLogEntity(name = "A", calories = 500, protein = 10, carbs = 10, fats = 10, timestamp = today))
        
        val workoutId = workoutRepository.createWorkout("My Workout", 0)
        val session = workoutRepository.startOrGetSession(workoutId)
        workoutRepository.finalizeSession(session)
        
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(1, state.historicalCalendarData.size)
        
        val marker = state.historicalCalendarData.values.first()
        assertTrue(marker.hasNutrition)
        assertTrue(marker.hasWorkout)
        assertEquals(500, marker.nutritionCalories)
        assertTrue(marker.workoutNames.contains("My Workout"))
        
        job.cancel()
    }
}
