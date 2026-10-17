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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutStartupTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val repository = FakeWorkoutRepository()
    private val store = ViewModelStore()
    private lateinit var vm: WorkoutViewModel
    private val source = TrackedRepository(repository)

    private class TrackedRepository(private val source: IWorkoutRepository) : IWorkoutRepository by source {
        var recoveries = 0
        var listStarts = 0
        var listActive = 0
        var listEmissions = 0
        var sessions = 0
        var histories = 0
        var exercisesActive = 0
        var setsActive = 0
        val exerciseWorkouts = mutableListOf<Int>()
        val publishRoutines = MutableStateFlow(true)
        val failRoutines = MutableStateFlow(false)
        var beforeRecovery: suspend () -> Unit = {}
        var afterRecovery: suspend (WorkoutSessionEntity?) -> WorkoutSessionEntity? = { it }
        var beforeSession: suspend () -> Unit = {}
        var beforeDelete: suspend () -> Unit = {}
        var beforeCreate: suspend () -> Unit = {}
        var beforeWrite: suspend () -> Unit = {}

        override suspend fun getAnyActiveSession(): WorkoutSessionEntity? {
            recoveries++; beforeRecovery()
            return afterRecovery(source.getAnyActiveSession())
        }
        override val allWorkouts: Flow<List<WorkoutEntity>> = flow {
            listStarts++; listActive++
            try {
                emitAll(combine(source.allWorkouts, publishRoutines, failRoutines) { rows, publish, fail ->
                    if (fail) error("injected routine observation failure")
                    rows to publish
                }.filter { it.second }.map { listEmissions++; it.first })
            } finally { listActive-- }
        }
        override suspend fun startOrGetSession(workoutId: Int): WorkoutSessionEntity {
            sessions++; beforeSession(); return source.startOrGetSession(workoutId)
        }
        override suspend fun getPreviousSetsForWorkout(workoutId: Int) = source.getPreviousSetsForWorkout(workoutId).also { histories++ }
        override fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>> = flow {
            exerciseWorkouts += workoutId; exercisesActive++
            try { emitAll(source.getExercisesForWorkout(workoutId)) }
            finally { exercisesActive-- }
        }
        override fun getSetsForSession(sessionId: Int): Flow<List<SetLogEntity>> = flow {
            setsActive++
            try { emitAll(source.getSetsForSession(sessionId)) }
            finally { setsActive-- }
        }
        override suspend fun deleteWorkout(workout: WorkoutEntity) { beforeDelete(); source.deleteWorkout(workout) }
        override suspend fun createWorkout(name: String, orderIndex: Int): Int {
            beforeCreate(); return source.createWorkout(name, orderIndex)
        }
        override suspend fun logSet(sessionId: Int, exerciseId: Int, setNumber: Int, weight: Float,
            reps: Int, isCompleted: Boolean, existingSetId: Int) {
            beforeWrite(); source.logSet(sessionId, exerciseId, setNumber, weight, reps, isCompleted, existingSetId)
        }
    }

    @After fun tearDown() { store.clear() }

    private suspend fun prepare() {
        val push = repository.createWorkout("Push")
        repository.createExercise(push, "Bench", "", "", 0)
        val legs = repository.createWorkout("Legs")
        repository.createExercise(legs, "Squat", "", "", 0)
        vm = WorkoutViewModel(source, main.testDispatcher)
        store.put("workout", vm)
    }
    private fun TestScope.watch() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    private fun TestScope.stop(observer: Job) {
        observer.cancel(); runCurrent(); advanceTimeBy(1_000); runCurrent()
        assertEquals(0, source.listActive); assertEquals(0, source.exercisesActive); assertEquals(0, source.setsActive)
    }
    private fun selected() = vm.uiState.value.selectedWorkout?.id

    @Test fun `constructing unused Workout performs no recovery session history or presentation queries`() = runTest {
        prepare(); runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(0, source.recoveries); assertEquals(0, source.listStarts)
        assertEquals(0, source.sessions); assertEquals(0, source.histories)
        assertTrue(source.exerciseWorkouts.isEmpty()); assertNull(selected())
        assertTrue(vm.uiState.value.isLoading); assertFalse(vm.uiState.value.workoutsLoadFailed)
        watch(); runCurrent()
        assertEquals(1, source.recoveries); assertEquals(1, source.listStarts)
        assertEquals(1, source.sessions); assertEquals(1, source.histories)
    }

    @Test fun `first demand recovers active routine before falling back to first template`() = runTest {
        prepare(); val active = repository.startOrGetSession(2)
        watch(); runCurrent()
        assertEquals(2, selected()); assertEquals(active, vm.uiState.value.activeSession)
        assertEquals("Squat", vm.uiState.value.exercises.single().exercise.name)
        assertEquals(listOf(2), source.exerciseWorkouts)
    }

    @Test fun `shared demand grace stops all three subscriptions and quick returns reuse them`() = runTest {
        prepare(); val first = watch(); val second = watch(); runCurrent()
        first.cancel(); advanceTimeBy(2_000); runCurrent()
        assertEquals(1, source.listActive)
        second.cancel(); runCurrent(); advanceTimeBy(999); runCurrent()
        assertEquals(1, source.listActive)
        val third = watch(); runCurrent()
        assertEquals(1, source.listStarts); assertFalse(vm.uiState.value.isLoading)
        stop(third)
        assertTrue(vm.uiState.value.isLoading)
        watch(); runCurrent()
        assertEquals(2, source.listStarts); assertEquals(1, source.recoveries)
        assertEquals(1, source.sessions); assertEquals(1, source.histories)
        store.clear(); runCurrent()
        assertEquals(0, source.listActive); assertEquals(0, source.exercisesActive); assertEquals(0, source.setsActive)
    }

    @Test fun `paused list mutations do no processing and refresh metadata without selection recovery`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        vm.updateDraftWeight(1, 1, "80"); val session = vm.uiState.value.activeSession
        stop(observer); val emissions = source.listEmissions
        repository.updateWorkout(repository.allWorkouts.first().first().copy(name = "Upper", defaultRestTimer = 60))
        repository.createWorkout("New", 2); runCurrent()
        assertEquals(emissions, source.listEmissions)
        assertEquals("Push", vm.uiState.value.selectedWorkout?.name)
        watch(); runCurrent()
        assertEquals("Upper", vm.uiState.value.selectedWorkout?.name)
        assertEquals(60, vm.uiState.value.selectedWorkout?.defaultRestTimer)
        assertEquals(3, vm.uiState.value.workouts.size); assertEquals(session, vm.uiState.value.activeSession)
        assertEquals("80", vm.getDraftWeight(1, 1))
        assertEquals(1, source.recoveries); assertEquals(1, source.sessions); assertEquals(1, source.histories)
    }

    @Test fun `fresh routine membership is required before any resumed row queries or cached commands`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        val old = vm.uiState.value.selectedWorkout!!; stop(observer)
        repository.deleteWorkout(old); source.publishRoutines.value = false
        watch(); runCurrent()
        assertTrue(vm.uiState.value.isLoading); assertEquals(0, source.exercisesActive)
        vm.selectWorkout(old); vm.createWorkout("Stale"); vm.deleteWorkout(old); vm.updateWorkoutRestTimer(30)
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        assertEquals(1, source.sessions); assertEquals(listOf(1), source.exerciseWorkouts)
        assertEquals(listOf("Legs"), repository.allWorkouts.first().map { it.name })
        source.publishRoutines.value = true; runCurrent()
        assertEquals(2, selected()); assertEquals(listOf(1, 2), source.exerciseWorkouts)
        assertEquals(2, source.sessions); assertEquals(2, source.histories)
        assertEquals("Squat", vm.uiState.value.exercises.single().exercise.name)
    }

    @Test fun `deleting all routines while paused clears selection only from the fresh empty list`() = runTest {
        prepare(); val observer = watch(); runCurrent(); stop(observer)
        vm.deleteAllWorkouts(); runCurrent()
        assertEquals(0, source.listActive); assertEquals(1, source.sessions)
        watch(); runCurrent()
        assertNull(selected()); assertNull(vm.uiState.value.activeSession)
        assertTrue(vm.uiState.value.workouts.isEmpty()); assertTrue(vm.uiState.value.exercises.isEmpty())
        assertFalse(vm.uiState.value.isLoading); assertFalse(vm.uiState.value.isSelectionLoading)
        vm.createWorkout("New"); runCurrent()
        assertEquals("New", vm.uiState.value.selectedWorkout?.name); assertEquals(2, source.sessions)
    }

    @Test fun `failed initial active recovery preserves drafts and coalesces explicit retry taps`() = runTest {
        prepare(); var failing = true
        source.beforeRecovery = { if (failing) error("recovery failed") }
        vm.updateDraftWeight(1, 1, "80"); watch(); runCurrent()
        assertTrue(vm.uiState.value.workoutsLoadFailed); assertFalse(vm.uiState.value.isLoading)
        assertEquals(0, source.listStarts); assertEquals(0, source.sessions)
        failing = false; repeat(5) { vm.retryWorkouts() }; runCurrent()
        assertEquals(2, source.recoveries); assertEquals(1, source.listStarts)
        assertEquals(1, source.sessions); assertEquals("80", vm.getDraftWeight(1, 1))
        assertFalse(vm.uiState.value.workoutsLoadFailed)
        vm.retryWorkouts(); runCurrent(); assertEquals(1, source.listStarts)
    }

    @Test fun `failed first routine query retries without repeating successful initial recovery`() = runTest {
        prepare(); source.failRoutines.value = true; watch(); runCurrent()
        assertTrue(vm.uiState.value.workoutsLoadFailed); assertEquals(0, source.sessions)
        source.failRoutines.value = false; repeat(5) { vm.retryWorkouts() }; runCurrent()
        assertEquals(1, source.recoveries); assertEquals(2, source.listStarts)
        assertEquals(1, source.sessions); assertEquals(1, selected())
    }

    @Test fun `live routine failure stops rows but accepted saves complete and retry reuses selection`() = runTest {
        prepare(); watch(); runCurrent()
        val gate = CompletableDeferred<Unit>(); source.beforeWrite = { gate.await() }
        vm.updateDraftWeight(1, 1, "80"); vm.logSet(1, 1, 80f, 10, true); runCurrent()
        source.failRoutines.value = true; runCurrent()
        assertTrue(vm.uiState.value.workoutsLoadFailed); assertEquals(0, source.exercisesActive)
        gate.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.pendingSetWrites.isEmpty())
        assertEquals(1, repository.getSetsForSession(vm.uiState.value.activeSession!!.id).first().size)
        source.failRoutines.value = false; vm.retryWorkouts(); runCurrent()
        assertTrue(vm.uiState.value.exercises.single().sets.first().isCompleted)
        assertEquals("80", vm.getDraftWeight(1, 1))
        assertEquals(1, source.sessions); assertEquals(1, source.histories); assertEquals(1, source.recoveries)
    }

    @Test fun `return after routine failure retries current data while preserving the selected routine`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        vm.selectWorkout(repository.allWorkouts.first().last()); runCurrent()
        source.failRoutines.value = true; runCurrent(); stop(observer)
        source.failRoutines.value = false
        vm.retryWorkouts(); runCurrent(); assertEquals(1, source.listStarts)
        watch(); runCurrent()
        assertEquals(2, selected()); assertFalse(vm.uiState.value.workoutsLoadFailed)
        assertEquals(2, source.sessions); assertEquals(2, source.histories); assertEquals(1, source.recoveries)
    }

    @Test fun `cancelled initial recovery cannot publish late active selection or an obsolete error`() = runTest {
        prepare(); val gate = CompletableDeferred<Unit>()
        source.afterRecovery = { withContext(NonCancellable) { gate.await(); error("late recovery failure") } }
        val observer = watch(); runCurrent(); stop(observer)
        assertFalse(vm.uiState.value.workoutsLoadFailed); assertEquals(0, source.listStarts)
        source.afterRecovery = { it }; gate.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.workoutsLoadFailed)
        watch(); runCurrent()
        assertEquals(2, source.recoveries); assertEquals(1, source.listStarts); assertEquals(1, selected())
    }

    @Test fun `recovery cancelled during a short visit retries later without reporting a failure`() = runTest {
        prepare(); val gate = CompletableDeferred<Unit>()
        source.beforeRecovery = { gate.await() }
        val observer = watch(); runCurrent(); stop(observer)
        assertFalse(vm.uiState.value.workoutsLoadFailed); assertEquals(0, source.sessions)
        gate.complete(Unit); watch(); runCurrent()
        assertEquals(2, source.recoveries); assertEquals(1, source.sessions)
    }

    @Test fun `already started session setup finishes after pause and does not repeat on return`() = runTest {
        prepare(); val gate = CompletableDeferred<Unit>(); source.beforeSession = { gate.await() }
        val observer = watch(); runCurrent(); stop(observer)
        gate.complete(Unit); runCurrent()
        assertEquals(1, source.sessions); assertEquals(1, source.histories)
        assertEquals(0, source.exercisesActive); assertTrue(source.exerciseWorkouts.isEmpty())
        watch(); runCurrent()
        assertEquals(1, source.sessions); assertEquals(1, source.histories)
        assertEquals(listOf(1), source.exerciseWorkouts)
    }

    @Test fun `accepted routine deletion while paused does not start a replacement session until return`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        val gate = CompletableDeferred<Unit>(); source.beforeDelete = { gate.await() }
        vm.deleteWorkout(vm.uiState.value.selectedWorkout!!); runCurrent(); stop(observer)
        gate.complete(Unit); runCurrent()
        assertEquals(1, source.sessions); assertEquals(0, source.listActive)
        assertEquals(listOf(2), repository.allWorkouts.first().map { it.id })
        watch(); runCurrent()
        assertEquals(2, selected()); assertEquals(2, source.sessions)
        assertEquals(listOf(1, 2), source.exerciseWorkouts)
    }

    @Test fun `accepted routine creation while paused does not trigger eager selection`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        val gate = CompletableDeferred<Unit>(); source.beforeCreate = { gate.await() }
        vm.createWorkout("New"); runCurrent(); stop(observer)
        gate.complete(Unit); runCurrent()
        assertEquals(3, repository.allWorkouts.first().size)
        assertEquals(1, source.sessions); assertEquals(0, source.listActive)
        watch(); runCurrent()
        assertEquals(3, vm.uiState.value.workouts.size); assertEquals(1, selected()); assertEquals(1, source.sessions)
    }
}
