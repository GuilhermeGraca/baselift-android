package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import com.example.baselift.fakes.FakeWorkoutRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSetRemovalTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val repository = FakeWorkoutRepository()
    private val store = ViewModelStore()
    private lateinit var vm: WorkoutViewModel

    @After fun tearDown() { store.clear() }

    private class ControlledRemoval(private val source: IWorkoutRepository) : IWorkoutRepository by source {
        val calls = mutableListOf<Pair<ExerciseEntity, Int>>()
        val release = CompletableDeferred<Unit>()
        val publishExercises = MutableStateFlow(true)
        val publishLogs = MutableStateFlow(true)
        var failure: Exception? = null
        var reject = false
        var afterWrite: CompletableDeferred<Unit>? = null

        override fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>> =
            source.getExercisesForWorkout(workoutId).combine(publishExercises) { rows, publish -> rows to publish }
                .filter { it.second }.map { it.first }

        override fun getSetsForSession(sessionId: Int): Flow<List<SetLogEntity>> =
            source.getSetsForSession(sessionId).combine(publishLogs) { rows, publish -> rows to publish }
                .filter { it.second }.map { it.first }

        override suspend fun removeLastSet(exercise: ExerciseEntity, sessionId: Int): Boolean {
            calls += exercise to sessionId
            release.await()
            failure?.let { throw it }
            if (reject) return false
            val removed = source.removeLastSet(exercise, sessionId)
            afterWrite?.await()
            return removed
        }
    }

    private suspend fun prepare(): ControlledRemoval {
        val workout = repository.createWorkout("Push")
        repository.createExercise(workout, "Bench", "", "", 0)
        repository.createExercise(workout, "Press", "", "", 1)
        repository.updateExercises(repository.allExercises.first().map { it.copy(setCount = 3) })
        val exercise = repository.allExercises.first().first()
        val session = repository.startOrGetSession(workout)
        (1..3).forEach { repository.logSet(session.id, exercise.id, it, 80f, 10, true, 0) }
        val source = ControlledRemoval(repository)
        initialize(source)
        vm.updateDraftWeight(exercise.id, 2, "70")
        vm.updateDraftWeight(exercise.id, 3, "80")
        vm.updateDraftReps(exercise.id, 3, "10")
        return source
    }

    private fun initialize(source: IWorkoutRepository) {
        vm = WorkoutViewModel(source, main.testDispatcher)
        store.put("workout", vm)
    }

    @Test fun `duplicate removal is guarded through persistence and clears only successful drafts`() = runTest {
        val source = prepare()
        runCurrent()
        val exerciseId = vm.uiState.value.exercises.first().exercise.id
        val sessionId = vm.uiState.value.activeSession!!.id
        repeat(4) { vm.removeLastSet(exerciseId, 3, sessionId) }
        assertEquals(1, vm.uiState.value.pendingSetRemovals.size)
        vm.updateDraftWeight(exerciseId, 3, "999")
        vm.updateDraftReps(exerciseId, 3, "99")
        vm.addSetToExercise(exerciseId)
        vm.logSet(exerciseId, 3, 80f, 10, false)
        vm.deleteExercise(exerciseId)
        vm.finalizeWorkout(sessionId)
        // Independent metadata/order edits must not undo the pending count mutation.
        vm.updateExercise(exerciseId, "Renamed", "Machine", "Chest")
        vm.moveExercise(0, 1)
        runCurrent()
        assertEquals(1, source.calls.size)
        assertEquals("80", vm.getDraftWeight(exerciseId, 3))
        assertEquals("10", vm.getDraftReps(exerciseId, 3))
        assertFalse(vm.uiState.value.isFinalizing)
        source.release.complete(Unit)
        runCurrent()
        val after = vm.uiState.value.exercises.single { it.exercise.id == exerciseId }
        assertEquals(2, after.sets.size)
        assertEquals("Renamed", after.exercise.name)
        assertEquals(1, after.exercise.orderIndex)
        assertEquals("", vm.getDraftWeight(exerciseId, 3))
        assertEquals("", vm.getDraftReps(exerciseId, 3))
        assertEquals("70", vm.getDraftWeight(exerciseId, 2))
        assertEquals(listOf(1, 2), repository.getSetsForSession(sessionId).first().map { it.setNumber })
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty())
        vm.removeLastSet(exerciseId, 3, sessionId)
        runCurrent()
        assertEquals(1, source.calls.size)
        assertTrue(vm.uiState.value.setRemovalFailed)
        vm.removeLastSet(exerciseId, 2, sessionId)
        runCurrent()
        vm.removeLastSet(exerciseId, 1, sessionId)
        runCurrent()
        assertEquals(2, source.calls.size)
        assertEquals(1, vm.uiState.value.exercises.single { it.exercise.id == exerciseId }.sets.size)
    }

    @Test fun `failed removal preserves logs count and drafts and retries once`() = runTest {
        val source = prepare()
        runCurrent()
        val id = vm.uiState.value.exercises.first().exercise.id
        val session = vm.uiState.value.activeSession!!.id
        val before = repository.getSetsForSession(session).first()
        source.failure = IllegalStateException("transaction failed")
        source.release.complete(Unit)
        vm.removeLastSet(id, 3)
        runCurrent()
        assertEquals(before, repository.getSetsForSession(session).first())
        assertEquals(3, vm.uiState.value.exercises.first().sets.size)
        assertEquals("80", vm.getDraftWeight(id, 3))
        assertEquals("10", vm.getDraftReps(id, 3))
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty())
        assertTrue(vm.uiState.value.setRemovalFailed)
        vm.dismissSetRemovalError()
        assertFalse(vm.uiState.value.setRemovalFailed)
        source.failure = null
        repeat(3) { vm.removeLastSet(id, 3) }
        runCurrent()
        assertEquals(2, source.calls.size)
        assertEquals(2, vm.uiState.value.exercises.first().sets.size)
        assertFalse(vm.uiState.value.setRemovalFailed)
    }

    @Test fun `rejected transaction keeps drafts and allows a fresh confirmation`() = runTest {
        val source = prepare()
        runCurrent()
        val id = vm.uiState.value.exercises.first().exercise.id
        source.reject = true
        source.release.complete(Unit)
        vm.removeLastSet(id, 3)
        runCurrent()
        assertTrue(vm.uiState.value.setRemovalFailed)
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty())
        assertEquals(3, vm.uiState.value.exercises.first().sets.size)
        assertEquals("80", vm.getDraftWeight(id, 3))
        source.reject = false
        vm.removeLastSet(id, 3)
        runCurrent()
        assertEquals(2, vm.uiState.value.exercises.first().sets.size)
        assertFalse(vm.uiState.value.setRemovalFailed)
    }

    @Test fun `acknowledgement requires both updated count and removed log snapshots`() = runTest {
        val source = prepare()
        runCurrent()
        val id = vm.uiState.value.exercises.first().exercise.id
        source.publishLogs.value = false
        source.release.complete(Unit)
        vm.removeLastSet(id, 3)
        runCurrent()
        // Count is fresh, but the stale log still causes the model to contain row 3.
        assertEquals(2, vm.uiState.value.exercises.first().exercise.setCount)
        assertEquals(3, vm.uiState.value.exercises.first().sets.size)
        assertEquals(1, vm.uiState.value.pendingSetRemovals.size)
        // A stale row's initial-sync callback must not repopulate a removed draft.
        vm.updateDraftWeight(id, 3, "80")
        assertEquals("", vm.getDraftWeight(id, 3))
        vm.logSet(id, 3, 80f, 10, false)
        vm.addSetToExercise(id)
        vm.finalizeWorkout()
        runCurrent()
        assertFalse(vm.uiState.value.isFinalizing)
        source.publishLogs.value = true
        runCurrent()
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty())
        assertEquals(2, vm.uiState.value.exercises.first().sets.size)

        // Test the opposite emission order with an unlogged last set.
        vm.addSetToExercise(id)
        runCurrent()
        source.publishExercises.value = false
        vm.removeLastSet(id, 3)
        runCurrent()
        assertEquals(3, vm.uiState.value.exercises.first().sets.size)
        assertEquals(1, vm.uiState.value.pendingSetRemovals.size)
        source.publishExercises.value = true
        runCurrent()
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty())
        assertEquals(2, vm.uiState.value.exercises.first().sets.size)
    }

    @Test fun `early snapshots cannot acknowledge an unfinished repository call`() = runTest {
        val source = prepare()
        runCurrent()
        val id = vm.uiState.value.exercises.first().exercise.id
        source.afterWrite = CompletableDeferred()
        source.release.complete(Unit)
        vm.removeLastSet(id, 3)
        runCurrent()
        assertEquals(2, vm.uiState.value.exercises.first().sets.size)
        assertEquals(1, vm.uiState.value.pendingSetRemovals.size)
        assertEquals("80", vm.getDraftWeight(id, 3))
        vm.finalizeWorkout()
        runCurrent()
        assertFalse(vm.uiState.value.isFinalizing)
        source.afterWrite!!.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty())
        assertEquals("", vm.getDraftWeight(id, 3))
    }

    @Test fun `routine switches preserve accepted removals and other routine drafts`() = runTest {
        val source = prepare()
        val other = repository.createWorkout("Pull", 1)
        repository.createExercise(other, "Row", "", "", 0)
        runCurrent()
        val workout = vm.uiState.value.selectedWorkout!!
        val id = vm.uiState.value.exercises.first().exercise.id
        val sessionId = vm.uiState.value.activeSession!!.id
        vm.removeLastSet(id, 3, sessionId)
        runCurrent()
        vm.selectWorkout(repository.allWorkouts.first().single { it.id == other })
        runCurrent()
        val otherSession = vm.uiState.value.activeSession!!
        val otherExercise = vm.uiState.value.exercises.single().exercise.id
        vm.updateDraftWeight(otherExercise, 1, "50")
        source.release.complete(Unit)
        runCurrent()
        assertEquals(otherSession, vm.uiState.value.activeSession)
        assertEquals("50", vm.getDraftWeight(otherExercise, 1))
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty())
        assertEquals(sessionId, source.calls.single().second)
        vm.selectWorkout(workout)
        runCurrent()
        assertEquals(2, vm.uiState.value.exercises.first().sets.size)
    }

    @Test fun `adds block removal in flight and stale post-add confirmations cannot delete a different set`() = runTest {
        val source = prepare()
        val addRelease = CompletableDeferred<Unit>()
        val delayedAdds = object : IWorkoutRepository by source {
            override suspend fun addSet(exercise: ExerciseEntity) {
                addRelease.await()
                source.addSet(exercise)
            }
        }
        initialize(delayedAdds)
        runCurrent()
        val id = vm.uiState.value.exercises.first().exercise.id
        vm.updateDraftWeight(id, 3, "80")
        vm.updateDraftReps(id, 3, "10")
        repeat(2) { vm.addSetToExercise(id) }
        vm.removeLastSet(id, 3)
        runCurrent()
        assertEquals(setOf(id), vm.uiState.value.addingSetExerciseIds)
        assertTrue(source.calls.isEmpty())
        // Persistence may beat its model emission; SQL must reject the stale count too.
        source.publishExercises.value = false
        addRelease.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value.addingSetExerciseIds.isEmpty())
        assertEquals(3, vm.uiState.value.exercises.first().sets.size)
        source.release.complete(Unit)
        vm.removeLastSet(id, 3)
        runCurrent()
        assertTrue(vm.uiState.value.setRemovalFailed)
        assertEquals("80", vm.getDraftWeight(id, 3))
        assertEquals(3, repository.getSetsForSession(vm.uiState.value.activeSession!!.id).first().size)
        source.publishExercises.value = true
        runCurrent()
        assertEquals(5, vm.uiState.value.exercises.first().sets.size)
    }

    @Test fun `cancelled removals clear pending without reporting failure or discarding inputs`() = runTest {
        val source = prepare()
        runCurrent()
        val id = vm.uiState.value.exercises.first().exercise.id
        source.failure = CancellationException("cancelled")
        source.release.complete(Unit)
        vm.removeLastSet(id, 3)
        runCurrent()
        assertFalse(vm.uiState.value.setRemovalFailed)
        assertTrue(vm.uiState.value.pendingSetRemovals.isEmpty())
        assertEquals("80", vm.getDraftWeight(id, 3))
        assertEquals(3, vm.uiState.value.exercises.first().sets.size)
    }

    @Test fun `old session confirmations cannot remove from the next session`() = runTest {
        val source = prepare()
        runCurrent()
        val id = vm.uiState.value.exercises.first().exercise.id
        val oldSession = vm.uiState.value.activeSession!!.id
        vm.finalizeWorkout(oldSession)
        runCurrent()
        assertNotEquals(oldSession, vm.uiState.value.activeSession!!.id)
        vm.removeLastSet(id, 3, oldSession)
        runCurrent()
        assertTrue(source.calls.isEmpty())
        assertEquals(3, repository.getSetsForSession(oldSession).first().size)
        assertEquals(3, vm.uiState.value.exercises.first().sets.size)
    }
}
