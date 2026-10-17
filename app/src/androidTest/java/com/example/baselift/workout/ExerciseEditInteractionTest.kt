package com.example.baselift.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.example.baselift.View.workout.WorkoutScreen
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ExerciseEditInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var database: AppDatabase? = null
    private val requests = CopyOnWriteArrayList<Request>()
    private class Request {
        val release = CompletableDeferred<Unit>()
        var failure: Exception? = null
    }
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database?.close()
    }

    private fun prepare(): WorkoutViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        database = db
        val dao = db.workoutDao()
        runBlocking {
            dao.insertWorkout(WorkoutEntity(id = 1, name = "Push"))
            dao.insertWorkout(WorkoutEntity(id = 2, name = "Legs"))
            dao.insertExercise(ExerciseEntity(id = 1, workoutId = 1, name = "Bench",
                equipment = "Barbell", muscleGroups = "Chest", setCount = 2))
            dao.insertExercise(ExerciseEntity(id = 2, workoutId = 2, name = "Squat"))
            dao.insertSession(WorkoutSessionEntity(id = 1, workoutId = 1, timestamp = 1_000L))
        }
        val real = WorkoutRepository(dao, context)
        runBlocking { real.logSet(1, 1, 1, 80f, 10, true, 0) }
        val source = object : IWorkoutRepository by real {
            override suspend fun startOrGetSession(workoutId: Int) = dao.getOrCreateActiveSession(workoutId)
            override suspend fun updateExercise(exercise: ExerciseEntity, name: String, equipment: String, muscleGroups: String) {
                val request = Request()
                requests += request
                request.release.await()
                request.failure?.let { throw it }
                real.updateExercise(exercise, name, equipment, muscleGroups)
            }
        }
        lateinit var vm: WorkoutViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = WorkoutViewModel(source)
            store.put("workout", vm)
            vm.updateDraftWeight(1, 2, "75")
        }
        compose.setContent { MaterialTheme { WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.isNotEmpty() }
        compose.onNodeWithContentDescription("Options").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        return vm
    }

    private fun openEditor() {
        compose.onNodeWithContentDescription("Options").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(text(R.string.workout_edit_exercise)).performClick()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun field(value: String) = compose.onNode(hasSetTextAction() and hasText(value))

    @Test fun failedSaveKeepsFieldsBlocksDuplicatesAndRetriesWithoutChangingSets() {
        val vm = prepare()
        openEditor()
        field("Bench").performTextReplacement(" ")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(text(R.string.workout_save)).assertIsNotEnabled()
        field(" ").performTextReplacement("Incline")
        field("Barbell").performTextReplacement("Machine")
        field("Chest").performTextReplacement("Upper chest")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(text(R.string.workout_save)).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(5_000) { requests.size == 1 }
        compose.onNodeWithText(text(R.string.workout_edit_saving)).assertIsNotEnabled()
        compose.onNodeWithText("Incline").assertIsNotEnabled()
        repeat(3) { compose.onNodeWithText(text(R.string.workout_edit_saving)).performTouchInput { click() } }
        assertEquals(1, requests.size)
        requests[0].failure = IllegalStateException("injected")
        requests[0].release.complete(Unit)
        compose.waitUntil(5_000) { vm.uiState.value.exerciseEdits[1]?.saveFailed == true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(text(R.string.workout_edit_error)).assertIsDisplayed()
        field("Incline").assertIsEnabled()
        field("Machine").assertIsEnabled()
        field("Upper chest").assertIsEnabled()
        compose.onNodeWithText(text(R.string.workout_save)).performClick()
        compose.waitUntil(5_000) { requests.size == 2 }
        requests[1].release.complete(Unit)
        compose.waitUntil(5_000) { vm.uiState.value.exerciseEdits.isEmpty() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(text(R.string.workout_edit_ex_title)).assertDoesNotExist()
        compose.onNodeWithText("Incline").assertIsDisplayed()
        val dao = database!!.workoutDao()
        val row = runBlocking { dao.getExercisesForWorkout(1).first().single() }
        assertEquals("Incline", row.name)
        assertEquals("Machine", row.equipment)
        assertEquals("Upper chest", row.muscleGroups)
        assertEquals(2, row.setCount)
        assertEquals(1, runBlocking { dao.getSetsForSession(1).first().size })
        assertEquals("75", vm.getDraftWeight(1, 2))
    }

    @Test fun closeDuringSaveAllowsRoutineNavigationAndTheAcceptedEditStillPersists() {
        val vm = prepare()
        openEditor()
        field("Bench").performTextReplacement("Incline")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(text(R.string.workout_save)).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(5_000) { requests.size == 1 }
        compose.onNodeWithText(text(R.string.workout_edit_close)).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(text(R.string.workout_edit_ex_title)).assertDoesNotExist()
        compose.onNodeWithText("LEGS").performTouchInput { click() }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.singleOrNull()?.exercise?.id == 2 }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Squat").assertIsDisplayed()
        requests[0].release.complete(Unit)
        compose.waitUntil(5_000) { vm.uiState.value.exerciseEdits.isEmpty() }
        compose.onNodeWithText("PUSH").performTouchInput { click() }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.singleOrNull()?.exercise?.name == "Incline" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Incline").assertIsDisplayed()
        assertEquals("75", vm.getDraftWeight(1, 2))
        assertEquals(1, requests.size)
    }
}
