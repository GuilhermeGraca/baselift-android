package com.example.baselift.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.dao.WorkoutDao
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes instrumentados para o WorkoutDao.
 *
 * Verifica as queries mais complexas da app: workouts, exercícios,
 * sessões, set logs, PRs, e cascading deletes.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var workoutDao: WorkoutDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        workoutDao = database.workoutDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // =========================================================================
    // HELPERS — criar entidades com IDs reais
    // =========================================================================

    private suspend fun insertWorkout(name: String = "Push", order: Int = 0): Int {
        return workoutDao.insertWorkout(WorkoutEntity(name = name, orderIndex = order)).toInt()
    }

    private suspend fun insertExercise(workoutId: Int, name: String = "Bench Press", setCount: Int = 3): Int {
        return workoutDao.insertExercise(
            ExerciseEntity(workoutId = workoutId, name = name, setCount = setCount)
        ).toInt()
    }

    private suspend fun insertSession(workoutId: Int, isCompleted: Boolean = false, timestamp: Long = 1000L): Int {
        return workoutDao.insertSession(
            WorkoutSessionEntity(workoutId = workoutId, timestamp = timestamp, isCompleted = isCompleted)
        ).toInt()
    }

    private suspend fun insertCompletedSet(
        sessionId: Int, exerciseId: Int, setNumber: Int,
        weight: Float, reps: Int, prType: String = "NONE"
    ): Int {
        return workoutDao.insertSet(
            SetLogEntity(
                sessionId = sessionId, exerciseId = exerciseId, setNumber = setNumber,
                weight = weight, reps = reps, isCompleted = true, prType = prType
            )
        ).toInt()
    }

    // =========================================================================
    // TESTES DE WORKOUTS
    // =========================================================================

    @Test
    fun insertWorkout_getAllWorkouts_returnsByOrderIndex() = runTest {
        insertWorkout("Pull", order = 1)
        insertWorkout("Push", order = 0)
        insertWorkout("Legs", order = 2)

        val workouts = workoutDao.getAllWorkouts().first()

        assertEquals(3, workouts.size)
        assertEquals("Push", workouts[0].name)  // order 0
        assertEquals("Pull", workouts[1].name)  // order 1
        assertEquals("Legs", workouts[2].name)  // order 2
    }

    @Test
    fun deleteWorkout_cascadeDeletesExercises() = runTest {
        val workoutId = insertWorkout("Push")
        insertExercise(workoutId, "Bench Press")
        insertExercise(workoutId, "Shoulder Press")

        // confirmar que existem
        val beforeDelete = workoutDao.getExercisesForWorkout(workoutId).first()
        assertEquals(2, beforeDelete.size)

        // apagar o workout → CASCADE deve apagar os exercícios
        workoutDao.deleteWorkout(WorkoutEntity(id = workoutId, name = "Push"))
        val afterDelete = workoutDao.getAllExercises().first()
        assertTrue(afterDelete.isEmpty())
    }

    // =========================================================================
    // TESTES DE EXERCISES
    // =========================================================================

    @Test
    fun insertExercise_associatedToCorrectWorkout() = runTest {
        val pushId = insertWorkout("Push")
        val pullId = insertWorkout("Pull")

        insertExercise(pushId, "Bench Press")
        insertExercise(pullId, "Barbell Row")

        val pushExercises = workoutDao.getExercisesForWorkout(pushId).first()
        val pullExercises = workoutDao.getExercisesForWorkout(pullId).first()

        assertEquals(1, pushExercises.size)
        assertEquals("Bench Press", pushExercises[0].name)
        assertEquals(1, pullExercises.size)
        assertEquals("Barbell Row", pullExercises[0].name)
    }

    // =========================================================================
    // TESTES DE SESSIONS
    // =========================================================================

    @Test
    fun getActiveSession_returnsOnlyIncompleteSessions() = runTest {
        val workoutId = insertWorkout()

        // inserir uma sessão ativa (não completa)
        insertSession(workoutId, isCompleted = false)

        val active = workoutDao.getActiveSession(workoutId)
        assertNotNull(active)
        assertFalse(active!!.isCompleted)
    }

    @Test
    fun getActiveSession_returnsNullWhenAllCompleted() = runTest {
        val workoutId = insertWorkout()
        insertSession(workoutId, isCompleted = true)

        val active = workoutDao.getActiveSession(workoutId)
        assertNull(active)
    }

    @Test
    fun getAllCompletedSessions_orderedByTimestampDesc() = runTest {
        val workoutId = insertWorkout()
        insertSession(workoutId, isCompleted = true, timestamp = 3000L)
        insertSession(workoutId, isCompleted = true, timestamp = 1000L)
        insertSession(workoutId, isCompleted = true, timestamp = 2000L)
        insertSession(workoutId, isCompleted = false, timestamp = 4000L) // ativa, não deve aparecer

        val completed = workoutDao.getAllCompletedSessions().first()

        assertEquals(3, completed.size)
        assertEquals(3000L, completed[0].timestamp) // mais recente primeiro (DESC)
        assertEquals(2000L, completed[1].timestamp)
        assertEquals(1000L, completed[2].timestamp)
    }

    // =========================================================================
    // TESTES DE SET LOGS
    // =========================================================================

    @Test
    fun getSetsForSession_returnsInOrder() = runTest {
        val workoutId = insertWorkout()
        val exerciseId = insertExercise(workoutId)
        val sessionId = insertSession(workoutId)

        insertCompletedSet(sessionId, exerciseId, setNumber = 2, weight = 70f, reps = 8)
        insertCompletedSet(sessionId, exerciseId, setNumber = 1, weight = 60f, reps = 10)
        insertCompletedSet(sessionId, exerciseId, setNumber = 3, weight = 80f, reps = 6)

        val sets = workoutDao.getSetsForSession(sessionId).first()

        assertEquals(3, sets.size)
        assertEquals(1, sets[0].setNumber)
        assertEquals(2, sets[1].setNumber)
        assertEquals(3, sets[2].setNumber)
    }

    @Test
    fun deleteSetByNumber_removesOnlyTargetSet() = runTest {
        val workoutId = insertWorkout()
        val exerciseId = insertExercise(workoutId)
        val sessionId = insertSession(workoutId)

        insertCompletedSet(sessionId, exerciseId, setNumber = 1, weight = 60f, reps = 10)
        insertCompletedSet(sessionId, exerciseId, setNumber = 2, weight = 70f, reps = 8)

        workoutDao.deleteSetByNumber(exerciseId, sessionId, setNumber = 2)

        val remaining = workoutDao.getSetsForSession(sessionId).first()
        assertEquals(1, remaining.size)
        assertEquals(1, remaining[0].setNumber)
    }

    // =========================================================================
    // TESTES DE PRs E HISTORICAL
    // =========================================================================

    @Test
    fun getMaxWeightForExercise_returnsHighestWeight() = runTest {
        val workoutId = insertWorkout()
        val exerciseId = insertExercise(workoutId)
        val sessionId = insertSession(workoutId, isCompleted = true)

        insertCompletedSet(sessionId, exerciseId, 1, weight = 80f, reps = 10)
        insertCompletedSet(sessionId, exerciseId, 2, weight = 100f, reps = 5)
        insertCompletedSet(sessionId, exerciseId, 3, weight = 90f, reps = 8)

        val maxWeight = workoutDao.getMaxWeightForExercise(exerciseId)
        assertEquals(100f, maxWeight!!, 0.01f)
    }

    @Test
    fun getMax1RMForExercise_calculatesCorrectly() = runTest {
        val workoutId = insertWorkout()
        val exerciseId = insertExercise(workoutId)
        val sessionId = insertSession(workoutId, isCompleted = true)

        // 1RM = weight * (1 + reps/30)
        // Set 1: 100 * (1 + 5/30) = 100 * 1.1667 = 116.67
        // Set 2: 80 * (1 + 10/30) = 80 * 1.3333 = 106.67
        insertCompletedSet(sessionId, exerciseId, 1, weight = 100f, reps = 5)
        insertCompletedSet(sessionId, exerciseId, 2, weight = 80f, reps = 10)

        val max1RM = workoutDao.getMax1RMForExercise(exerciseId)
        // O set 1 tem o 1RM mais alto (~116.67)
        assertEquals(116.67f, max1RM!!, 1f)
    }

    @Test
    fun getPreviousSet_returnsFromLastCompletedSession() = runTest {
        val workoutId = insertWorkout()
        val exerciseId = insertExercise(workoutId)

        // sessão antiga (completa) — esta é a "previous"
        val oldSessionId = insertSession(workoutId, isCompleted = true, timestamp = 1000L)
        insertCompletedSet(oldSessionId, exerciseId, 1, weight = 70f, reps = 10)

        // sessão atual (ativa) — NÃO deve aparecer como "previous"
        val currentSessionId = insertSession(workoutId, isCompleted = false, timestamp = 2000L)
        insertCompletedSet(currentSessionId, exerciseId, 1, weight = 80f, reps = 8)

        val previous = workoutDao.getPreviousSet(exerciseId, setNumber = 1)

        assertNotNull(previous)
        assertEquals(70f, previous!!.weight, 0.01f) // da sessão completada, não da ativa
        assertEquals(10, previous.reps)
    }

    @Test
    fun getMaxWeight_ignoresActiveSessions() = runTest {
        val workoutId = insertWorkout()
        val exerciseId = insertExercise(workoutId)

        // sessão completa com peso 80
        val completedSessionId = insertSession(workoutId, isCompleted = true, timestamp = 1000L)
        insertCompletedSet(completedSessionId, exerciseId, 1, weight = 80f, reps = 10)

        // sessão ativa com peso 100 → NÃO deve contar
        val activeSessionId = insertSession(workoutId, isCompleted = false, timestamp = 2000L)
        insertCompletedSet(activeSessionId, exerciseId, 1, weight = 100f, reps = 5)

        val maxWeight = workoutDao.getMaxWeightForExercise(exerciseId)
        assertEquals(80f, maxWeight!!, 0.01f) // só conta sessões completas
    }

    // =========================================================================
    // TESTES DE CLEAR
    // =========================================================================

    @Test
    fun clearAllTables_removesEverything() = runTest {
        val workoutId = insertWorkout()
        insertExercise(workoutId)
        insertSession(workoutId)

        workoutDao.clearSetLogsTable()
        workoutDao.clearWorkoutSessionsTable()
        workoutDao.clearExercisesTable()
        workoutDao.clearWorkoutsTable()

        val workouts = workoutDao.getAllWorkouts().first()
        val exercises = workoutDao.getAllExercises().first()

        assertTrue(workouts.isEmpty())
        assertTrue(exercises.isEmpty())
    }
}
