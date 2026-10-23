package com.example.baselift.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
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
import com.example.baselift.R
import com.example.baselift.View.dashboard.DashboardScreen
import com.example.baselift.View.workout.WorkoutScreen
import com.example.baselift.ViewModel.dashboard.DashboardUiState
import com.example.baselift.ViewModel.dashboard.ExerciseCharts
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
        lateinit var warmObserver: kotlinx.coroutines.Job
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            warmObserver = vm.viewModelScope.launch { vm.uiState.collect {} }
        }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.isNotEmpty() }
        val visible = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { if (visible.value) WorkoutScreen(vm) } }
        compose.runOnIdle { warmObserver.cancel() }
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
    fun workoutShowsReadOnlyHeadersWhileHistoryLoadsAndPublishesRowsWhenItArrives() {
        val historyReady = CompletableDeferred<Unit>()
        val vm = workoutViewModel(historyReady)
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { vm.uiState.value.activeSession != null }
        compose.waitUntil(5_000) { vm.uiState.value.loadingExerciseHeaders.isNotEmpty() }
        val enteredAt = compose.mainClock.currentTime
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Bench").assertIsDisplayed()
        compose.onNodeWithText("Set 1 completion").assertDoesNotExist()
        assertFalse(vm.uiState.value.areRowsValidated)
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
        val state = DashboardUiState(workouts = listOf(WorkoutEntity(id = 1, name = "Push")),
            routinesLoaded = true, isLoading = false)
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

    @Test
    fun selectionFailureCanRetryWithRetainedInputsAndRepeatedRoutineTapsDoNotReload() {
        val fail = AtomicBoolean(true)
        val attempts = AtomicInteger()
        val vm = workoutViewModel(historyAction = {
            attempts.incrementAndGet()
            if (fail.getAndSet(false)) error("injected history failure")
        })
        compose.setContent { MaterialTheme { WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { vm.uiState.value.selectionLoadFailed }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.workout_selection_error)).assertIsDisplayed()
        compose.onNodeWithText("Bench").assertDoesNotExist()
        compose.runOnIdle {
            vm.updateDraftWeight(1, 1, "80")
            vm.updateDraftReps(1, 1, "10")
        }
        compose.onNodeWithText(context.getString(R.string.workout_selection_retry)).performClick()
        compose.waitUntil(5_000) { vm.uiState.value.exercises.isNotEmpty() }
        compose.onNodeWithText("Bench").assertIsDisplayed()
        compose.onNodeWithText("80").assertIsDisplayed()
        compose.onNodeWithText("10").assertIsDisplayed()
        val session = vm.uiState.value.activeSession
        repeat(4) { compose.onNodeWithText("PUSH").performTouchInput { click() } }
        compose.runOnIdle {
            assertEquals(2, attempts.get())
            assertEquals(session, vm.uiState.value.activeSession)
            assertEquals("80", vm.getDraftWeight(1, 1))
            assertEquals("10", vm.getDraftReps(1, 1))
        }
        compose.onNodeWithText(context.getString(R.string.workout_selection_error)).assertDoesNotExist()
    }

    private fun workoutViewModel(
        historyReady: CompletableDeferred<Unit>? = null,
        historyAction: () -> Unit = {}
    ): WorkoutViewModel {
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
                historyAction()
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
