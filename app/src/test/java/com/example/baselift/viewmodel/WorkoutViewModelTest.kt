package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import com.example.baselift.fakes.FakeWorkoutRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private class ControlledFinalizationRepository(
        private val source: IWorkoutRepository
    ) : IWorkoutRepository by source {
        val startedSessions = mutableListOf<WorkoutSessionEntity>()
        val release = CompletableDeferred<Unit>()
        var failure: Exception? = null

        override suspend fun finalizeSession(session: WorkoutSessionEntity) {
            startedSessions += session
            release.await()
            failure?.let { throw it }
            source.finalizeSession(session)
        }
    }

    private class ControlledSetRepository(private val source: IWorkoutRepository) : IWorkoutRepository by source {
        data class Call(val sessionId: Int, val exerciseId: Int, val number: Int, val completed: Boolean, val existingId: Int)
        val calls = mutableListOf<Call>()
        val release = CompletableDeferred<Unit>()
        val publishSnapshots = MutableStateFlow(true)
        var failure: Exception? = null
        var afterWrite: (suspend () -> Unit)? = null

        override fun getSetsForSession(sessionId: Int): Flow<List<SetLogEntity>> =
            source.getSetsForSession(sessionId).combine(publishSnapshots) { logs, publish -> logs to publish }
                .filter { it.second }.map { it.first }

        override suspend fun logSet(
            sessionId: Int, exerciseId: Int, setNumber: Int, weight: Float, reps: Int,
            isCompleted: Boolean, existingSetId: Int
        ) {
            calls += Call(sessionId, exerciseId, setNumber, isCompleted, existingSetId)
            release.await()
            failure?.let { throw it }
            source.logSet(sessionId, exerciseId, setNumber, weight, reps, isCompleted, existingSetId)
            afterWrite?.invoke()
        }
    }

    private suspend fun prepareSetWrites(): ControlledSetRepository {
        val workoutId = repository.createWorkout("Push", 0)
        repository.createExercise(workoutId, "Bench", "", "", 0)
        repository.createExercise(workoutId, "Press", "", "", 1)
        repository.updateExercises(repository.allExercises.first().map { it.copy(setCount = 2) })
        return ControlledSetRepository(repository).also { initViewModel(it) }
    }

    @Test
    fun `rapid completion taps accept one write synchronously and preserve sequential actions`() = runTest {
        val source = prepareSetWrites()
        runCurrent()
        val id = viewModel.uiState.value.exercises.first().exercise.id
        val sessionId = viewModel.uiState.value.activeSession!!.id

        viewModel.logSet(id, 2, 70f, 10, true)
        viewModel.logSet(-1, 1, 80f, 10, true)
        runCurrent()
        assertTrue(source.calls.isEmpty())
        repeat(5) { viewModel.logSet(id, 1, 80f, 10, true) }
        assertEquals(1, viewModel.uiState.value.pendingSetWrites.size)
        viewModel.logSet(id, 2, 70f, 10, true)
        viewModel.removeLastSet(id)
        viewModel.deleteExercise(id)
        viewModel.finalizeWorkout(sessionId)
        runCurrent()
        assertEquals(1, source.calls.size)
        assertFalse(viewModel.uiState.value.isFinalizing)
        assertEquals(2, viewModel.uiState.value.exercises.first().sets.size)

        source.release.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
        assertEquals(1, repository.getSetsForSession(sessionId).first().size)
        // A stale callback still requesting completion must not insert a second row.
        viewModel.logSet(id, 1, 80f, 10, true)
        runCurrent()
        assertEquals(1, source.calls.size)
        viewModel.logSet(id, 2, 70f, 10, true)
        runCurrent()
        assertEquals(2, source.calls.size)
        assertEquals(2, repository.getSetsForSession(sessionId).first().size)
        // Only the latest complete set may be unchecked.
        viewModel.logSet(id, 1, 80f, 10, false)
        runCurrent()
        assertEquals(2, source.calls.size)
    }

    @Test
    fun `guard survives a successful write until the UI receives its persisted snapshot`() = runTest {
        val source = prepareSetWrites()
        runCurrent()
        val id = viewModel.uiState.value.exercises.first().exercise.id
        val sessionId = viewModel.uiState.value.activeSession!!.id
        source.publishSnapshots.value = false
        source.release.complete(Unit)
        viewModel.logSet(id, 1, 80f, 10, true)
        runCurrent()
        assertEquals(1, repository.getSetsForSession(sessionId).first().size)
        assertFalse(viewModel.uiState.value.exercises.first().sets.first().isCompleted)
        assertEquals(1, viewModel.uiState.value.pendingSetWrites.size)
        viewModel.logSet(id, 1, 80f, 10, true)
        viewModel.finalizeWorkout(sessionId)
        runCurrent()
        assertEquals(1, source.calls.size)
        assertFalse(viewModel.uiState.value.isFinalizing)

        source.publishSnapshots.value = true
        runCurrent()
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
        assertTrue(viewModel.uiState.value.exercises.first().sets.first().isCompleted)
        // The ViewModel resolves the latest ID itself, even if the old UI sent zero.
        viewModel.logSet(id, 1, 80f, 10, false)
        runCurrent()
        assertTrue(source.calls.last().existingId > 0)
        assertTrue(repository.getSetsForSession(sessionId).first().isEmpty())
        viewModel.logSet(id, 1, 90f, 8, true)
        runCurrent()
        assertEquals(0, source.calls.last().existingId)
        assertEquals(1, repository.getSetsForSession(sessionId).first().size)
    }

    @Test
    fun `snapshot arriving before repository return cannot release the write guard early`() = runTest {
        val source = prepareSetWrites()
        runCurrent()
        val id = viewModel.uiState.value.exercises.first().exercise.id
        val afterWrite = CompletableDeferred<Unit>()
        source.afterWrite = { afterWrite.await() }
        source.release.complete(Unit)
        viewModel.logSet(id, 1, 80f, 10, true)
        runCurrent()
        assertTrue(viewModel.uiState.value.exercises.first().sets.first().isCompleted)
        assertEquals(1, viewModel.uiState.value.pendingSetWrites.size)
        viewModel.logSet(id, 1, 80f, 10, false)
        viewModel.logSet(id, 2, 70f, 10, true)
        runCurrent()
        assertEquals(1, source.calls.size)
        afterWrite.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
    }

    @Test
    fun `different exercises save independently and finalization waits for both`() = runTest {
        val source = prepareSetWrites()
        runCurrent()
        val ids = viewModel.uiState.value.exercises.map { it.exercise.id }
        ids.forEach { viewModel.logSet(it, 1, 80f, 10, true) }
        runCurrent()
        assertEquals(2, source.calls.size)
        assertEquals(2, viewModel.uiState.value.pendingSetWrites.size)
        viewModel.finalizeWorkout()
        runCurrent()
        assertFalse(viewModel.uiState.value.isFinalizing)
        source.release.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
        val sessionId = viewModel.uiState.value.activeSession!!.id
        viewModel.finalizeWorkout(sessionId)
        runCurrent()
        assertEquals(sessionId, viewModel.uiState.value.finalizedSessionId)
    }

    @Test
    fun `failed set save preserves inputs and allows a single retry`() = runTest {
        val source = prepareSetWrites()
        runCurrent()
        val id = viewModel.uiState.value.exercises.first().exercise.id
        viewModel.updateDraftWeight(id, 1, "80")
        viewModel.updateDraftReps(id, 1, "10")
        source.failure = IllegalStateException("write failed")
        source.release.complete(Unit)
        viewModel.logSet(id, 1, 80f, 10, true)
        runCurrent()
        assertTrue(viewModel.uiState.value.setSaveFailed)
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
        assertNull(viewModel.uiState.value.exercises.first().sets.first().currentLog)
        assertEquals("80", viewModel.getDraftWeight(id, 1))
        assertEquals("10", viewModel.getDraftReps(id, 1))
        viewModel.dismissSetSaveError()
        assertFalse(viewModel.uiState.value.setSaveFailed)
        source.failure = null
        repeat(3) { viewModel.logSet(id, 1, 80f, 10, true) }
        runCurrent()
        assertEquals(2, source.calls.size)
        assertTrue(viewModel.uiState.value.exercises.first().sets.first().isCompleted)
        assertFalse(viewModel.uiState.value.setSaveFailed)
    }

    @Test
    fun `failed uncheck preserves the completed row and retries using its persisted ID`() = runTest {
        val source = prepareSetWrites()
        runCurrent()
        val id = viewModel.uiState.value.exercises.first().exercise.id
        source.release.complete(Unit)
        viewModel.logSet(id, 1, 80f, 10, true)
        runCurrent()
        val savedId = viewModel.uiState.value.exercises.first().sets.first().currentLog!!.id
        source.failure = IllegalStateException("delete failed")
        viewModel.logSet(id, 1, 80f, 10, false)
        runCurrent()
        assertTrue(viewModel.uiState.value.setSaveFailed)
        assertEquals(savedId, viewModel.uiState.value.exercises.first().sets.first().currentLog!!.id)
        source.failure = null
        viewModel.logSet(id, 1, 80f, 10, false)
        runCurrent()
        assertEquals(savedId, source.calls.last().existingId)
        assertNull(viewModel.uiState.value.exercises.first().sets.first().currentLog)
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
    }

    @Test
    fun `switching routines does not cancel saves or apply stale callbacks to the new session`() = runTest {
        val source = prepareSetWrites()
        val otherId = repository.createWorkout("Pull", 1)
        repository.createExercise(otherId, "Row", "", "", 0)
        runCurrent()
        val firstWorkout = viewModel.uiState.value.selectedWorkout!!
        val id = viewModel.uiState.value.exercises.first().exercise.id
        val sessionId = viewModel.uiState.value.activeSession!!.id
        viewModel.logSet(id, 1, 80f, 10, true, sessionId)
        runCurrent()
        viewModel.selectWorkout(repository.allWorkouts.first().single { it.id == otherId })
        runCurrent()
        val otherSession = viewModel.uiState.value.activeSession!!
        viewModel.updateDraftWeight(viewModel.uiState.value.exercises.single().exercise.id, 1, "50")
        viewModel.logSet(id, 1, 80f, 10, true, sessionId)
        source.release.complete(Unit)
        runCurrent()
        assertEquals(1, source.calls.size)
        assertEquals(otherSession, viewModel.uiState.value.activeSession)
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
        assertTrue(repository.getSetsForSession(otherSession.id).first().isEmpty())
        assertEquals(1, repository.getSetsForSession(sessionId).first().size)
        viewModel.selectWorkout(firstWorkout)
        runCurrent()
        assertTrue(viewModel.uiState.value.exercises.first().sets.first().isCompleted)
    }

    @Test
    fun `cancelled set save releases the guard without presenting a failure`() = runTest {
        val source = prepareSetWrites()
        runCurrent()
        val id = viewModel.uiState.value.exercises.first().exercise.id
        source.failure = CancellationException("cancelled")
        source.release.complete(Unit)
        viewModel.logSet(id, 1, 80f, 10, true)
        runCurrent()
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
        assertFalse(viewModel.uiState.value.setSaveFailed)
        assertFalse(viewModel.uiState.value.exercises.first().sets.first().isCompleted)
    }

    @Test
    fun `returning to a routine before its save finishes still acknowledges the right session`() = runTest {
        val source = prepareSetWrites()
        val otherId = repository.createWorkout("Pull", 1)
        runCurrent()
        val workout = viewModel.uiState.value.selectedWorkout!!
        val id = viewModel.uiState.value.exercises.first().exercise.id
        viewModel.logSet(id, 1, 80f, 10, true)
        runCurrent()
        viewModel.selectWorkout(repository.allWorkouts.first().single { it.id == otherId })
        runCurrent()
        viewModel.selectWorkout(workout)
        runCurrent()
        viewModel.logSet(id, 1, 80f, 10, true)
        source.release.complete(Unit)
        runCurrent()
        assertEquals(1, source.calls.size)
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
        assertTrue(viewModel.uiState.value.exercises.first().sets.first().isCompleted)
    }

    @Test
    fun `switching after persistence before acknowledgement releases the offscreen guard`() = runTest {
        val source = prepareSetWrites()
        val otherId = repository.createWorkout("Pull", 1)
        runCurrent()
        val workout = viewModel.uiState.value.selectedWorkout!!
        val id = viewModel.uiState.value.exercises.first().exercise.id
        source.publishSnapshots.value = false
        source.release.complete(Unit)
        viewModel.logSet(id, 1, 80f, 10, true)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.pendingSetWrites.size)
        viewModel.selectWorkout(repository.allWorkouts.first().single { it.id == otherId })
        runCurrent()
        assertTrue(viewModel.uiState.value.pendingSetWrites.isEmpty())
        source.publishSnapshots.value = true
        viewModel.selectWorkout(workout)
        runCurrent()
        assertTrue(viewModel.uiState.value.exercises.first().sets.first().isCompleted)
        viewModel.logSet(id, 1, 80f, 10, true)
        runCurrent()
        assertEquals(1, source.calls.size)
    }

    @Test
    fun `finalizing blocks set commands and a previous session callback cannot log to the new one`() = runTest {
        val saves = prepareSetWrites()
        val finalizations = ControlledFinalizationRepository(saves)
        initViewModel(finalizations)
        runCurrent()
        val id = viewModel.uiState.value.exercises.first().exercise.id
        val sessionId = viewModel.uiState.value.activeSession!!.id
        saves.release.complete(Unit)
        viewModel.logSet(id, 1, 80f, 10, true, sessionId)
        runCurrent()
        viewModel.finalizeWorkout(sessionId)
        viewModel.logSet(id, 1, 80f, 10, false, sessionId)
        viewModel.logSet(id, 2, 70f, 10, true, sessionId)
        runCurrent()
        assertEquals(1, saves.calls.size)
        assertTrue(viewModel.uiState.value.isFinalizing)
        finalizations.release.complete(Unit)
        runCurrent()
        val newSessionId = viewModel.uiState.value.activeSession!!.id
        assertNotEquals(sessionId, newSessionId)
        viewModel.logSet(id, 1, 90f, 8, true, sessionId)
        runCurrent()
        assertEquals(1, saves.calls.size)
        assertTrue(repository.getSetsForSession(newSessionId).first().isEmpty())
        viewModel.logSet(id, 1, 90f, 8, true, newSessionId)
        runCurrent()
        assertEquals(2, saves.calls.size)
        assertEquals(newSessionId, saves.calls.last().sessionId)
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
        val workoutId = repository.createWorkout("A", 0)
        repository.createExercise(workoutId, "Bench", "", "", 0)
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
    fun `finalization starts without animation delay and ignores duplicate and stale taps`() = runTest {
        repository.createWorkout("A", 0)
        val controlled = ControlledFinalizationRepository(repository)
        initViewModel(controlled)
        runCurrent()
        val sessionId = viewModel.uiState.value.activeSession!!.id

        viewModel.finalizeWorkout(sessionId)
        assertTrue(viewModel.uiState.value.isFinalizing)
        viewModel.finalizeWorkout(sessionId)
        runCurrent()
        assertEquals(listOf(sessionId), controlled.startedSessions.map { it.id })
        assertTrue(repository.getAllCompletedSessions().first().isEmpty())
        assertNull(viewModel.uiState.value.finalizedSessionId)

        controlled.release.complete(Unit)
        runCurrent()
        assertEquals(0L, testScheduler.currentTime)
        assertFalse(viewModel.uiState.value.isFinalizing)
        assertEquals(sessionId, viewModel.uiState.value.finalizedSessionId)
        val nextSessionId = viewModel.uiState.value.activeSession!!.id
        assertNotEquals(sessionId, nextSessionId)
        viewModel.finalizeWorkout(sessionId)
        runCurrent()
        assertEquals(1, controlled.startedSessions.size)
        assertEquals(nextSessionId, viewModel.uiState.value.activeSession!!.id)
        assertEquals(1, repository.getAllCompletedSessions().first().size)

        viewModel.acknowledgeFinalization(sessionId + 100)
        assertEquals(sessionId, viewModel.uiState.value.finalizedSessionId)
        viewModel.acknowledgeFinalization(sessionId)
        assertNull(viewModel.uiState.value.finalizedSessionId)
    }

    @Test
    fun `failed finalization preserves session and drafts and allows retry`() = runTest {
        val workoutId = repository.createWorkout("A", 0)
        repository.createExercise(workoutId, "Bench", "", "", 0)
        val exerciseId = repository.allExercises.first().single().id
        val controlled = ControlledFinalizationRepository(repository)
        controlled.failure = IllegalStateException("Database unavailable")
        controlled.release.complete(Unit)
        initViewModel(controlled)
        runCurrent()
        val session = viewModel.uiState.value.activeSession
        viewModel.updateDraftWeight(exerciseId, 1, "100")
        viewModel.updateDraftReps(exerciseId, 1, "8")

        viewModel.finalizeWorkout()
        runCurrent()
        assertTrue(viewModel.uiState.value.finalizationFailed)
        assertFalse(viewModel.uiState.value.isFinalizing)
        assertNull(viewModel.uiState.value.finalizedSessionId)
        assertEquals(session, viewModel.uiState.value.activeSession)
        assertEquals("100", viewModel.getDraftWeight(exerciseId, 1))
        assertEquals("8", viewModel.getDraftReps(exerciseId, 1))
        assertTrue(repository.getAllCompletedSessions().first().isEmpty())

        viewModel.dismissFinalizationError()
        assertFalse(viewModel.uiState.value.finalizationFailed)
        controlled.failure = null
        viewModel.finalizeWorkout()
        runCurrent()
        assertEquals(2, controlled.startedSessions.size)
        assertEquals(1, repository.getAllCompletedSessions().first().size)
        assertEquals(session!!.id, viewModel.uiState.value.finalizedSessionId)
        assertEquals("", viewModel.getDraftWeight(exerciseId, 1))
        assertEquals("", viewModel.getDraftReps(exerciseId, 1))
    }

    @Test
    fun `switching routine during finalization preserves its session and drafts`() = runTest {
        val firstId = repository.createWorkout("A", 0)
        val secondId = repository.createWorkout("B", 1)
        repository.createExercise(firstId, "Bench", "", "", 0)
        repository.createExercise(secondId, "Squat", "", "", 0)
        val exercises = repository.allExercises.first()
        val firstExerciseId = exercises.first().id
        val secondExerciseId = exercises.last().id
        val controlled = ControlledFinalizationRepository(repository)
        initViewModel(controlled)
        runCurrent()
        val firstSession = viewModel.uiState.value.activeSession!!
        viewModel.updateDraftWeight(firstExerciseId, 1, "100")
        viewModel.finalizeWorkout()
        runCurrent()

        val secondWorkout = repository.allWorkouts.first().last()
        viewModel.selectWorkout(secondWorkout)
        runCurrent()
        val secondSession = viewModel.uiState.value.activeSession
        viewModel.updateDraftWeight(secondExerciseId, 1, "80")
        viewModel.updateDraftReps(secondExerciseId, 1, "10")
        controlled.release.complete(Unit)
        runCurrent()

        assertEquals(secondWorkout, viewModel.uiState.value.selectedWorkout)
        assertEquals(secondSession, viewModel.uiState.value.activeSession)
        assertEquals("80", viewModel.getDraftWeight(secondExerciseId, 1))
        assertEquals("10", viewModel.getDraftReps(secondExerciseId, 1))
        assertEquals("", viewModel.getDraftWeight(firstExerciseId, 1))
        assertEquals(listOf(firstSession.id), repository.getAllCompletedSessions().first().map { it.id })
        viewModel.selectWorkout(repository.allWorkouts.first().first())
        runCurrent()
        assertNotEquals(firstSession.id, viewModel.uiState.value.activeSession!!.id)
    }

    @Test
    fun `leaving the screen does not cancel an accepted finalization`() = runTest {
        repository.createWorkout("A", 0)
        val controlled = ControlledFinalizationRepository(repository)
        initViewModel(controlled)
        runCurrent()
        val sessionId = viewModel.uiState.value.activeSession!!.id
        val screenSubscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        viewModel.finalizeWorkout(sessionId)
        runCurrent()
        screenSubscription.cancel()
        controlled.release.complete(Unit)
        runCurrent()

        assertEquals(listOf(sessionId), repository.getAllCompletedSessions().first().map { it.id })
        assertFalse(viewModel.uiState.value.isFinalizing)
        assertFalse(viewModel.uiState.value.finalizationFailed)
    }

    @Test
    fun `cancelled finalization is not reported as a save failure`() = runTest {
        repository.createWorkout("A", 0)
        val controlled = ControlledFinalizationRepository(repository)
        controlled.failure = CancellationException("ViewModel cleared")
        controlled.release.complete(Unit)
        initViewModel(controlled)
        runCurrent()
        val session = viewModel.uiState.value.activeSession
        viewModel.finalizeWorkout()
        runCurrent()

        assertEquals(session, viewModel.uiState.value.activeSession)
        assertFalse(viewModel.uiState.value.isFinalizing)
        assertFalse(viewModel.uiState.value.finalizationFailed)
        assertNull(viewModel.uiState.value.finalizedSessionId)
        assertTrue(repository.getAllCompletedSessions().first().isEmpty())
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
