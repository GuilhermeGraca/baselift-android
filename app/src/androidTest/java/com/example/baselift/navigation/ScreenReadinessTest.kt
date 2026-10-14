package com.example.baselift.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.Model.repository.WorkoutRepository
import com.example.baselift.View.dashboard.DashboardScreen
import com.example.baselift.View.workout.WorkoutScreen
import com.example.baselift.ViewModel.dashboard.DashboardUiState
import com.example.baselift.ViewModel.dashboard.ExerciseCharts
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenReadinessTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database?.close()
    }

    @Test
    fun readyWorkoutAppearsWithoutAClockAdvanceAndAfterReturning() {
        val vm = workoutViewModel()
        compose.waitUntil(5_000) { vm.uiState.value.exercises.isNotEmpty() }
        val visible = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { if (visible.value) WorkoutScreen(vm) } }
        val enteredAt = compose.mainClock.currentTime
        compose.onNodeWithText("Bench").assertIsDisplayed()
        assertEquals(enteredAt, compose.mainClock.currentTime)

        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Bench").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        val returnedAt = compose.mainClock.currentTime
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Bench").assertIsDisplayed()
        assertTrue(compose.mainClock.currentTime - returnedAt < 50)
    }

    @Test
    fun workoutWaitsForRealHistoryAndPublishesRowsWhenItArrives() {
        val historyReady = CompletableDeferred<Unit>()
        val vm = workoutViewModel(historyReady)
        compose.waitUntil(5_000) { vm.uiState.value.activeSession != null }
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { WorkoutScreen(vm) } }
        val enteredAt = compose.mainClock.currentTime
        compose.onNodeWithText("Bench").assertDoesNotExist()
        historyReady.complete(Unit)
        compose.waitUntil(5_000) { vm.uiState.value.exercises.isNotEmpty() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Bench").assertIsDisplayed()
        assertTrue(compose.mainClock.currentTime - enteredAt < 50)
    }

    @Test
    fun dashboardRoutinesAreImmediatelyAvailableToScrollOnEntryAndReturn() {
        val visible = mutableStateOf(true)
        val state = DashboardUiState(workouts = listOf(WorkoutEntity(id = 1, name = "Push")), isLoading = false)
        var chartRequests = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            if (visible.value) DashboardScreen(state, exerciseCharts = {
                chartRequests++
                flowOf(ExerciseCharts(isLoading = false))
            })
        } }
        val enteredAt = compose.mainClock.currentTime
        compose.onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollToIndex) { it(3) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("PUSH").assertIsDisplayed()
        assertTrue(compose.mainClock.currentTime - enteredAt < 50)
        assertEquals(0, chartRequests)

        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("PUSH").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        val returnedAt = compose.mainClock.currentTime
        compose.mainClock.advanceTimeByFrame()
        compose.onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollToIndex) { it(3) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("PUSH").assertIsDisplayed()
        assertTrue(compose.mainClock.currentTime - returnedAt < 50)
        assertEquals(0, chartRequests)
    }

    private fun workoutViewModel(historyReady: CompletableDeferred<Unit>? = null): WorkoutViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        database = db
        val dao = db.workoutDao()
        runBlocking {
            dao.insertWorkout(WorkoutEntity(id = 1, name = "Push"))
            dao.insertExercise(ExerciseEntity(id = 1, workoutId = 1, name = "Bench"))
            dao.insertSession(WorkoutSessionEntity(id = 1, workoutId = 1, timestamp = 1_000L))
        }
        val real = WorkoutRepository(dao, context)
        val source = object : IWorkoutRepository by real {
            // No notification work in this display test; session recovery still uses real SQLite.
            override suspend fun startOrGetSession(workoutId: Int) = dao.getActiveSession(workoutId)!!
            override suspend fun getPreviousSetsForWorkout(workoutId: Int): List<SetLogEntity> {
                historyReady?.await()
                return real.getPreviousSetsForWorkout(workoutId)
            }
        }
        lateinit var vm: WorkoutViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = WorkoutViewModel(source)
            store.put("workout", vm)
        }
        return vm
    }
}
