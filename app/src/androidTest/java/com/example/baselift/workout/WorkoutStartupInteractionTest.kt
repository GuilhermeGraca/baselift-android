package com.example.baselift.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
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
import com.example.baselift.R
import com.example.baselift.View.workout.WorkoutScreen
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class WorkoutStartupInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private val failRoutines = MutableStateFlow(false)
    private val publishRoutines = MutableStateFlow(true)
    private val publishExercises = MutableStateFlow(true)
    private val activeRoutines = AtomicInteger()
    private val activeExercises = AtomicInteger()
    private val recoveries = AtomicInteger()
    private val sessions = AtomicInteger()
    @Volatile private var holdHistory = false
    private val releaseHistory = CompletableDeferred<Unit>()

    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        if (::db.isInitialized) db.close()
    }

    private fun prepare(): WorkoutViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.workoutDao()
        runBlocking {
            dao.insertWorkout(WorkoutEntity(id = 1, name = "Push"))
            dao.insertWorkout(WorkoutEntity(id = 2, name = "Legs"))
            dao.insertExercise(ExerciseEntity(id = 1, workoutId = 1, name = "Bench"))
            dao.insertExercise(ExerciseEntity(id = 2, workoutId = 2, name = "Squat"))
            dao.insertSession(WorkoutSessionEntity(id = 1, workoutId = 1, timestamp = 1_000L))
        }
        val real = WorkoutRepository(dao, context)
        val source = object : IWorkoutRepository by real {
            override val allWorkouts = flow {
                activeRoutines.incrementAndGet()
                try {
                    emitAll(combine(real.allWorkouts, failRoutines, publishRoutines) { rows, fail, publish ->
                        if (fail) error("injected routine-list failure")
                        rows to publish
                    }.filter { it.second }.map { it.first })
                } finally { activeRoutines.decrementAndGet() }
            }
            override suspend fun getAnyActiveSession() = real.getAnyActiveSession().also { recoveries.incrementAndGet() }
            override suspend fun startOrGetSession(workoutId: Int) = dao.getOrCreateActiveSession(workoutId).also { sessions.incrementAndGet() }
            override suspend fun getPreviousSetsForWorkout(workoutId: Int): List<SetLogEntity> {
                if (holdHistory) releaseHistory.await()
                return real.getPreviousSetsForWorkout(workoutId)
            }
            override fun getExercisesForWorkout(workoutId: Int) = flow {
                activeExercises.incrementAndGet()
                try { emitAll(combine(real.getExercisesForWorkout(workoutId), publishExercises) { rows, publish ->
                    rows to publish
                }.filter { it.second }.map { it.first }) }
                finally { activeExercises.decrementAndGet() }
            }
        }
        lateinit var vm: WorkoutViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = WorkoutViewModel(source); store.put("workout", vm)
            vm.updateDraftWeight(1, 1, "80")
            vm.updateDraftReps(1, 1, "10")
        }
        return vm
    }

    @Test fun firstRoutineReadShowsCardSkeletonThenRemovesItAsSoonAsRowsArrive() {
        publishRoutines.value = false
        val vm = prepare()
        compose.setContent { MaterialTheme { WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { activeRoutines.get() == 1 }
        compose.onNodeWithTag("workout-tabs-skeleton").assertIsDisplayed()
        compose.onNodeWithTag("workout-loading-skeleton").assertIsDisplayed()
        compose.onNodeWithText("Bench").assertDoesNotExist()
        publishRoutines.value = true
        compose.waitUntil(5_000) { vm.uiState.value.areRowsValidated }
        compose.onNodeWithTag("workout-tabs-skeleton").assertDoesNotExist()
        compose.onNodeWithTag("workout-loading-skeleton").assertDoesNotExist()
        compose.onNodeWithText("Bench").assertIsDisplayed()
    }

    @Test fun changingRoutineShowsItsRealHeaderAndSkeletonSetsWithoutOldControls() {
        val vm = prepare()
        compose.setContent { MaterialTheme { WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { vm.uiState.value.areRowsValidated }
        holdHistory = true
        compose.runOnIdle { vm.selectWorkout(vm.uiState.value.workouts.single { it.id == 2 }) }
        compose.waitUntil(5_000) { vm.uiState.value.loadingExerciseHeaders.any { it.name == "Squat" } }
        compose.onNodeWithText("LEGS").assertIsDisplayed()
        compose.onNodeWithText("Squat").assertIsDisplayed()
        compose.onNodeWithTag("workout-loading-skeleton").assertIsDisplayed()
        compose.onAllNodesWithTag("workout-skeleton-set-row").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Bench").assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.workout_set_check, 1)).assertDoesNotExist()
        releaseHistory.complete(Unit)
        compose.waitUntil(5_000) { vm.uiState.value.selectedWorkout?.id == 2 && vm.uiState.value.areRowsValidated }
        compose.onNodeWithTag("workout-loading-skeleton").assertDoesNotExist()
        compose.onNodeWithText("Squat").assertIsDisplayed()
    }

    @Test fun routineListFailureShowsRetryAndRecoversSavedRoutineAndInputs() {
        failRoutines.value = true
        val vm = prepare()
        assertEquals(0, recoveries.get()); assertEquals(0, sessions.get())
        compose.setContent { MaterialTheme { WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { vm.uiState.value.workoutsLoadFailed }
        compose.onNodeWithText(text(R.string.workout_list_error)).assertIsDisplayed()
        compose.onNodeWithText("Bench").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add Workout").assertDoesNotExist()
        assertEquals(0, sessions.get()); assertEquals("80", vm.getDraftWeight(1, 1))
        failRoutines.value = false
        compose.onNodeWithText(text(R.string.workout_selection_retry)).performClick()
        compose.waitUntil(5_000) { !vm.uiState.value.isLoading && vm.uiState.value.exercises.isNotEmpty() }
        compose.onNodeWithText("Bench").assertIsDisplayed()
        compose.onNodeWithText("80").assertIsDisplayed()
        compose.onNodeWithText("10").assertIsDisplayed()
        assertEquals(1, recoveries.get()); assertEquals(1, sessions.get())
        assertEquals(1, vm.uiState.value.activeSession?.id)
    }

    @Test fun returnRetainsReadOnlyRowsAfterMembershipValidationUntilFreshRowsArrive() {
        val vm = prepare()
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (visible.value) WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.isNotEmpty() }
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(5_000) { activeRoutines.get() == 0 && activeExercises.get() == 0 }
        publishExercises.value = false
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(5_000) { !vm.uiState.value.isLoading && activeExercises.get() == 1 }
        compose.onNodeWithText("Bench").assertIsDisplayed()
        assertTrue(vm.uiState.value.isSelectionLoading)
        assertFalse(vm.uiState.value.areRowsValidated)
        compose.onNodeWithContentDescription(text(R.string.workout_set_check, 1))
            .assertIsNotEnabled()
        compose.runOnIdle { vm.logSet(1, 1, 80f, 10, true) }
        runBlocking { assertTrue(db.workoutDao().getSetsForSession(1).first().isEmpty()) }
        assertEquals(1, sessions.get())
        runBlocking { db.workoutDao().updateExerciseMetadata(1, "Renamed Bench", "", "") }
        publishExercises.value = true
        compose.waitUntil(5_000) { !vm.uiState.value.isSelectionLoading }
        assertTrue(vm.uiState.value.areRowsValidated)
        compose.onNodeWithText("Renamed Bench").assertIsDisplayed()
        compose.onNodeWithText("Bench").assertDoesNotExist()
        assertEquals(1, vm.uiState.value.activeSession?.id)
        assertEquals("80", vm.getDraftWeight(1, 1))
        assertEquals(1, sessions.get())
    }

    @Test fun returnKeepsAnEmptyRoutineSnapshotInsteadOfShowingTheStartupSpinner() {
        val vm = prepare()
        runBlocking { db.workoutDao().insertWorkout(WorkoutEntity(id = 3, name = "Empty")) }
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (visible.value) WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { vm.uiState.value.areRowsValidated }
        compose.runOnIdle { vm.selectWorkout(vm.uiState.value.workouts.single { it.id == 3 }) }
        compose.waitUntil(5_000) {
            vm.uiState.value.selectedWorkout?.id == 3 && vm.uiState.value.hasRowsSnapshot &&
                vm.uiState.value.areRowsValidated
        }
        assertTrue(vm.uiState.value.exercises.isEmpty())
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(5_000) { activeRoutines.get() == 0 && activeExercises.get() == 0 }
        publishRoutines.value = false
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(5_000) { activeRoutines.get() == 1 }
        compose.onNodeWithText("EMPTY").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.workout_refreshing)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.workout_add_exercise_session)).assertIsDisplayed()
        assertFalse(vm.uiState.value.areRowsValidated)
        publishRoutines.value = true
        compose.waitUntil(5_000) { vm.uiState.value.areRowsValidated }
        compose.onNodeWithText(text(R.string.workout_refreshing)).assertDoesNotExist()
        assertEquals(3, vm.uiState.value.activeSession?.workoutId)
    }

    @Test fun returnShowsReadOnlySnapshotUntilFreshRoutineMembershipReplacesDeletedRoutine() {
        val vm = prepare()
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (visible.value) WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.isNotEmpty() }
        val old = vm.uiState.value.selectedWorkout!!
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(5_000) { activeRoutines.get() == 0 && activeExercises.get() == 0 }
        runBlocking { db.workoutDao().deleteWorkout(old) }
        publishRoutines.value = false
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(5_000) { activeRoutines.get() == 1 }
        compose.onNodeWithText("Bench").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.workout_refreshing)).assertIsDisplayed()
        assertFalse(vm.uiState.value.areRowsValidated)
        compose.onNodeWithText("Squat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add Workout").assertDoesNotExist()
        compose.runOnIdle {
            vm.selectWorkout(old); vm.logSet(1, 1, 80f, 10, true); vm.createWorkout("Stale")
        }
        assertEquals(1, sessions.get()); assertEquals(0, activeExercises.get())
        publishRoutines.value = true
        compose.waitUntil(5_000) { vm.uiState.value.selectedWorkout?.id == 2 && !vm.uiState.value.isSelectionLoading }
        assertTrue(vm.uiState.value.areRowsValidated)
        compose.onNodeWithText("Squat").assertIsDisplayed()
        compose.onNodeWithText("LEGS").assertIsDisplayed()
        compose.onNodeWithText("Bench").assertDoesNotExist()
        assertEquals(2, sessions.get()); assertEquals(1, recoveries.get())
        assertEquals(listOf(2), vm.uiState.value.workouts.map { it.id })
    }
}
