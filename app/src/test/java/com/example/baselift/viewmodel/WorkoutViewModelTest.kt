package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import com.example.baselift.fakes.FakeWorkoutRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeWorkoutRepository
    private lateinit var viewModel: WorkoutViewModel
    private val viewModelStore = ViewModelStore()

    @Before
    fun setup() {
        repository = FakeWorkoutRepository()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    private fun initViewModel(source: IWorkoutRepository = repository) {
        viewModel = WorkoutViewModel(source, modelDispatcher = mainDispatcherRule.testDispatcher)
        viewModelStore.put("workout", viewModel)
    }

    private class HistoryCountingRepository(
        private val source: IWorkoutRepository
    ) : IWorkoutRepository by source {
        var batchReads = 0
            private set
        var singleSetReads = 0
            private set

        override suspend fun getPreviousSetsForWorkout(workoutId: Int): List<SetLogEntity> {
            batchReads++
            return source.getPreviousSetsForWorkout(workoutId)
        }

        override suspend fun getPreviousSet(exerciseId: Int, setNumber: Int): SetLogEntity? {
            singleSetReads++
            return source.getPreviousSet(exerciseId, setNumber)
        }
    }

    @Test
    fun `exercise model assembly runs on the injected dispatcher`() = runTest {
        val workoutId = repository.createWorkout("Push", 0)
        repository.createExercise(workoutId, "Bench", "", "", 0)
        val exercises = repository.allExercises.first()
        val modelReadThreads = ConcurrentLinkedQueue<Thread>()
        val observed = object : AbstractList<ExerciseEntity>() {
            override val size = exercises.size
            override fun get(index: Int): ExerciseEntity {
                modelReadThreads.add(Thread.currentThread())
                return exercises[index]
            }
        }
        val source = object : IWorkoutRepository by repository {
            override fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>> = flowOf(observed)
        }
        val modelThread = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "workout-model-test").also { modelThread.set(it) }
        }.asCoroutineDispatcher().use { dispatcher ->
            val isolated = WorkoutViewModel(source, dispatcher)
            val store = ViewModelStore().apply { put("workout", isolated) }
            try {
                val state = isolated.uiState.first { it.exercises.isNotEmpty() }
                assertEquals("Bench", state.exercises.single().exercise.name)
                assertTrue(modelReadThreads.isNotEmpty())
                assertTrue(modelReadThreads.all { it === modelThread.get() })
            } finally {
                store.clear()
            }
        }
    }

    @Test
    fun `history is batched once per selection and refreshed after finalization`() = runTest {
        val workoutId = repository.createWorkout("Full body", 0)
        repeat(6) { repository.createExercise(workoutId, "Exercise $it", "Barbell", "Legs", it) }
        repository.updateExercises(repository.allExercises.first().map { it.copy(setCount = 4) })
        val exercises = repository.allExercises.first()
        val oldSession = repository.startOrGetSession(workoutId)
        exercises.forEach { exercise ->
            repeat(4) { repository.logSet(oldSession.id, exercise.id, it + 1, 100f, 10, true, 0) }
        }
        repository.finalizeSession(oldSession.copy(timestamp = 1_000))

        val counted = HistoryCountingRepository(repository)
        initViewModel(counted)
        runCurrent()
        assertEquals(1, counted.batchReads)
        assertEquals(0, counted.singleSetReads)
        assertEquals(24, viewModel.uiState.value.exercises.sumOf { it.sets.size })
        assertTrue(viewModel.uiState.value.exercises.all { model -> model.sets.all { it.prevWeight == 100f } })

        val exerciseId = exercises.first().id
        viewModel.logSet(exerciseId, 1, 120f, 8, true)
        viewModel.addSetToExercise(exerciseId)
        viewModel.createExercise("New exercise", "", "")
        runCurrent()
        assertEquals(1, counted.batchReads)
        assertEquals(0, counted.singleSetReads)
        assertNull(viewModel.uiState.value.exercises.last().sets.single().prevWeight)

        viewModel.finalizeWorkout()
        runCurrent()
        assertEquals(2, counted.batchReads)
        assertEquals(0, counted.singleSetReads)
        val currentSets = viewModel.uiState.value.exercises.first().sets
        assertEquals(120f, currentSets[0].prevWeight!!, 0f)
        assertEquals(8, currentSets[0].prevReps)
        assertEquals(100f, currentSets[1].prevWeight!!, 0f)
        assertNull(currentSets.last().prevWeight)
    }

    @Test
    fun `adding a set retains older per-number history when the latest session omitted it`() = runTest {
        val workoutId = repository.createWorkout("Push", 0)
        repository.createExercise(workoutId, "Bench", "", "", 0)
        val exerciseId = repository.allExercises.first().single().id
        val older = repository.startOrGetSession(workoutId)
        repository.logSet(older.id, exerciseId, 1, 70f, 10, true, 0)
        repository.logSet(older.id, exerciseId, 2, 65f, 12, true, 0)
        repository.finalizeSession(older.copy(timestamp = 1_000))
        val latest = repository.startOrGetSession(workoutId)
        repository.logSet(latest.id, exerciseId, 1, 80f, 8, true, 0)
        repository.finalizeSession(latest.copy(timestamp = 2_000))
        val active = repository.startOrGetSession(workoutId)
        repository.logSet(active.id, exerciseId, 1, 999f, 1, true, 0)

        initViewModel()
        runCurrent()
        assertEquals(80f, viewModel.uiState.value.exercises.single().sets.single().prevWeight!!, 0f)
        viewModel.addSetToExercise(exerciseId)
        runCurrent()
        val second = viewModel.uiState.value.exercises.single().sets[1]
        assertEquals(65f, second.prevWeight!!, 0f)
        assertEquals(12, second.prevReps)
    }

    @Test
    fun `pending history never publishes rows or overwrites a newer selection`() = runTest {
        val firstId = repository.createWorkout("First", 0)
        val secondId = repository.createWorkout("Second", 1)
        repository.createExercise(firstId, "First exercise", "", "", 0)
        repository.createExercise(secondId, "Second exercise", "", "", 0)
        val pending = CompletableDeferred<List<SetLogEntity>>()
        val delayed = object : IWorkoutRepository by repository {
            override suspend fun getPreviousSetsForWorkout(workoutId: Int): List<SetLogEntity> =
                if (workoutId == firstId) pending.await() else repository.getPreviousSetsForWorkout(workoutId)
        }
        initViewModel(delayed)
        runCurrent()
        assertEquals(firstId, viewModel.uiState.value.selectedWorkout?.id)
        assertTrue(viewModel.uiState.value.exercises.isEmpty())

        viewModel.selectWorkout(repository.allWorkouts.first().single { it.id == secondId })
        runCurrent()
        pending.complete(emptyList())
        runCurrent()
        assertEquals(secondId, viewModel.uiState.value.selectedWorkout?.id)
        assertEquals("Second exercise", viewModel.uiState.value.exercises.single().exercise.name)
        assertEquals(secondId, viewModel.uiState.value.activeSession?.workoutId)
    }

    @Test
    fun `current logs beyond the template count and first duplicate remain visible`() = runTest {
        val workoutId = repository.createWorkout("Push", 0)
        repository.createExercise(workoutId, "Bench", "", "", 0)
        val exerciseId = repository.allExercises.first().single().id
        val session = repository.startOrGetSession(workoutId)
        repository.logSet(session.id, exerciseId, 3, 80f, 10, true, 0)
        repository.logSet(session.id, exerciseId, 3, 90f, 8, true, 0)

        initViewModel()
        runCurrent()
        val sets = viewModel.uiState.value.exercises.single().sets
        assertEquals(3, sets.size)
        assertEquals(80f, sets[2].currentLog!!.weight, 0f)
        assertTrue(sets[2].isLastCompleted)
        assertTrue(sets[2].canBeChecked)
        assertTrue(sets[2].canBeRemoved)
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
