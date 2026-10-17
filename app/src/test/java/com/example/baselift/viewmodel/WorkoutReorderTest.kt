package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import com.example.baselift.fakes.FakeWorkoutRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutReorderTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val repository = FakeWorkoutRepository()
    private val store = ViewModelStore()
    private lateinit var vm: WorkoutViewModel
    private lateinit var source: ControlledReorder

    private class Request(val rows: List<ExerciseEntity>) {
        val write = CompletableDeferred<Unit>()
        var afterWrite: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        val ids get() = rows.map { it.id }
    }

    private class ControlledReorder(private val source: IWorkoutRepository) : IWorkoutRepository by source {
        val calls = mutableListOf<Request>()
        val publishExercises = MutableStateFlow(true)
        override fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>> =
            source.getExercisesForWorkout(workoutId).combine(publishExercises) { rows, publish -> rows to publish }
                .filter { it.second }.map { it.first }

        override suspend fun reorderExercises(exercises: List<ExerciseEntity>) {
            val request = Request(exercises)
            calls += request
            request.write.await()
            request.failure?.let { throw it }
            source.reorderExercises(exercises)
            request.afterWrite?.await()
        }
    }

    @After fun tearDown() { store.clear() }

    private suspend fun prepare() {
        val workoutId = repository.createWorkout("Push")
        listOf("Bench", "Row", "Curl").forEachIndexed { index, name ->
            repository.createExercise(workoutId, name, "", "", index)
        }
        source = ControlledReorder(repository)
        vm = WorkoutViewModel(source, main.testDispatcher)
        store.put("workout", vm)
    }

    private fun ids() = vm.uiState.value.exercises.map { it.exercise.id }
    private suspend fun stored(workoutId: Int = 1) = repository.getExercisesForWorkout(workoutId).first().map { it.id }

    @Test fun `invalid redundant and stale callbacks make no writes`() = runTest {
        prepare(); runCurrent()
        vm.moveExercise(0, 0)
        vm.moveExercise(-1, 1)
        vm.moveExercise(0, 3)
        vm.moveExercise(0, 1, expectedWorkoutId = 2)
        vm.moveExercise(0, 1, expectedExerciseId = 3)
        runCurrent()
        assertEquals(listOf(1, 2, 3), ids())
        assertTrue(source.calls.isEmpty())
    }

    @Test fun `rapid intents before worker starts persist only the newest order`() = runTest {
        prepare(); runCurrent()
        vm.moveExercise(0, 2)
        assertEquals(listOf(2, 3, 1), ids())
        vm.moveExercise(0, 1)
        assertEquals(listOf(3, 2, 1), ids())
        runCurrent()
        assertEquals(1, source.calls.size)
        assertEquals(ids(), source.calls.single().ids)
        assertEquals(listOf(0, 1, 2), source.calls.single().rows.map { it.orderIndex })
        source.calls.single().write.complete(Unit); runCurrent()
        assertEquals(ids(), stored())
        // A completed worker must not prevent a later drag from starting another one.
        vm.moveExercise(2, 0); runCurrent()
        assertEquals(2, source.calls.size)
        source.calls.last().write.complete(Unit); runCurrent()
        assertEquals(ids(), stored())
    }

    @Test fun `busy worker serializes writes and skips all intermediate queued orders`() = runTest {
        prepare(); runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        repeat(5) { vm.moveExercise(0, 1) }
        val newest = ids()
        runCurrent()
        assertEquals(1, source.calls.size)
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(2, source.calls.size)
        assertEquals(newest, ids()) // Older database acknowledgement cannot rewind the display.
        assertEquals(newest, source.calls[1].ids)
        source.calls[1].write.complete(Unit); runCurrent()
        assertEquals(newest, stored())
        assertEquals(newest, ids())
    }

    @Test fun `fresh metadata counts logs and drafts survive while order is pending`() = runTest {
        prepare(); runCurrent()
        vm.updateDraftWeight(1, 1, "80")
        vm.updateDraftReps(1, 1, "10")
        vm.moveExercise(0, 2); runCurrent()
        vm.updateExercise(1, "Renamed", "Machine", "Chest")
        vm.addSetToExercise(1)
        vm.logSet(1, 1, 80f, 10, true)
        runCurrent()
        assertEquals(listOf(2, 3, 1), ids())
        val model = vm.uiState.value.exercises.last()
        assertEquals("Renamed", model.exercise.name)
        assertEquals("Machine", model.exercise.equipment)
        assertEquals(2, model.sets.size)
        assertTrue(model.sets.first().isCompleted)
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals("80", vm.getDraftWeight(1, 1))
        assertEquals("10", vm.getDraftReps(1, 1))
        assertEquals("Renamed", vm.uiState.value.exercises.last().exercise.name)
        assertEquals(2, vm.uiState.value.exercises.last().sets.size)
        assertEquals(ids(), stored())
    }

    @Test fun `successful return before model emission retains order until acknowledgement`() = runTest {
        prepare(); runCurrent()
        source.publishExercises.value = false; runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(listOf(2, 3, 1), ids())
        // A log emission combines with the old exercise snapshot while Room delivery lags.
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        assertEquals(listOf(2, 3, 1), ids())
        source.publishExercises.value = true; runCurrent()
        assertEquals(ids(), stored())
        // Once acknowledged, later authoritative order updates are no longer masked.
        repository.reorderExercises(repository.allExercises.first().sortedBy { it.id }
            .mapIndexed { index, row -> row.copy(orderIndex = index) })
        runCurrent()
        assertEquals(listOf(1, 2, 3), ids())
    }

    @Test fun `model before repository return is reconciled when the write returns`() = runTest {
        prepare(); runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        val returned = CompletableDeferred<Unit>()
        source.calls[0].afterWrite = returned
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(ids(), stored())
        returned.complete(Unit); runCurrent()
        repository.reorderExercises(repository.allExercises.first().sortedBy { it.id }
            .mapIndexed { index, row -> row.copy(orderIndex = index) })
        runCurrent()
        assertEquals(listOf(1, 2, 3), ids())
    }

    @Test fun `latest failure restores fresh database models preserves drafts and permits retry`() = runTest {
        prepare(); runCurrent()
        vm.updateDraftWeight(1, 1, "80")
        vm.moveExercise(0, 2); runCurrent()
        vm.updateExercise(1, "Renamed", "", "")
        vm.addSetToExercise(1); runCurrent()
        source.calls[0].failure = IllegalStateException("injected")
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(stored(), ids())
        assertEquals("Renamed", vm.uiState.value.exercises.first().exercise.name)
        assertEquals(2, vm.uiState.value.exercises.first().sets.size)
        assertEquals("80", vm.getDraftWeight(1, 1))
        assertEquals(setOf(1), vm.uiState.value.reorderFailedWorkoutIds)
        vm.dismissReorderError(1)
        assertTrue(vm.uiState.value.reorderFailedWorkoutIds.isEmpty())
        vm.moveExercise(0, 2); runCurrent()
        source.calls[1].write.complete(Unit); runCurrent()
        assertEquals(listOf(2, 3, 1), stored())
        assertEquals(stored(), ids())
    }

    @Test fun `superseded failure does not discard or report failure for a newer successful intent`() = runTest {
        prepare(); runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        vm.moveExercise(0, 1)
        source.calls[0].failure = IllegalStateException("old intent failed")
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(listOf(3, 2, 1), ids())
        assertTrue(vm.uiState.value.reorderFailedWorkoutIds.isEmpty())
        source.calls[1].write.complete(Unit); runCurrent()
        assertEquals(ids(), stored())
    }

    @Test fun `failed newer intent falls back to earlier saved order even with delayed delivery`() = runTest {
        prepare(); runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        vm.moveExercise(0, 1)
        source.publishExercises.value = false; runCurrent()
        source.calls[0].write.complete(Unit); runCurrent()
        source.calls[1].failure = IllegalStateException("new intent failed")
        source.calls[1].write.complete(Unit); runCurrent()
        assertEquals(setOf(1), vm.uiState.value.reorderFailedWorkoutIds)
        source.publishExercises.value = true; runCurrent()
        assertEquals(listOf(2, 3, 1), ids())
        assertEquals(ids(), stored())
    }

    @Test fun `different routines have independent workers and saved orders`() = runTest {
        prepare(); runCurrent()
        val other = repository.createWorkout("Legs")
        repository.createExercise(other, "Squat", "", "", 0)
        repository.createExercise(other, "Lunge", "", "", 1)
        runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        vm.selectWorkout(repository.allWorkouts.first().last()); runCurrent()
        vm.moveExercise(0, 1); runCurrent()
        assertEquals(2, source.calls.size)
        assertEquals(1, source.calls[0].rows.first().workoutId)
        assertEquals(other, source.calls[1].rows.first().workoutId)
        source.calls[1].write.complete(Unit); runCurrent()
        assertEquals(listOf(5, 4), ids())
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(listOf(5, 4), ids())
        vm.selectWorkout(repository.allWorkouts.first().first()); runCurrent()
        assertEquals(listOf(2, 3, 1), ids())
    }

    @Test fun `switching away and back during a pending save retains the latest intent`() = runTest {
        prepare(); runCurrent()
        val other = repository.createWorkout("Legs")
        repository.createExercise(other, "Squat", "", "", 0); runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        vm.selectWorkout(repository.allWorkouts.first().last()); runCurrent()
        vm.selectWorkout(repository.allWorkouts.first().first()); runCurrent()
        assertEquals(listOf(2, 3, 1), ids())
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(ids(), stored())
    }

    @Test fun `offscreen failure is scoped to its routine and does not replace the selected rows`() = runTest {
        prepare(); runCurrent()
        val other = repository.createWorkout("Legs")
        repository.createExercise(other, "Squat", "", "", 0); runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        vm.selectWorkout(repository.allWorkouts.first().last()); runCurrent()
        source.calls[0].failure = IllegalStateException("injected")
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(listOf(4), ids())
        assertEquals(setOf(1), vm.uiState.value.reorderFailedWorkoutIds)
        vm.dismissReorderError(other)
        assertEquals(setOf(1), vm.uiState.value.reorderFailedWorkoutIds)
        vm.selectWorkout(repository.allWorkouts.first().first()); runCurrent()
        assertEquals(stored(), ids())
        vm.moveExercise(0, 1)
        assertTrue(vm.uiState.value.reorderFailedWorkoutIds.isEmpty())
    }

    @Test fun `deleted and newly added rows are not resurrected or hidden by pending order`() = runTest {
        prepare(); runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        vm.deleteExercise(2); runCurrent()
        vm.createExercise("New", "", ""); runCurrent()
        assertEquals(listOf(3, 1, 4), ids())
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(listOf(3, 1, 4), ids())
        assertEquals(ids(), stored())
    }

    @Test fun `repository cancellation clears pending order without reporting failure and can retry`() = runTest {
        prepare(); runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        vm.moveExercise(0, 1)
        source.calls[0].failure = CancellationException("cancelled")
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(stored(), ids())
        assertTrue(vm.uiState.value.reorderFailedWorkoutIds.isEmpty())
        assertEquals(1, source.calls.size)
        vm.moveExercise(0, 2); runCurrent()
        source.calls[1].write.complete(Unit); runCurrent()
        assertEquals(ids(), stored())
    }

    @Test fun `ViewModel disposal cancels the worker and queued unsaved order`() = runTest {
        prepare(); runCurrent()
        vm.moveExercise(0, 2); runCurrent()
        vm.moveExercise(0, 1)
        store.clear(); runCurrent()
        source.calls[0].write.complete(Unit); runCurrent()
        assertEquals(1, source.calls.size)
        assertEquals(listOf(1, 2, 3), stored())
        assertTrue(vm.uiState.value.reorderFailedWorkoutIds.isEmpty())
    }

    @Test fun `immediate repository completion cannot leave a worker blocking future drags`() = runTest {
        val immediate = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(immediate)
        try {
            val workoutId = repository.createWorkout("Push")
            listOf("Bench", "Row", "Curl").forEachIndexed { index, name ->
                repository.createExercise(workoutId, name, "", "", index)
            }
            vm = WorkoutViewModel(repository, immediate)
            store.put("workout", vm)
            runCurrent()
            vm.moveExercise(0, 2)
            runCurrent()
            assertEquals(listOf(2, 3, 1), stored())
            vm.moveExercise(0, 1)
            runCurrent()
            assertEquals(listOf(3, 2, 1), stored())
            assertEquals(stored(), ids())
        } finally {
            Dispatchers.setMain(main.testDispatcher)
        }
    }
}
