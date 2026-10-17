package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
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
class WorkoutSubscriptionsTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val repository = FakeWorkoutRepository()
    private val store = ViewModelStore()
    private lateinit var vm: WorkoutViewModel
    private lateinit var source: TrackedRepository

    private class TrackedRepository(private val source: IWorkoutRepository) : IWorkoutRepository by source {
        var activeExercises = 0
        var activeSets = 0
        var exerciseStarts = 0
        var setStarts = 0
        var exerciseEmissions = 0
        var setEmissions = 0
        var sessions = 0
        var histories = 0
        var setWrites = 0
        var removals = 0
        var edits = 0
        var reorders = 0
        val publish = MutableStateFlow(true)
        var beforeWrite: suspend () -> Unit = {}
        var beforeRemove: suspend () -> Unit = {}
        var beforeEdit: suspend () -> Unit = {}
        var beforeReorder: suspend () -> Unit = {}
        var beforeFinalize: suspend () -> Unit = {}
        var failModels = false

        override suspend fun startOrGetSession(workoutId: Int) = source.startOrGetSession(workoutId).also { sessions++ }
        override suspend fun getPreviousSetsForWorkout(workoutId: Int) = source.getPreviousSetsForWorkout(workoutId).also { histories++ }
        override fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>> = flow {
            activeExercises++; exerciseStarts++
            try {
                emitAll(source.getExercisesForWorkout(workoutId).combine(publish) { rows, enabled -> rows to enabled }
                    .filter { it.second }.map {
                        if (failModels) error("injected observation failure")
                        exerciseEmissions++; it.first
                    })
            } finally { activeExercises-- }
        }
        override fun getSetsForSession(sessionId: Int): Flow<List<SetLogEntity>> = flow {
            activeSets++; setStarts++
            try {
                emitAll(source.getSetsForSession(sessionId).combine(publish) { rows, enabled -> rows to enabled }
                    .filter { it.second }.map { setEmissions++; it.first })
            }
            finally { activeSets-- }
        }
        override suspend fun logSet(sessionId: Int, exerciseId: Int, setNumber: Int, weight: Float,
            reps: Int, isCompleted: Boolean, existingSetId: Int) {
            setWrites++; beforeWrite()
            source.logSet(sessionId, exerciseId, setNumber, weight, reps, isCompleted, existingSetId)
        }
        override suspend fun removeLastSet(exercise: ExerciseEntity, sessionId: Int): Boolean {
            removals++; beforeRemove(); return source.removeLastSet(exercise, sessionId)
        }
        override suspend fun updateExercise(exercise: ExerciseEntity, name: String, equipment: String, muscleGroups: String) {
            edits++; beforeEdit(); source.updateExercise(exercise, name, equipment, muscleGroups)
        }
        override suspend fun reorderExercises(exercises: List<ExerciseEntity>) {
            reorders++; beforeReorder(); source.reorderExercises(exercises)
        }
        override suspend fun finalizeSession(session: com.example.baselift.Model.local.entity.WorkoutSessionEntity) {
            beforeFinalize(); source.finalizeSession(session)
        }
    }

    @After fun tearDown() { store.clear() }

    private suspend fun prepare() {
        val push = repository.createWorkout("Push")
        listOf("Bench", "Row", "Curl").forEachIndexed { index, name ->
            repository.createExercise(push, name, "Barbell", "Chest", index)
        }
        repository.updateExercises(repository.allExercises.first().map { it.copy(setCount = 2) })
        val legs = repository.createWorkout("Legs")
        repository.createExercise(legs, "Squat", "", "", 0)
        source = TrackedRepository(repository)
        vm = WorkoutViewModel(source, main.testDispatcher)
        store.put("workout", vm)
    }

    private fun TestScope.watch() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    private fun TestScope.stop(observer: Job) {
        observer.cancel(); runCurrent(); advanceTimeBy(1_000); runCurrent()
        assertEquals(0, source.activeExercises); assertEquals(0, source.activeSets)
    }
    private fun ids() = vm.uiState.value.exercises.map { it.exercise.id }

    @Test fun `startup defers selection and row observation until presentation demand`() = runTest {
        prepare(); runCurrent()
        assertEquals(0, source.sessions); assertEquals(0, source.histories)
        assertEquals(0, source.exerciseStarts); assertEquals(0, source.setStarts)
        assertTrue(vm.uiState.value.exercises.isEmpty())
        watch(); runCurrent()
        assertEquals(listOf(1, 2, 3), ids())
        assertEquals(1, source.activeExercises); assertEquals(1, source.activeSets)
    }

    @Test fun `shared observers stop exactly after last subscriber grace and preserve drafts`() = runTest {
        prepare(); val first = watch(); val second = watch(); runCurrent()
        val cached = vm.uiState.value.exercises
        vm.updateDraftWeight(1, 2, "75"); vm.updateDraftReps(1, 2, "8")
        first.cancel(); advanceTimeBy(2_000); runCurrent()
        assertEquals(1, source.activeExercises)
        second.cancel(); runCurrent(); advanceTimeBy(999); runCurrent()
        assertEquals(1, source.activeExercises)
        advanceTimeBy(1); runCurrent()
        assertEquals(0, source.activeExercises); assertEquals(0, source.activeSets)
        assertSame(cached, vm.uiState.value.exercises)
        assertTrue(vm.uiState.value.isSelectionLoading)
        assertEquals("75", vm.getDraftWeight(1, 2)); assertEquals("8", vm.getDraftReps(1, 2))
    }

    @Test fun `quick return reuses observation and long return reuses session and history`() = runTest {
        prepare(); val first = watch(); runCurrent()
        val session = vm.uiState.value.activeSession
        first.cancel(); runCurrent(); advanceTimeBy(999)
        val second = watch(); runCurrent()
        assertEquals(1, source.exerciseStarts); assertFalse(vm.uiState.value.isSelectionLoading)
        stop(second); watch(); runCurrent()
        assertEquals(2, source.exerciseStarts); assertEquals(2, source.setStarts)
        assertEquals(1, source.sessions); assertEquals(1, source.histories)
        assertEquals(session, vm.uiState.value.activeSession)
        store.clear(); runCurrent()
        assertEquals(0, source.activeExercises); assertEquals(0, source.activeSets)
    }

    @Test fun `paused mutations do no model work and return reads fresh rows order counts and logs`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        val session = vm.uiState.value.activeSession!!
        stop(observer)
        val emissions = source.exerciseEmissions to source.setEmissions
        val rows = repository.getExercisesForWorkout(1).first()
        repository.updateExercise(rows[0], "Incline", "Machine", "Upper chest")
        repository.addSet(rows[0])
        repository.reorderExercises(rows.reversed().mapIndexed { index, row -> row.copy(orderIndex = index) })
        repository.logSet(session.id, 1, 1, 80f, 10, true, 0)
        repository.createExercise(1, "Press", "", "", 3)
        runCurrent()
        assertEquals(emissions, source.exerciseEmissions to source.setEmissions)
        assertEquals("Bench", vm.uiState.value.exercises.first().exercise.name)
        watch(); runCurrent()
        assertEquals(listOf(3, 2, 1, 5), ids())
        val updated = vm.uiState.value.exercises.single { it.exercise.id == 1 }
        assertEquals("Incline", updated.exercise.name); assertEquals(3, updated.sets.size)
        assertTrue(updated.sets.first().isCompleted)
        assertFalse(vm.uiState.value.isSelectionLoading)
        assertEquals(1, source.sessions); assertEquals(1, source.histories)
    }

    @Test fun `stale callbacks while paused or awaiting fresh rows cannot submit new commands`() = runTest {
        prepare(); val observer = watch(); runCurrent(); stop(observer)
        fun staleCallbacks() {
            vm.logSet(1, 1, 80f, 10, true); vm.addSetToExercise(1); vm.removeLastSet(1)
            vm.deleteExercise(1); vm.updateExercise(1, "Stale", "", ""); vm.moveExercise(0, 1)
            vm.beginExerciseEdit(1); vm.createExercise("Stale", "", ""); vm.finalizeWorkout()
        }
        staleCallbacks(); runCurrent()
        source.publish.value = false; watch(); runCurrent()
        staleCallbacks(); runCurrent()
        assertEquals(0, source.setWrites); assertEquals(0, source.removals)
        assertEquals(0, source.edits); assertEquals(0, source.reorders)
        assertEquals(listOf("Bench", "Row", "Curl"), repository.getExercisesForWorkout(1).first().map { it.name })
        assertTrue(repository.getSetsForSession(vm.uiState.value.activeSession!!.id).first().isEmpty())
        source.publish.value = true; runCurrent()
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        assertEquals(1, source.setWrites)
    }

    @Test fun `accepted set write completes while paused and releases its guard without an observer`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        val gate = CompletableDeferred<Unit>(); source.beforeWrite = { gate.await() }
        val session = vm.uiState.value.activeSession!!
        vm.logSet(1, 1, 80f, 10, true); runCurrent(); stop(observer)
        assertEquals(1, vm.uiState.value.pendingSetWrites.size)
        gate.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.pendingSetWrites.isEmpty())
        assertEquals(1, repository.getSetsForSession(session.id).first().size)
        watch(); runCurrent()
        assertTrue(vm.uiState.value.exercises.first().sets.first().isCompleted)
        assertEquals(1, source.setWrites)
    }

    @Test fun `saved set acknowledgement is released at detach and failure remains retryable on return`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        source.publish.value = false; runCurrent()
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        assertEquals(1, vm.uiState.value.pendingSetWrites.size)
        stop(observer); assertTrue(vm.uiState.value.pendingSetWrites.isEmpty())
        source.publish.value = true
        val next = watch(); runCurrent()
        val gate = CompletableDeferred<Unit>(); source.beforeWrite = { gate.await(); error("write failed") }
        vm.logSet(1, 2, 75f, 8, true); runCurrent(); stop(next)
        gate.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.setSaveFailed); assertTrue(vm.uiState.value.pendingSetWrites.isEmpty())
        watch(); runCurrent(); source.beforeWrite = {}
        vm.logSet(1, 2, 75f, 8, true); runCurrent()
        assertFalse(vm.uiState.value.setSaveFailed)
        assertTrue(vm.uiState.value.exercises.first().sets[1].isCompleted)
    }

    @Test fun `accepted removal survives pause and clears only its successful set draft`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        vm.updateDraftWeight(1, 1, "80"); vm.updateDraftWeight(1, 2, "75")
        val gate = CompletableDeferred<Unit>(); source.beforeRemove = { gate.await() }
        vm.removeLastSet(1); runCurrent(); stop(observer)
        gate.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty())
        assertEquals("", vm.getDraftWeight(1, 2)); assertEquals("80", vm.getDraftWeight(1, 1))
        watch(); runCurrent()
        assertEquals(1, vm.uiState.value.exercises.first().sets.size)
    }

    @Test fun `saved removal and metadata acknowledgements release when rows stop`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        source.publish.value = false; runCurrent()
        vm.removeLastSet(1)
        vm.beginExerciseEdit(2); vm.updateExerciseEditDraft(2, name = "Cable row"); vm.saveExerciseEdit(2)
        runCurrent()
        assertEquals(1, vm.uiState.value.pendingSetRemovals.size)
        assertTrue(vm.uiState.value.exerciseEdits.getValue(2).isSaving)
        stop(observer)
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty()); assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        source.publish.value = true; watch(); runCurrent()
        assertEquals(1, vm.uiState.value.exercises.first().sets.size)
        assertEquals("Cable row", vm.uiState.value.exercises[1].exercise.name)
    }

    @Test fun `running and queued reorder intents persist across detach without a stale overlay on return`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        val gate = CompletableDeferred<Unit>(); source.beforeReorder = { gate.await() }
        vm.moveExercise(0, 2); runCurrent()
        vm.moveExercise(0, 1); runCurrent()
        val expected = ids(); stop(observer)
        gate.complete(Unit); runCurrent()
        assertEquals(2, source.reorders)
        assertEquals(expected, repository.getExercisesForWorkout(1).first().map { it.id })
        val rows = repository.getExercisesForWorkout(1).first()
        repository.reorderExercises(rows.reversed().mapIndexed { index, row -> row.copy(orderIndex = index) })
        watch(); runCurrent()
        assertEquals(expected.reversed(), ids())
    }

    @Test fun `offscreen edit failure retains fields and retries while offscreen success releases pending state`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        val gate = CompletableDeferred<Unit>(); source.beforeEdit = { gate.await(); error("edit failed") }
        vm.beginExerciseEdit(1); vm.updateExerciseEditDraft(1, name = "Incline"); vm.saveExerciseEdit(1)
        runCurrent(); stop(observer); gate.complete(Unit); runCurrent()
        val failed = vm.uiState.value.exerciseEdits.getValue(1)
        assertEquals("Incline", failed.name); assertTrue(failed.saveFailed); assertFalse(failed.isSaving)
        val next = watch(); runCurrent()
        val retryGate = CompletableDeferred<Unit>(); source.beforeEdit = { retryGate.await() }
        vm.saveExerciseEdit(1); runCurrent(); stop(next); retryGate.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        watch(); runCurrent()
        assertEquals("Incline", vm.uiState.value.exercises.first().exercise.name)
    }

    @Test fun `accepted finalization while paused saves then defers session renewal until return`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        val oldSession = vm.uiState.value.activeSession!!
        val gate = CompletableDeferred<Unit>(); source.beforeFinalize = { gate.await() }
        vm.finalizeWorkout(); runCurrent(); stop(observer)
        gate.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.isFinalizing); assertFalse(vm.uiState.value.finalizationFailed)
        assertNull(vm.uiState.value.activeSession)
        assertEquals(oldSession.id, vm.uiState.value.finalizedSessionId)
        assertEquals(1, source.sessions); assertEquals(1, source.histories)
        assertEquals(0, source.activeExercises); assertEquals(1, source.exerciseStarts)
        watch(); runCurrent()
        assertEquals(80f, vm.uiState.value.exercises.first().sets.first().prevWeight!!, 0f)
        assertFalse(vm.uiState.value.exercises.first().sets.first().isCompleted)
        assertEquals(2, source.sessions); assertEquals(2, source.histories)
    }

    @Test fun `stale routine callbacks while paused are ignored until current routines return`() = runTest {
        prepare(); val observer = watch(); runCurrent(); stop(observer)
        val legs = repository.allWorkouts.first().single { it.id == 2 }
        vm.selectWorkout(legs); runCurrent()
        assertEquals(0, source.activeExercises); assertEquals(1, source.exerciseStarts)
        watch(); runCurrent()
        assertEquals(listOf(1, 2, 3), ids())
        assertEquals(1, source.sessions)
        vm.selectWorkout(legs); runCurrent()
        assertEquals(listOf(4), ids()); assertEquals(2, vm.uiState.value.activeSession?.workoutId)
        assertEquals(2, source.sessions); assertEquals(2, source.histories)
    }

    @Test fun `return before a set save finishes preserves its guard until the fresh saved model`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        val gate = CompletableDeferred<Unit>(); source.beforeWrite = { gate.await() }
        vm.logSet(1, 1, 80f, 10, true); runCurrent(); stop(observer)
        watch(); runCurrent()
        assertEquals(1, vm.uiState.value.pendingSetWrites.size)
        assertFalse(vm.uiState.value.exercises.first().sets.first().isCompleted)
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        assertEquals(1, source.setWrites)
        gate.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.pendingSetWrites.isEmpty())
        assertTrue(vm.uiState.value.exercises.first().sets.first().isCompleted)
        assertEquals(1, repository.getSetsForSession(vm.uiState.value.activeSession!!.id).first().size)
    }

    @Test fun `saved reorder waiting for model acknowledgement releases on detach`() = runTest {
        prepare(); val observer = watch(); runCurrent()
        source.publish.value = false; runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        assertEquals(listOf(2, 3, 1), ids())
        assertEquals(listOf(2, 3, 1), repository.getExercisesForWorkout(1).first().map { it.id })
        stop(observer)
        val rows = repository.getExercisesForWorkout(1).first()
        repository.reorderExercises(rows.reversed().mapIndexed { index, row -> row.copy(orderIndex = index) })
        source.publish.value = true; watch(); runCurrent()
        assertEquals(listOf(1, 3, 2), ids())
        vm.moveExercise(0, 1); runCurrent()
        assertEquals(2, source.reorders)
        assertEquals(ids(), repository.getExercisesForWorkout(1).first().map { it.id })
    }

    @Test fun `accepted set addition finishes after detach without keeping observation alive`() = runTest {
        val gate = CompletableDeferred<Unit>()
        prepare()
        val delayed = object : IWorkoutRepository by source {
            override suspend fun addSet(exercise: ExerciseEntity) {
                gate.await(); repository.addSet(exercise)
            }
        }
        vm = WorkoutViewModel(delayed, main.testDispatcher)
        store.put("workout", vm)
        val observer = watch(); runCurrent()
        vm.addSetToExercise(1); runCurrent(); stop(observer)
        assertEquals(setOf(1), vm.uiState.value.addingSetExerciseIds)
        gate.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.addingSetExerciseIds.isEmpty())
        assertEquals(0, source.activeExercises)
        watch(); runCurrent()
        assertEquals(3, vm.uiState.value.exercises.first().sets.size)
    }

    @Test fun `resumed observation failure leaves retry feedback and recovers without stuck guards`() = runTest {
        prepare(); val observer = watch(); runCurrent(); stop(observer)
        source.failModels = true; watch(); runCurrent()
        assertTrue(vm.uiState.value.selectionLoadFailed); assertFalse(vm.uiState.value.isSelectionLoading)
        assertNull(vm.uiState.value.activeSession); assertTrue(vm.uiState.value.exercises.isEmpty())
        source.failModels = false; vm.retryWorkoutSelection(1); runCurrent()
        assertFalse(vm.uiState.value.selectionLoadFailed); assertEquals(listOf(1, 2, 3), ids())
        assertEquals(1, source.activeExercises); assertEquals(1, source.activeSets)
    }
}
