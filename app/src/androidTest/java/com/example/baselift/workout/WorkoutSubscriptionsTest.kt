package com.example.baselift.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class WorkoutSubscriptionsTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var database: AppDatabase? = null

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database?.close()
    }

    @Test fun hiddenWorkoutStopsRowQueriesAndAcceptedSaveReturnsWithFreshModelsAndDrafts() {
        val exerciseQueries = AtomicInteger()
        val setQueries = AtomicInteger()
        val routineQueries = AtomicInteger()
        val activeRoutines = AtomicInteger()
        val recoveryReads = AtomicInteger()
        val activeExercises = AtomicInteger()
        val activeSets = AtomicInteger()
        val sessionReads = AtomicInteger()
        val historyReads = AtomicInteger()
        val writeStarted = CompletableDeferred<Unit>()
        val writeRelease = CompletableDeferred<Unit>()
        val writeFinished = CompletableDeferred<Unit>()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.startsWith("SELECT * FROM exercises WHERE workoutId", ignoreCase = true)) exerciseQueries.incrementAndGet()
                if (sql.startsWith("SELECT * FROM set_logs WHERE sessionId", ignoreCase = true)) setQueries.incrementAndGet()
                if (sql.startsWith("SELECT * FROM workouts ORDER BY", ignoreCase = true)) routineQueries.incrementAndGet()
            }, Executor { it.run() }).build()
        database = db
        val dao = db.workoutDao()
        runBlocking {
            dao.insertWorkout(WorkoutEntity(id = 1, name = "Push"))
            dao.insertExercise(ExerciseEntity(id = 1, workoutId = 1, name = "Bench", setCount = 2))
            dao.insertSession(WorkoutSessionEntity(id = 1, workoutId = 1, timestamp = 1_000L))
        }
        val real = WorkoutRepository(dao, context)
        fun <T> tracked(rows: Flow<T>, active: AtomicInteger): Flow<T> = flow {
            active.incrementAndGet()
            try { emitAll(rows) } finally { active.decrementAndGet() }
        }
        val source = object : IWorkoutRepository by real {
            override val allWorkouts = tracked(real.allWorkouts, activeRoutines)
            override suspend fun getAnyActiveSession() = real.getAnyActiveSession().also { recoveryReads.incrementAndGet() }
            override suspend fun startOrGetSession(workoutId: Int) = dao.getOrCreateActiveSession(workoutId).also { sessionReads.incrementAndGet() }
            override suspend fun getPreviousSetsForWorkout(workoutId: Int) = real.getPreviousSetsForWorkout(workoutId).also { historyReads.incrementAndGet() }
            override fun getExercisesForWorkout(workoutId: Int) = tracked(real.getExercisesForWorkout(workoutId), activeExercises)
            override fun getSetsForSession(sessionId: Int) = tracked(real.getSetsForSession(sessionId), activeSets)
            override suspend fun logSet(sessionId: Int, exerciseId: Int, setNumber: Int, weight: Float,
                reps: Int, isCompleted: Boolean, existingSetId: Int) {
                writeStarted.complete(Unit); writeRelease.await()
                real.logSet(sessionId, exerciseId, setNumber, weight, reps, isCompleted, existingSetId)
                writeFinished.complete(Unit)
            }
        }
        lateinit var vm: WorkoutViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = WorkoutViewModel(source)
            store.put("workout", vm)
            vm.updateDraftWeight(1, 2, "75")
            vm.updateDraftReps(1, 2, "8")
        }
        val visible = mutableStateOf(true)
        assertEquals(0, recoveryReads.get()); assertEquals(0, sessionReads.get())
        assertEquals(0, historyReads.get()); assertEquals(0, routineQueries.get())
        compose.setContent { MaterialTheme { if (visible.value) WorkoutScreen(vm) } }
        compose.waitUntil(5_000) { vm.uiState.value.exercises.isNotEmpty() }
        compose.onNodeWithText("Bench").assertIsDisplayed()
        compose.runOnIdle { vm.logSet(1, 1, 80f, 10, true) }
        compose.waitUntil(5_000) { writeStarted.isCompleted }
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(5_000) { activeExercises.get() == 0 && activeSets.get() == 0 && activeRoutines.get() == 0 }
        val exercisesBefore = exerciseQueries.get()
        val setsBefore = setQueries.get()
        val routinesBefore = routineQueries.get()
        assertTrue(exercisesBefore > 0); assertTrue(setsBefore > 0)
        writeRelease.complete(Unit)
        compose.waitUntil(5_000) { writeFinished.isCompleted && vm.uiState.value.pendingSetWrites.isEmpty() }
        runBlocking {
            dao.updateExerciseMetadata(1, "Incline", "Machine", "Upper chest")
            dao.incrementSetCount(1)
            dao.updateWorkout(WorkoutEntity(id = 1, name = "Upper", defaultRestTimer = 60))
        }
        assertEquals(exercisesBefore, exerciseQueries.get()); assertEquals(setsBefore, setQueries.get())
        assertEquals(routinesBefore, routineQueries.get())
        assertEquals("Bench", vm.uiState.value.exercises.single().exercise.name)
        assertTrue(vm.uiState.value.isSelectionLoading)
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(5_000) {
            val row = vm.uiState.value.exercises.singleOrNull()
            !vm.uiState.value.isSelectionLoading && row?.exercise?.name == "Incline" &&
                row.sets.size == 3 && row.sets.first().isCompleted
        }
        compose.onNodeWithText("Incline").assertIsDisplayed()
        compose.onNodeWithText("UPPER").assertIsDisplayed()
        assertEquals(60, vm.uiState.value.selectedWorkout?.defaultRestTimer)
        assertEquals("75", vm.getDraftWeight(1, 2)); assertEquals("8", vm.getDraftReps(1, 2))
        assertTrue(vm.uiState.value.pendingSetWrites.isEmpty())
        assertEquals(1, activeExercises.get()); assertEquals(1, activeSets.get())
        assertEquals(1, activeRoutines.get()); assertEquals(1, recoveryReads.get())
        assertTrue(exerciseQueries.get() > exercisesBefore); assertTrue(setQueries.get() > setsBefore)
        assertTrue(routineQueries.get() > routinesBefore)
        assertEquals(1, sessionReads.get()); assertEquals(1, historyReads.get())
        assertEquals(1, vm.uiState.value.activeSession?.id)
    }
}
