package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import com.example.baselift.fakes.FakeWorkoutRepository
import com.example.baselift.helpers.observeForTest
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSelectionTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val repository = FakeWorkoutRepository()
    private val store = ViewModelStore()
    private lateinit var vm: WorkoutViewModel
    private lateinit var source: ControlledSelection

    private class ControlledSelection(private val source: IWorkoutRepository) : IWorkoutRepository by source {
        val sessionReads = mutableListOf<Int>()
        val historyReads = mutableListOf<Int>()
        val exerciseReads = mutableListOf<Int>()
        var beforeSession: suspend (Int, Int) -> Unit = { _, _ -> }
        var afterSession: suspend (Int, Int, WorkoutSessionEntity) -> WorkoutSessionEntity = { _, _, row -> row }
        var beforeHistory: suspend (Int, Int) -> Unit = { _, _ -> }
        var modelsFailure: Exception? = null

        override suspend fun startOrGetSession(workoutId: Int): WorkoutSessionEntity {
            sessionReads += workoutId
            val attempt = sessionReads.count { it == workoutId }
            beforeSession(workoutId, attempt)
            return afterSession(workoutId, attempt, source.startOrGetSession(workoutId))
        }

        override suspend fun getPreviousSetsForWorkout(workoutId: Int): List<SetLogEntity> {
            historyReads += workoutId
            beforeHistory(workoutId, historyReads.count { it == workoutId })
            return source.getPreviousSetsForWorkout(workoutId)
        }

        override fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>> {
            exerciseReads += workoutId
            return source.getExercisesForWorkout(workoutId).map { rows ->
                modelsFailure?.let { throw it }
                rows
            }
        }
    }

    @After fun tearDown() { store.clear() }

    private suspend fun fixtures() {
        val push = repository.createWorkout("Push")
        repository.createExercise(push, "Bench", "", "", 0)
        val legs = repository.createWorkout("Legs")
        repository.createExercise(legs, "Squat", "", "", 0)
        source = ControlledSelection(repository)
    }

    private fun start() {
        vm = WorkoutViewModel(source, main.testDispatcher)
        store.put("workout", vm); vm.observeForTest()
    }

    private suspend fun select(id: Int) = vm.selectWorkout(repository.allWorkouts.first().single { it.id == id })

    @Test fun `repeated healthy selection preserves models drafts session and query counts`() = runTest {
        fixtures(); start(); runCurrent()
        val session = vm.uiState.value.activeSession
        val models = vm.uiState.value.exercises
        vm.updateDraftWeight(1, 1, "80")
        vm.updateDraftReps(1, 1, "10")
        repeat(5) { select(1) }
        assertSame(models, vm.uiState.value.exercises)
        assertEquals(session, vm.uiState.value.activeSession)
        assertFalse(vm.uiState.value.isSelectionLoading)
        runCurrent()
        assertEquals(listOf(1), source.sessionReads)
        assertEquals(listOf(1), source.historyReads)
        assertEquals(listOf(1), source.exerciseReads)
        assertEquals("80", vm.getDraftWeight(1, 1))
        assertEquals("10", vm.getDraftReps(1, 1))
    }

    @Test fun `repeated selection during session recovery does not restart the load`() = runTest {
        fixtures()
        val gate = CompletableDeferred<Unit>()
        source.beforeSession = { _, _ -> gate.await() }
        start(); runCurrent()
        repeat(5) { select(1) }; runCurrent()
        assertTrue(vm.uiState.value.isSelectionLoading)
        assertNull(vm.uiState.value.activeSession)
        assertEquals(listOf(1), source.sessionReads)
        assertTrue(source.historyReads.isEmpty())
        gate.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.isSelectionLoading)
        assertEquals(1, vm.uiState.value.exercises.size)
    }

    @Test fun `repeated selection during history preparation retains the same request`() = runTest {
        fixtures()
        val gate = CompletableDeferred<Unit>()
        source.beforeHistory = { _, _ -> gate.await() }
        start(); runCurrent()
        val session = vm.uiState.value.activeSession
        repeat(5) { select(1) }; runCurrent()
        assertTrue(vm.uiState.value.isSelectionLoading)
        assertTrue(vm.uiState.value.exercises.isEmpty())
        assertEquals(listOf(1), source.sessionReads)
        assertEquals(listOf(1), source.historyReads)
        gate.complete(Unit); runCurrent()
        assertEquals(session, vm.uiState.value.activeSession)
        assertEquals(1, vm.uiState.value.exercises.size)
    }

    @Test fun `cancelled late session result cannot overwrite a newer attempt for the same routine`() = runTest {
        fixtures()
        val gate = CompletableDeferred<Unit>()
        source.afterSession = { workoutId, attempt, session ->
            if (workoutId == 1 && attempt == 1) withContext(NonCancellable) {
                gate.await(); session.copy(id = 999)
            } else session
        }
        start(); runCurrent()
        select(2); runCurrent()
        select(1); runCurrent()
        val current = vm.uiState.value
        gate.complete(Unit); runCurrent()
        assertEquals(current, vm.uiState.value)
        assertNotEquals(999, vm.uiState.value.activeSession?.id)
        assertEquals(listOf(2, 1), source.historyReads)
        assertEquals(listOf(2, 1), source.exerciseReads)
    }

    @Test fun `cancelled late history failure cannot clear newer state or show an obsolete error`() = runTest {
        fixtures()
        val gate = CompletableDeferred<Unit>()
        source.beforeHistory = { workoutId, attempt ->
            if (workoutId == 1 && attempt == 1) withContext(NonCancellable) {
                gate.await(); throw IllegalStateException("old load failed late")
            }
        }
        start(); runCurrent()
        select(2); runCurrent()
        select(1); runCurrent()
        val current = vm.uiState.value
        gate.complete(Unit); runCurrent()
        assertEquals(current, vm.uiState.value)
        assertFalse(vm.uiState.value.selectionLoadFailed)
        assertFalse(vm.uiState.value.isSelectionLoading)
    }

    @Test fun `session failure is recoverable and repeated retry taps start one request`() = runTest {
        fixtures()
        source.beforeSession = { _, attempt -> if (attempt == 1) error("injected") }
        start(); runCurrent()
        assertTrue(vm.uiState.value.selectionLoadFailed)
        assertFalse(vm.uiState.value.isSelectionLoading)
        assertNull(vm.uiState.value.activeSession)
        vm.updateDraftWeight(1, 1, "80")
        vm.retryWorkoutSelection(2); runCurrent()
        assertEquals(1, source.sessionReads.size)
        repeat(4) { vm.retryWorkoutSelection(1) }; runCurrent()
        assertEquals(2, source.sessionReads.size)
        assertFalse(vm.uiState.value.selectionLoadFailed)
        assertEquals("80", vm.getDraftWeight(1, 1))
        assertEquals("Bench", vm.uiState.value.exercises.single().exercise.name)
    }

    @Test fun `history failure preserves stored logs drafts and session identity across retry`() = runTest {
        fixtures()
        val session = repository.startOrGetSession(1)
        repository.logSet(session.id, 1, 1, 80f, 10, true, 0)
        source.beforeHistory = { _, attempt -> if (attempt == 1) error("injected") }
        start(); runCurrent()
        assertTrue(vm.uiState.value.selectionLoadFailed)
        assertTrue(vm.uiState.value.exercises.isEmpty())
        vm.updateDraftWeight(1, 2, "75")
        vm.retryWorkoutSelection(1); runCurrent()
        assertEquals(session.id, vm.uiState.value.activeSession?.id)
        assertTrue(vm.uiState.value.exercises.single().sets.first().isCompleted)
        assertEquals(1, repository.getSetsForSession(session.id).first().size)
        assertEquals("75", vm.getDraftWeight(1, 2))
    }

    @Test fun `failed exercise observation can reconnect and a healthy retry callback is ignored`() = runTest {
        fixtures(); start(); runCurrent()
        vm.retryWorkoutSelection(1); runCurrent()
        assertEquals(1, source.sessionReads.size)
        source.modelsFailure = IllegalStateException("query failed")
        repository.updateExercise(repository.allExercises.first().first(), "Renamed", "", "")
        runCurrent()
        assertTrue(vm.uiState.value.selectionLoadFailed)
        assertNull(vm.uiState.value.activeSession)
        assertTrue(vm.uiState.value.exercises.isEmpty())
        source.modelsFailure = null
        vm.retryWorkoutSelection(1); runCurrent()
        assertFalse(vm.uiState.value.selectionLoadFailed)
        assertEquals("Renamed", vm.uiState.value.exercises.single().exercise.name)
    }

    @Test fun `source cancellation is not reported as a load failure and a later tap can retry`() = runTest {
        fixtures()
        source.beforeHistory = { _, attempt -> if (attempt == 1) throw CancellationException("cancelled") }
        start(); runCurrent()
        assertFalse(vm.uiState.value.selectionLoadFailed)
        assertFalse(vm.uiState.value.isSelectionLoading)
        select(1); runCurrent()
        assertEquals(2, source.sessionReads.size)
        assertEquals(1, vm.uiState.value.exercises.size)
    }

    @Test fun `routine metadata emissions update selection without restarting history`() = runTest {
        fixtures(); start(); runCurrent()
        val stale = vm.uiState.value.selectedWorkout!!
        repository.updateWorkout(stale.copy(name = "Renamed", defaultRestTimer = 60)); runCurrent()
        select(1); runCurrent()
        assertEquals("Renamed", vm.uiState.value.selectedWorkout?.name)
        assertEquals(60, vm.uiState.value.selectedWorkout?.defaultRestTimer)
        assertEquals(listOf(1), source.sessionReads)
        assertEquals(listOf(1), source.historyReads)
        vm.selectWorkout(stale)
        assertEquals("Renamed", vm.uiState.value.selectedWorkout?.name)
    }

    @Test fun `successful finalization forces fresh session and history despite healthy same selection`() = runTest {
        fixtures(); start(); runCurrent()
        val old = vm.uiState.value.activeSession!!.id
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        select(1); runCurrent()
        assertEquals(1, source.historyReads.size)
        vm.finalizeWorkout(old); runCurrent()
        assertNotEquals(old, vm.uiState.value.activeSession?.id)
        assertEquals(2, source.historyReads.size)
        assertEquals(80f, vm.uiState.value.exercises.single().sets.first().prevWeight!!, 0f)
        assertFalse(vm.uiState.value.exercises.single().sets.first().isCompleted)
    }

    @Test fun `deleting the final routine clears selection and creating another loads it once`() = runTest {
        fixtures(); start(); runCurrent()
        vm.deleteAllWorkouts(); runCurrent()
        assertNull(vm.uiState.value.selectedWorkout)
        assertNull(vm.uiState.value.activeSession)
        assertTrue(vm.uiState.value.exercises.isEmpty())
        assertFalse(vm.uiState.value.isSelectionLoading)
        assertFalse(vm.uiState.value.selectionLoadFailed)
        vm.createWorkout("New"); runCurrent()
        assertEquals("New", vm.uiState.value.selectedWorkout?.name)
        assertEquals(2, source.sessionReads.size)
    }

    @Test fun `deselection cancels pending history and late data does not reopen the routine`() = runTest {
        fixtures()
        val gate = CompletableDeferred<Unit>()
        source.beforeHistory = { _, _ -> withContext(NonCancellable) { gate.await() } }
        start(); runCurrent()
        vm.selectWorkout(null)
        gate.complete(Unit); runCurrent()
        assertNull(vm.uiState.value.selectedWorkout)
        assertNull(vm.uiState.value.activeSession)
        assertTrue(vm.uiState.value.exercises.isEmpty())
        assertFalse(vm.uiState.value.isSelectionLoading)
        assertTrue(source.exerciseReads.isEmpty())
    }

    @Test fun `late rest timer save cannot replace a different selected routine`() = runTest {
        fixtures()
        val gate = CompletableDeferred<Unit>()
        val delayed = object : IWorkoutRepository by source {
            override suspend fun updateWorkout(workout: com.example.baselift.Model.local.entity.WorkoutEntity) {
                gate.await()
                repository.updateWorkout(workout)
            }
        }
        vm = WorkoutViewModel(delayed, main.testDispatcher)
        store.put("workout", vm); vm.observeForTest(); runCurrent()
        vm.updateWorkoutRestTimer(60); runCurrent()
        select(2); runCurrent()
        val session = vm.uiState.value.activeSession
        gate.complete(Unit); runCurrent()
        assertEquals(2, vm.uiState.value.selectedWorkout?.id)
        assertEquals(session, vm.uiState.value.activeSession)
        assertEquals("Squat", vm.uiState.value.exercises.single().exercise.name)
        assertEquals(60, repository.allWorkouts.first().first().defaultRestTimer)
        assertEquals(listOf(1, 2), source.sessionReads)
    }

    @Test fun `finalization save remains successful when next session loading needs a retry`() = runTest {
        fixtures(); start(); runCurrent()
        val completed = vm.uiState.value.activeSession!!.id
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        source.beforeHistory = { _, attempt -> if (attempt == 2) error("refresh failed") }
        vm.finalizeWorkout(completed); runCurrent()
        assertEquals(listOf(completed), repository.getAllCompletedSessions().first().map { it.id })
        assertFalse(vm.uiState.value.finalizationFailed)
        assertTrue(vm.uiState.value.selectionLoadFailed)
        val created = repository.getActiveSessionFlow(1).first()!!.id
        assertNotEquals(completed, created)
        vm.retryWorkoutSelection(1); runCurrent()
        assertEquals(created, vm.uiState.value.activeSession?.id)
        assertFalse(vm.uiState.value.selectionLoadFailed)
        assertEquals(80f, vm.uiState.value.exercises.single().sets.first().prevWeight!!, 0f)
    }

    @Test fun `stale finalize callback cannot finalize a session still awaiting history`() = runTest {
        fixtures()
        val gate = CompletableDeferred<Unit>()
        source.beforeHistory = { _, _ -> gate.await() }
        start(); runCurrent()
        val session = vm.uiState.value.activeSession!!.id
        vm.finalizeWorkout(session); runCurrent()
        assertTrue(repository.getAllCompletedSessions().first().isEmpty())
        gate.complete(Unit); runCurrent()
        assertEquals(session, vm.uiState.value.activeSession?.id)
    }
}
