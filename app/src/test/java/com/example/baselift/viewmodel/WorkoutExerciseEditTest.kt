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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutExerciseEditTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val repository = FakeWorkoutRepository()
    private val store = ViewModelStore()
    private lateinit var vm: WorkoutViewModel
    private lateinit var source: ControlledEdit

    private class Request(val row: ExerciseEntity, val name: String, val equipment: String, val muscles: String) {
        val release = CompletableDeferred<Unit>()
        var afterWrite: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
    }

    private class ControlledEdit(private val source: IWorkoutRepository) : IWorkoutRepository by source {
        val calls = mutableListOf<Request>()
        val publish = kotlinx.coroutines.flow.MutableStateFlow(true)
        override fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>> =
            source.getExercisesForWorkout(workoutId).combine(publish) { rows, enabled -> rows to enabled }
                .filter { it.second }.map { it.first }
        override suspend fun updateExercise(exercise: ExerciseEntity, name: String, equipment: String, muscleGroups: String) {
            val call = Request(exercise, name, equipment, muscleGroups)
            calls += call
            call.release.await()
            call.failure?.let { throw it }
            source.updateExercise(exercise, name, equipment, muscleGroups)
            call.afterWrite?.await()
        }
    }

    @After fun tearDown() { store.clear() }

    private suspend fun prepare() {
        val push = repository.createWorkout("Push")
        repository.createExercise(push, "Bench", "Barbell", "Chest", 0)
        repository.createExercise(push, "Row", "Cable", "Back", 1)
        val legs = repository.createWorkout("Legs")
        repository.createExercise(legs, "Squat", "Barbell", "Legs", 0)
        source = ControlledEdit(repository)
        vm = WorkoutViewModel(source, main.testDispatcher)
        store.put("workout", vm)
    }

    private fun edit(id: Int = 1) = vm.uiState.value.exerciseEdits.getValue(id)
    private fun change(id: Int = 1) {
        vm.beginExerciseEdit(id)
        vm.updateExerciseEditDraft(id, name = "Incline", equipment = "Machine", muscleGroups = "Upper chest")
    }

    @Test fun `opening and updating individual fields preserves the other fields and cancel discards unsaved input`() = runTest {
        prepare(); runCurrent()
        vm.beginExerciseEdit(1)
        assertEquals("Bench", edit().name)
        vm.updateExerciseEditDraft(1, name = "Incline")
        vm.updateExerciseEditDraft(1, equipment = "")
        vm.updateExerciseEditDraft(1, muscleGroups = "Upper chest")
        assertEquals("Incline", edit().name)
        assertEquals("", edit().equipment)
        vm.dismissExerciseEdit(1)
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        vm.beginExerciseEdit(1)
        assertEquals("Bench", edit().name)
        assertTrue(source.calls.isEmpty())
    }

    @Test fun `blank names missing exercises and stale save callbacks make no writes`() = runTest {
        prepare(); runCurrent()
        vm.beginExerciseEdit(999)
        vm.saveExerciseEdit(999)
        vm.beginExerciseEdit(1)
        vm.updateExerciseEditDraft(1, name = "  ")
        vm.saveExerciseEdit(1)
        vm.dismissExerciseEdit(1)
        vm.saveExerciseEdit(1)
        runCurrent()
        assertTrue(source.calls.isEmpty())
    }

    @Test fun `save guards duplicate commands and freezes the submitted fields until acknowledged`() = runTest {
        prepare(); runCurrent(); change()
        repeat(5) { vm.saveExerciseEdit(1) }
        vm.updateExerciseEditDraft(1, name = "Stale callback")
        assertTrue(edit().isSaving)
        assertEquals("Incline", edit().name)
        runCurrent()
        assertEquals(1, source.calls.size)
        assertEquals("Incline", source.calls[0].name)
        source.calls[0].release.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        assertEquals("Incline", vm.uiState.value.exercises.first().exercise.name)
    }

    @Test fun `failure preserves all entries and logs for an edited retry`() = runTest {
        prepare(); runCurrent()
        val session = vm.uiState.value.activeSession!!.id
        vm.updateDraftWeight(1, 1, "80")
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        change(); vm.saveExerciseEdit(1); runCurrent()
        source.calls[0].failure = IllegalStateException("injected")
        source.calls[0].release.complete(Unit); runCurrent()
        assertTrue(edit().isOpen)
        assertFalse(edit().isSaving)
        assertTrue(edit().saveFailed)
        assertEquals("Incline", edit().name)
        assertEquals("Machine", edit().equipment)
        assertEquals("Upper chest", edit().muscleGroups)
        assertEquals("Bench", vm.uiState.value.exercises.first().exercise.name)
        assertEquals(1, repository.getSetsForSession(session).first().size)
        vm.updateExerciseEditDraft(1, name = "Incline press")
        assertFalse(edit().saveFailed)
        vm.saveExerciseEdit(1); runCurrent()
        source.calls[1].release.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        assertEquals("Incline press", vm.uiState.value.exercises.first().exercise.name)
        assertEquals("80", vm.getDraftWeight(1, 1))
        assertEquals(1, repository.getSetsForSession(session).first().size)
    }

    @Test fun `repository return before model does not close the form or admit another save`() = runTest {
        prepare(); runCurrent(); change()
        source.publish.value = false; runCurrent()
        vm.saveExerciseEdit(1); runCurrent()
        source.calls[0].release.complete(Unit); runCurrent()
        assertTrue(edit().isSaving)
        repeat(3) { vm.saveExerciseEdit(1) }; runCurrent()
        assertEquals(1, source.calls.size)
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        assertTrue(edit().isSaving)
        source.publish.value = true; runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
    }

    @Test fun `matching model before repository return is reconciled after success`() = runTest {
        prepare(); runCurrent(); change()
        vm.saveExerciseEdit(1); runCurrent()
        val returned = CompletableDeferred<Unit>()
        source.calls[0].afterWrite = returned
        source.calls[0].release.complete(Unit); runCurrent()
        assertEquals("Incline", vm.uiState.value.exercises.first().exercise.name)
        assertTrue(edit().isSaving)
        returned.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
    }

    @Test fun `closing during save hides the form without cancelling and can reopen pending state`() = runTest {
        prepare(); runCurrent(); change()
        vm.saveExerciseEdit(1); runCurrent()
        vm.dismissExerciseEdit(1)
        assertFalse(edit().isOpen)
        assertTrue(edit().isSaving)
        vm.beginExerciseEdit(1)
        assertTrue(edit().isOpen)
        assertEquals("Incline", edit().name)
        vm.saveExerciseEdit(1); runCurrent()
        assertEquals(1, source.calls.size)
        source.calls[0].release.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
    }

    @Test fun `offscreen failure retains a closed draft for reopening in the correct routine`() = runTest {
        prepare(); runCurrent(); change()
        vm.saveExerciseEdit(1); runCurrent()
        vm.dismissExerciseEdit(1)
        vm.selectWorkout(repository.allWorkouts.first().last()); runCurrent()
        source.calls[0].failure = IllegalStateException("injected")
        source.calls[0].release.complete(Unit); runCurrent()
        assertEquals(2, vm.uiState.value.selectedWorkout?.id)
        assertEquals("Squat", vm.uiState.value.exercises.single().exercise.name)
        assertTrue(edit().saveFailed)
        assertFalse(edit().isOpen)
        vm.saveExerciseEdit(1); runCurrent()
        assertEquals(1, source.calls.size)
        vm.selectWorkout(repository.allWorkouts.first().first()); runCurrent()
        vm.beginExerciseEdit(1)
        assertTrue(edit().isOpen)
        assertEquals("Incline", edit().name)
        assertTrue(edit().saveFailed)
    }

    @Test fun `offscreen success releases acknowledgement and reopening uses stored metadata`() = runTest {
        prepare(); runCurrent(); change()
        vm.saveExerciseEdit(1); runCurrent()
        vm.dismissExerciseEdit(1)
        vm.selectWorkout(repository.allWorkouts.first().last()); runCurrent()
        source.calls[0].release.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        assertEquals("Squat", vm.uiState.value.exercises.single().exercise.name)
        vm.selectWorkout(repository.allWorkouts.first().first()); runCurrent()
        vm.beginExerciseEdit(1)
        assertEquals("Incline", edit().name)
        assertFalse(edit().isSaving)
    }

    @Test fun `different exercises save independently and only one form is open`() = runTest {
        prepare(); runCurrent(); change(1)
        vm.saveExerciseEdit(1); runCurrent()
        change(2); vm.saveExerciseEdit(2); runCurrent()
        assertFalse(edit(1).isOpen)
        assertTrue(edit(2).isOpen)
        assertEquals(2, source.calls.size)
        source.calls[0].release.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.exerciseEdits.containsKey(1))
        assertTrue(edit(2).isSaving)
        source.calls[1].release.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
    }

    @Test fun `set additions and reordering keep their fields while metadata is pending`() = runTest {
        prepare(); runCurrent(); change()
        vm.saveExerciseEdit(1); runCurrent()
        vm.addSetToExercise(1)
        vm.moveExercise(0, 1)
        vm.deleteExercise(1)
        runCurrent()
        assertEquals(2, vm.uiState.value.exercises.size)
        source.calls[0].release.complete(Unit); runCurrent()
        val row = vm.uiState.value.exercises.last()
        assertEquals(1, row.exercise.id)
        assertEquals(1, row.exercise.orderIndex)
        assertEquals(2, row.sets.size)
        assertEquals("Incline", row.exercise.name)
    }

    @Test fun `deleted rows discard unsaved forms rather than leave an editor that cannot save`() = runTest {
        prepare(); runCurrent(); change()
        repository.deleteExercise(repository.allExercises.first().first()); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        vm.saveExerciseEdit(1); runCurrent()
        assertTrue(source.calls.isEmpty())
    }

    @Test fun `cancellation restores editable input without failure and permits retry`() = runTest {
        prepare(); runCurrent(); change()
        vm.saveExerciseEdit(1); runCurrent()
        source.calls[0].failure = CancellationException("cancelled")
        source.calls[0].release.complete(Unit); runCurrent()
        assertFalse(edit().isSaving)
        assertFalse(edit().saveFailed)
        assertEquals("Incline", edit().name)
        vm.saveExerciseEdit(1); runCurrent()
        source.calls[1].release.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
    }

    @Test fun `finalization releases saved acknowledgement without cancelling an in-flight metadata save`() = runTest {
        prepare(); runCurrent(); change()
        vm.saveExerciseEdit(1); runCurrent()
        val old = vm.uiState.value.activeSession!!.id
        vm.logSet(1, 1, 80f, 10, true); runCurrent()
        vm.finalizeWorkout(old); runCurrent()
        assertNotEquals(old, vm.uiState.value.activeSession?.id)
        assertTrue(edit().isSaving)
        source.calls[0].release.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        assertEquals("Incline", vm.uiState.value.exercises.first().exercise.name)
    }

    @Test fun `unchanged metadata closes successfully even when no new model emission is produced`() = runTest {
        prepare(); runCurrent()
        vm.beginExerciseEdit(1)
        vm.saveExerciseEdit(1); runCurrent()
        source.calls[0].release.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        assertEquals("Bench", vm.uiState.value.exercises.first().exercise.name)
    }

    @Test fun `selection discards saved acknowledgement when its snapshot has not arrived yet`() = runTest {
        prepare(); runCurrent(); change()
        source.publish.value = false; runCurrent()
        vm.saveExerciseEdit(1); runCurrent()
        source.calls[0].release.complete(Unit); runCurrent()
        assertTrue(edit().isSaving)
        vm.selectWorkout(repository.allWorkouts.first().last()); runCurrent()
        assertTrue(vm.uiState.value.exerciseEdits.isEmpty())
        source.publish.value = true; runCurrent()
        vm.selectWorkout(repository.allWorkouts.first().first()); runCurrent()
        vm.beginExerciseEdit(1)
        assertEquals("Incline", edit().name)
        assertFalse(edit().isSaving)
    }
}
