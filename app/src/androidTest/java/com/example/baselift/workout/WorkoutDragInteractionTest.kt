package com.example.baselift.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
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
import com.example.baselift.View.workout.WorkoutScreen
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutDragInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database?.close()
    }

    @Test
    fun dragPersistsOrderAndSwitchingRoutinesKeepsEachListsIdentity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        database = db
        val dao = db.workoutDao()
        runBlocking {
            dao.insertWorkout(WorkoutEntity(id = 1, name = "Push"))
            dao.insertWorkout(WorkoutEntity(id = 2, name = "Legs"))
            dao.insertSession(WorkoutSessionEntity(id = 1, workoutId = 1, timestamp = 1_000L))
            listOf("Bench", "Row", "Curl").forEachIndexed { index, name ->
                dao.insertExercise(ExerciseEntity(id = index + 1, workoutId = 1, name = name, orderIndex = index))
            }
            dao.insertExercise(ExerciseEntity(id = 4, workoutId = 2, name = "Squat"))
        }
        val real = WorkoutRepository(dao, context)
        val source = object : IWorkoutRepository by real {
            override suspend fun startOrGetSession(workoutId: Int) = dao.getOrCreateActiveSession(workoutId)
        }
        lateinit var vm: WorkoutViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = WorkoutViewModel(source)
            store.put("workout", vm)
        }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.size == 3 }
        compose.setContent { MaterialTheme { WorkoutScreen(vm) } }
        compose.onNodeWithText("Bench", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 500f), delayMillis = 400)
            up()
        }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.first().exercise.id != 1 }
        val reordered = vm.uiState.value.exercises.map { it.exercise.id }
        compose.waitUntil(5_000) {
            runBlocking { dao.getExercisesForWorkout(1).first().map { it.id } } == reordered
        }
        compose.onNodeWithText("LEGS").performTouchInput { click() }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.singleOrNull()?.exercise?.id == 4 }
        compose.onNodeWithText("Squat").assertIsDisplayed()
        assertEquals(listOf(4), runBlocking { dao.getExercisesForWorkout(2).first().map { it.id } })
        compose.onNodeWithText("PUSH").performTouchInput { click() }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.map { it.exercise.id } == reordered }
        compose.onNodeWithText("Bench").assertExists()
        assertEquals(reordered, runBlocking { dao.getExercisesForWorkout(1).first().map { it.id } })
    }
}
