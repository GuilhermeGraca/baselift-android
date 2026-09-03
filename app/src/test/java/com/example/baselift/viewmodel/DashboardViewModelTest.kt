package com.example.baselift.viewmodel

import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.ViewModel.dashboard.DashboardViewModel
import com.example.baselift.fakes.FakeNutritionRepository
import com.example.baselift.fakes.FakeWorkoutRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var workoutRepository: FakeWorkoutRepository
    private lateinit var nutritionRepository: FakeNutritionRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        workoutRepository = FakeWorkoutRepository()
        nutritionRepository = FakeNutritionRepository()
        viewModel = DashboardViewModel(workoutRepository, nutritionRepository)
    }

    private fun getTimestampDaysAgo(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return cal.timeInMillis
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
