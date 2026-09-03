package com.example.baselift.viewmodel

import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import com.example.baselift.fakes.FakeWorkoutRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeWorkoutRepository
    private lateinit var viewModel: WorkoutViewModel

    @Before
    fun setup() {
        repository = FakeWorkoutRepository()
    }

    private fun initViewModel() {
        viewModel = WorkoutViewModel(repository)
    }

    @Test
    fun `init loads workouts and selects first if none selected`() = runTest {
        repository.createWorkout("Push", 0)
        repository.createWorkout("Pull", 1)
        
        initViewModel()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.workouts.size)
        assertEquals("Push", state.selectedWorkout?.name)
    }

    @Test
    fun `draft methods update and read correctly`() = runTest {
        initViewModel()
        
        viewModel.updateDraftWeight(1, 1, "50")
        viewModel.updateDraftReps(1, 1, "10")
        
        assertEquals("50", viewModel.getDraftWeight(1, 1))
        assertEquals("10", viewModel.getDraftReps(1, 1))
        
        // Non existent
        assertEquals("", viewModel.getDraftWeight(2, 1))
    }

    @Test
    fun `createWorkout adds a new workout`() = runTest {
        initViewModel()
        runCurrent()
        
        viewModel.createWorkout("Legs")
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(1, state.workouts.size)
        assertEquals("Legs", state.workouts[0].name)
    }

    @Test
    fun `deleteWorkout removes workout and selects another`() = runTest {
        repository.createWorkout("A", 0)
        repository.createWorkout("B", 1)
        initViewModel()
        runCurrent()
        
        val state = viewModel.uiState.value
        val workoutA = state.workouts[0]
        assertEquals("A", state.selectedWorkout?.name)
        
        viewModel.deleteWorkout(workoutA)
        runCurrent()
        
        val stateAfter = viewModel.uiState.value
        assertEquals(1, stateAfter.workouts.size)
        assertEquals("B", stateAfter.selectedWorkout?.name)
    }

    @Test
    fun `createExercise adds an exercise to selected workout`() = runTest {
        repository.createWorkout("Full Body", 0)
        initViewModel()
        runCurrent()
        
        viewModel.createExercise("Squat", "Barbell", "Legs")
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(1, state.exercises.size)
        assertEquals("Squat", state.exercises[0].exercise.name)
        assertEquals(1, state.exercises[0].exercise.setCount)
    }

    @Test
    fun `addSetToExercise increases set count`() = runTest {
        repository.createWorkout("A", 0)
        initViewModel()
        runCurrent()
        
        viewModel.createExercise("Bench", "Bar", "Chest")
        runCurrent()
        
        val exerciseId = viewModel.uiState.value.exercises[0].exercise.id
        viewModel.addSetToExercise(exerciseId)
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(2, state.exercises[0].exercise.setCount)
        assertEquals(2, state.exercises[0].sets.size)
    }

    @Test
    fun `removeLastSet decreases set count and clears draft`() = runTest {
        repository.createWorkout("A", 0)
        initViewModel()
        runCurrent()
        
        viewModel.createExercise("Bench", "Bar", "Chest")
        runCurrent()
        
        val exerciseId = viewModel.uiState.value.exercises[0].exercise.id
        viewModel.addSetToExercise(exerciseId)
        runCurrent()
        
        // Add draft to set 2
        viewModel.updateDraftWeight(exerciseId, 2, "100")
        
        viewModel.removeLastSet(exerciseId)
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(1, state.exercises[0].exercise.setCount)
        assertEquals("", viewModel.getDraftWeight(exerciseId, 2))
    }

    @Test
    fun `deleteExercise removes exercise and clears drafts`() = runTest {
        repository.createWorkout("A", 0)
        initViewModel()
        runCurrent()
        
        viewModel.createExercise("Bench", "Bar", "Chest")
        runCurrent()
        
        val exerciseId = viewModel.uiState.value.exercises[0].exercise.id
        viewModel.updateDraftWeight(exerciseId, 1, "60")
        
        viewModel.deleteExercise(exerciseId)
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(0, state.exercises.size)
        assertEquals("", viewModel.getDraftWeight(exerciseId, 1))
    }

    @Test
    fun `logSet adds log and updates ui state`() = runTest {
        repository.createWorkout("A", 0)
        initViewModel()
        runCurrent()
        
        viewModel.createExercise("Bench", "Bar", "Chest")
        runCurrent()
        
        val exerciseId = viewModel.uiState.value.exercises[0].exercise.id
        
        viewModel.logSet(exerciseId, 1, 80f, 10, true)
        runCurrent()
        
        val state = viewModel.uiState.value
        val loggedSet = state.exercises[0].sets[0].currentLog
        
        assertNotNull(loggedSet)
        assertEquals(80f, loggedSet!!.weight)
        assertEquals(10, loggedSet.reps)
        assertTrue(loggedSet.isCompleted)
    }

    @Test
    fun `finalizeWorkout marks session completed and clears drafts`() = runTest {
        repository.createWorkout("A", 0)
        initViewModel()
        runCurrent()
        
        val sessionBefore = viewModel.uiState.value.activeSession
        assertNotNull(sessionBefore)
        
        viewModel.updateDraftWeight(1, 1, "100")
        
        viewModel.finalizeWorkout()
        runCurrent()
        
        // Active session should be a new one
        val sessionAfter = viewModel.uiState.value.activeSession
        assertNotNull(sessionAfter)
        assertNotEquals(sessionBefore!!.id, sessionAfter!!.id)
        
        // drafts cleared
        assertEquals("", viewModel.getDraftWeight(1, 1))
        
        val allCompleted = repository.getAllCompletedSessions().first()
        assertEquals(1, allCompleted.size)
        assertEquals(sessionBefore.id, allCompleted[0].id)
    }

    @Test
    fun `deleteAllWorkouts clears repository`() = runTest {
        repository.createWorkout("A", 0)
        initViewModel()
        runCurrent()
        
        viewModel.deleteAllWorkouts()
        runCurrent()
        
        val all = repository.allWorkouts.first()
        assertTrue(all.isEmpty())
    }
}
