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
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
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
        }
    }

    private fun getTimestampDaysAgo(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return cal.timeInMillis
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
        assertEquals(1, state.exerciseVolumeTrends.size)
        assertEquals(1, state.exerciseMaxWeightTrends.size)
        
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
