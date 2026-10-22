package com.example.baselift.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.Model.repository.WorkoutRepository
import com.example.baselift.R
import com.example.baselift.View.workout.components.ExerciseCard
import com.example.baselift.View.workout.components.SetRow
import com.example.baselift.ViewModel.workout.ExerciseUiModel
import com.example.baselift.ViewModel.workout.SetUiModel
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetWriteInteractionTest {
    @get:Rule
    val compose = createComposeRule()
    private var database: AppDatabase? = null
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database?.close()
    }

    private val row = SetUiModel(1, null, 70f, 8, canBeChecked = true)
    private val editable = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    private fun samePixels(first: ImageBitmap, second: ImageBitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val before = first.toPixelMap()
        val after = second.toPixelMap()
        for (y in 0 until first.height) for (x in 0 until first.width) {
            if (before[x, y] != after[x, y]) return false
        }
        return true
    }

    @Test
    fun removingBlocksChecksAndAddWhileShowingTheTargetRow() {
        val model = ExerciseUiModel(ExerciseEntity(id = 1, workoutId = 1, name = "Bench", setCount = 2),
            listOf(row, row.copy(setNumber = 2, canBeRemoved = true)))
        var adds = 0
        var logs = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                ExerciseCard(model, emptyMap(), emptyMap(), { _, _, _ -> }, { _, _, _ -> }, {},
                    onAddSet = { adds++ }, onLogSet = { _, _, _, _, _ -> logs++ },
                    onDeleteExercise = {}, onEditExercise = {}, removingSetNumbers = setOf(2))
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Set 1 completion").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Set 2 completion").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Removing set…"))
        compose.onNodeWithText("Removing set…").assertExists()
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.workout_add_set)
        compose.onNodeWithText(label).assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, adds); assertEquals(0, logs) }
    }

    @Test
    fun confirmationRetainsTheRequestedSetNumberWhenAnotherSetArrives() {
        val exercise = ExerciseEntity(id = 1, workoutId = 1, name = "Bench", setCount = 2)
        val second = row.copy(setNumber = 2, canBeRemoved = true)
        val model = mutableStateOf(ExerciseUiModel(exercise, listOf(row, second)))
        val adding = mutableStateOf(false)
        val requested = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                ExerciseCard(model.value, emptyMap(), emptyMap(), { _, _, _ -> }, { _, _, _ -> },
                    onRemoveLastSet = { requested += it }, onAddSet = {}, onLogSet = { _, _, _, _, _ -> },
                    onDeleteExercise = {}, onEditExercise = {}, isAddingSets = adding.value)
            }
        }
        compose.onNodeWithText("2").performTouchInput { longClick() }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.workout_delete)
        compose.onNodeWithText(label).assertIsEnabled()
        compose.runOnIdle { adding.value = true }
        compose.onNodeWithText(label).assertIsNotEnabled()
        compose.runOnIdle {
            adding.value = false
            model.value = ExerciseUiModel(exercise.copy(setCount = 3), listOf(row,
                second.copy(canBeRemoved = false), row.copy(setNumber = 3, canBeRemoved = true)))
        }
        compose.onNodeWithText(label).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(2), requested) }
    }

    @Test
    fun completedValuesKeepRowHeightWithoutKeepingTextEditors() {
        val completed = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.testTag("set-row")) {
                    SetRow(1, row.copy(isCompleted = completed.value), "80", "10",
                        { _, _, _ -> }, { _, _, _ -> }, onLogSet = { _, _, _, _, _ -> })
                }
            }
        }
        val height = compose.onNodeWithTag("set-row").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertEquals(2, compose.onAllNodes(editable).fetchSemanticsNodes().size)
        compose.runOnIdle { completed.value = true }
        assertEquals(0, compose.onAllNodes(editable).fetchSemanticsNodes().size)
        compose.onNodeWithText("80").assertExists()
        compose.onNodeWithText("10").assertExists()
        assertEquals(height, compose.onNodeWithTag("set-row").getUnclippedBoundsInRoot().let { it.bottom - it.top })
        compose.runOnIdle { completed.value = false }
        assertEquals(2, compose.onAllNodes(editable).fetchSemanticsNodes().size)
        assertEquals(height, compose.onNodeWithTag("set-row").getUnclippedBoundsInRoot().let { it.bottom - it.top })
    }

    @Test
    fun instantaneousSaveDoesNotDelayConfirmedCheck() {
        val completed = mutableStateOf(false)
        var writes = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                SetRow(1, row.copy(isCompleted = completed.value), "", "",
                    { _, _, _ -> }, { _, _, _ -> },
                    onLogSet = { _, _, _, _, _ ->
                        writes++
                        completed.value = true
                    })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val checkbox = compose.onNodeWithContentDescription("Set 1 completion")
        checkbox.performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("set-pending-spinner", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, writes) }
        checkbox.assertIsEnabled()
    }

    @Test
    fun savingDisablesCheckboxAndInputsThenAllowsRetryWithPreservedValues() {
        val saving = mutableStateOf(false)
        val inputs = "80"
        val reps = "10"
        val calls = mutableListOf<Pair<Float, Int>>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                SetRow(1, row, inputs, reps, { _, _, _ -> }, { _, _, _ -> },
                    onLogSet = { _, weight, count, _, _ ->
                        calls += weight to count
                        saving.value = true
                    }, isSaving = saving.value)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val checkbox = compose.onNodeWithContentDescription("Set 1 completion")
        checkbox.performClick()
        compose.mainClock.advanceTimeByFrame()
        checkbox.assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Saving set…"))
        val spinner = compose.onNodeWithTag("set-pending-spinner", useUnmergedTree = true)
        val firstFrame = spinner.captureToImage()
        compose.onNodeWithText("Saving set…").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(64)
        assertFalse("The pending indicator must visibly rotate within a short write",
            samePixels(firstFrame, spinner.captureToImage()))
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithText("Saving set…").assertExists()
        compose.onNodeWithText("70kg x 8").assertExists()
        compose.onAllNodes(editable).apply {
            assertEquals(2, fetchSemanticsNodes().size)
            this[0].assertIsNotEnabled()
            this[1].assertIsNotEnabled()
        }
        repeat(3) { checkbox.performTouchInput { click() } }
        compose.runOnIdle {
            assertEquals(listOf(80f to 10), calls)
            // A failed save leaves the same model and drafts visible.
            saving.value = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Saving set…").assertDoesNotExist()
        checkbox.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(80f to 10, 80f to 10), calls) }
    }

    @Test
    fun cardBlocksNextAvailableSetWhilePriorWriteAwaitsAcknowledgement() {
        val pending = mutableStateOf(setOf(1))
        val exercise = ExerciseEntity(id = 1, workoutId = 1, name = "Bench", setCount = 2)
        val model = ExerciseUiModel(exercise, listOf(row,
            SetUiModel(2, null, 60f, 10, canBeChecked = true, canBeRemoved = true)))
        var calls = 0
        val draftUpdates = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                Column {
                    ExerciseCard(model, emptyMap(), emptyMap(), { _, _, value -> draftUpdates += value }, { _, _, _ -> }, {}, {},
                        onLogSet = { _, _, _, _, _ -> calls++ }, onDeleteExercise = {},
                        onEditExercise = {}, savingSetNumbers = pending.value)
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Set 1 completion").assertIsNotEnabled()
        val next = compose.onNodeWithContentDescription("Set 2 completion")
        next.assertIsNotEnabled().performTouchInput { click() }
        compose.onAllNodes(editable)[0].assertIsNotEnabled()
        compose.onAllNodes(editable)[2].assertIsEnabled().performTextReplacement("82.5")
        compose.runOnIdle { assertEquals(listOf("82.5"), draftUpdates) }
        compose.runOnIdle { assertEquals(0, calls); pending.value = emptySet() }
        compose.mainClock.advanceTimeByFrame()
        next.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun finalizationBlocksOtherwiseAvailableSetControls() {
        var calls = 0
        compose.setContent {
            MaterialTheme {
                SetRow(1, row, "", "", { _, _, _ -> }, { _, _, _ -> },
                    onLogSet = { _, _, _, _, _ -> calls++ }, writesBlocked = true)
            }
        }
        compose.onNodeWithContentDescription("Set 1 completion").assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onAllNodes(editable)[0].assertIsNotEnabled()
        compose.onAllNodes(editable)[1].assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun rapidTapsPersistOneRoomRowAndUncheckRecheckUsesCurrentIdentity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        database = db
        val dao = db.workoutDao()
        val workoutId = runBlocking { dao.insertWorkout(WorkoutEntity(name = "Test")).toInt() }
        val exerciseId = runBlocking { dao.insertExercise(ExerciseEntity(workoutId = workoutId, name = "Bench")).toInt() }
        val sessionId = runBlocking { dao.insertSession(WorkoutSessionEntity(workoutId = workoutId, timestamp = 1_000L)).toInt() }
        val real = WorkoutRepository(dao, context)
        val release = CompletableDeferred<Unit>()
        var writes = 0
        // Keep reminder scheduling outside this set-write integration test.
        val source = object : IWorkoutRepository by real {
            override suspend fun startOrGetSession(workoutId: Int) = dao.getActiveSession(workoutId)!!
            override suspend fun logSet(sessionId: Int, exerciseId: Int, setNumber: Int,
                weight: Float, reps: Int, isCompleted: Boolean, existingSetId: Int) {
                writes++
                release.await()
                real.logSet(sessionId, exerciseId, setNumber, weight, reps, isCompleted, existingSetId)
            }
        }
        lateinit var vm: WorkoutViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = WorkoutViewModel(source)
            store.put("test", vm)
            vm.updateDraftWeight(exerciseId, 1, "80")
            vm.updateDraftReps(exerciseId, 1, "10")
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val state by vm.uiState.collectAsState()
            MaterialTheme {
                state.exercises.firstOrNull()?.sets?.firstOrNull()?.let { set ->
                    SetRow(exerciseId, set, vm.getDraftWeight(exerciseId, set.setNumber), vm.getDraftReps(exerciseId, set.setNumber),
                        vm::updateDraftWeight, vm::updateDraftReps,
                        onLogSet = { number, weight, reps, completed, _ ->
                            vm.logSet(exerciseId, number, weight, reps, completed, sessionId)
                        }, isSaving = state.pendingSetWrites.isNotEmpty())
                }
            }
        }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.isNotEmpty() }
        compose.mainClock.advanceTimeBy(100)
        val checkbox = compose.onNodeWithContentDescription("Set 1 completion")
        checkbox.performClick()
        compose.mainClock.advanceTimeBy(100)
        repeat(3) { checkbox.performTouchInput { click() } }
        compose.runOnIdle {
            // Also simulate stale callbacks bypassing the disabled UI.
            repeat(3) { vm.logSet(exerciseId, 1, 80f, 10, true, sessionId) }
            assertEquals(1, writes)
        }
        assertTrue(runBlocking { dao.getSetsForSession(sessionId).first().isEmpty() })
        release.complete(Unit)
        compose.waitUntil(5_000) {
            vm.uiState.value.pendingSetWrites.isEmpty() &&
                vm.uiState.value.exercises.first().sets.first().isCompleted
        }
        val firstId = runBlocking { dao.getSetsForSession(sessionId).first().single().id }
        assertTrue(firstId > 0)
        compose.mainClock.advanceTimeByFrame()
        checkbox.assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            vm.uiState.value.pendingSetWrites.isEmpty() &&
                !vm.uiState.value.exercises.first().sets.first().isCompleted
        }
        assertTrue(runBlocking { dao.getSetsForSession(sessionId).first().isEmpty() })
        compose.mainClock.advanceTimeByFrame()
        checkbox.assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            vm.uiState.value.pendingSetWrites.isEmpty() &&
                vm.uiState.value.exercises.first().sets.first().isCompleted
        }
        val finalRow = runBlocking { dao.getSetsForSession(sessionId).first().single() }
        assertEquals(80f, finalRow.weight, 0f)
        assertEquals(10, finalRow.reps)
        compose.runOnIdle { assertEquals(3, writes) }
    }
}
