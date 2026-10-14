package com.example.baselift.dao

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.dao.WorkoutDao
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.WorkoutRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    @Test
    fun removeLastSet_scopesCurrentSessionAndPreservesMetadataAndOtherHistory() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout, setCount = 3)
        val other = insertExercise(workout, "Other", setCount = 3)
        val active = insertSession(workout)
        val history = insertSession(workout, isCompleted = true)
        val keepFirst = insertCompletedSet(active, exercise, 1, 80f, 10)
        insertCompletedSet(active, exercise, 3, 70f, 10)
        insertCompletedSet(active, exercise, 3, 75f, 8) // Existing logical duplicates share removal scope.
        val keepOther = insertCompletedSet(active, other, 3, 50f, 10)
        val keepHistory = insertCompletedSet(history, exercise, 3, 60f, 10)
        val stale = workoutDao.getExercisesForWorkout(workout).first().single { it.id == exercise }
        workoutDao.updateExerciseMetadata(exercise, "Renamed", "Machine", "Chest")
        workoutDao.updateExerciseOrder(exercise, 7)
        val repository = WorkoutRepository(workoutDao, ApplicationProvider.getApplicationContext())
        assertTrue(repository.removeLastSet(stale, active))
        val after = workoutDao.getExercisesForWorkout(workout).first().single { it.id == exercise }
        assertEquals(stale.copy(name = "Renamed", equipment = "Machine", muscleGroups = "Chest",
            orderIndex = 7, setCount = 2), after)
        assertEquals(listOf(keepFirst, keepOther), workoutDao.getSetsForSession(active).first().map { it.id })
        assertEquals(keepHistory, workoutDao.getSetsForSession(history).first().single().id)
    }

    @Test
    fun removeLastSet_rejectsMinimumStaleMissingAndWrongOrCompletedSessions() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout, setCount = 3)
        val active = insertSession(workout)
        val completed = insertSession(workout, isCompleted = true)
        val wrongSession = insertSession(insertWorkout("Other"))
        insertCompletedSet(active, exercise, 3, 80f, 10)
        val before = workoutDao.getSetsForSession(active).first()
        assertFalse(workoutDao.removeLastSet(exercise, active, 2))
        assertFalse(workoutDao.removeLastSet(exercise, active, 1))
        assertFalse(workoutDao.removeLastSet(exercise, completed, 3))
        assertFalse(workoutDao.removeLastSet(exercise, wrongSession, 3))
        assertFalse(workoutDao.removeLastSet(exercise, -1, 3))
        assertFalse(workoutDao.removeLastSet(-1, active, 3))
        assertEquals(3, workoutDao.getExercisesForWorkout(workout).first().single().setCount)
        workoutDao.incrementSetCount(exercise)
        assertFalse(workoutDao.removeLastSet(exercise, active, 3))
        assertEquals(4, workoutDao.getExercisesForWorkout(workout).first().single().setCount)
        assertEquals(before, workoutDao.getSetsForSession(active).first())
        val minimum = insertExercise(workout, "Minimum", setCount = 1)
        insertCompletedSet(active, minimum, 1, 50f, 10)
        assertFalse(workoutDao.removeLastSet(minimum, active, 1))
        assertEquals(1, workoutDao.getExercisesForWorkout(workout).first().single { it.id == minimum }.setCount)
        assertTrue(workoutDao.getSetsForSession(active).first().any { it.exerciseId == minimum })
    }

    @Test
    fun removeLastSet_deleteFailureRollsBackCountAndLogsAndAllowsRetry() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout, setCount = 3)
        val session = insertSession(workout)
        insertCompletedSet(session, exercise, 3, 80f, 10)
        val before = workoutDao.getSetsForSession(session).first()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_last_set_delete BEFORE DELETE ON set_logs
            WHEN OLD.exerciseId = $exercise AND OLD.sessionId = $session
            BEGIN SELECT RAISE(ABORT, 'injected deletion failure'); END
        """.trimIndent())
        try {
            workoutDao.removeLastSet(exercise, session, 3)
            fail("Expected transaction failure")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertEquals(3, workoutDao.getExercisesForWorkout(workout).first().single().setCount)
            assertEquals(before, workoutDao.getSetsForSession(session).first())
        }
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_last_set_delete")
        assertTrue(workoutDao.removeLastSet(exercise, session, 3))
        assertEquals(2, workoutDao.getExercisesForWorkout(workout).first().single().setCount)
        assertTrue(workoutDao.getSetsForSession(session).first().isEmpty())
    }

    @Test
    fun removeLastSet_concurrentDuplicateConfirmationsRemoveExactlyOnce() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout, setCount = 3)
        val session = insertSession(workout)
        insertCompletedSet(session, exercise, 3, 80f, 10)
        val start = CompletableDeferred<Unit>()
        val results = coroutineScope {
            val jobs = (1..12).map {
                async(Dispatchers.Default) { start.await(); workoutDao.removeLastSet(exercise, session, 3) }
            }
            start.complete(Unit)
            jobs.awaitAll()
        }
        assertEquals(1, results.count { it })
        assertEquals(2, workoutDao.getExercisesForWorkout(workout).first().single().setCount)
        assertTrue(workoutDao.getSetsForSession(session).first().isEmpty())
    }

    @Test
    fun concurrentAddRemoveMetadataAndReorderPreserveEachOthersFields() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout, setCount = 3)
        val other = insertExercise(workout, "Other", setCount = 2)
        workoutDao.updateExerciseOrder(other, 1)
        val session = insertSession(workout)
        insertCompletedSet(session, exercise, 3, 80f, 10)
        val stale = workoutDao.getExercisesForWorkout(workout).first()
        val target = stale.single { it.id == exercise }
        val repository = WorkoutRepository(workoutDao, ApplicationProvider.getApplicationContext())
        val start = CompletableDeferred<Unit>()
        val removed = coroutineScope {
            val addition = async(Dispatchers.Default) { start.await(); repository.addSet(target) }
            val removal = async(Dispatchers.Default) { start.await(); repository.removeLastSet(target, session) }
            val edit = async(Dispatchers.Default) { start.await(); repository.updateExercise(target, "New", "Machine", "Chest") }
            val reorder = async(Dispatchers.Default) {
                start.await()
                repository.reorderExercises(stale.reversed().mapIndexed { index, entity -> entity.copy(orderIndex = index) })
            }
            start.complete(Unit)
            addition.await(); edit.await(); reorder.await()
            removal.await()
        }
        val after = workoutDao.getExercisesForWorkout(workout).first()
        assertEquals(listOf(other, exercise), after.map { it.id })
        assertEquals(if (removed) 3 else 4, after.last().setCount)
        assertEquals("New", after.last().name)
        assertEquals("Machine", after.last().equipment)
        assertEquals("Chest", after.last().muscleGroups)
        assertEquals(2, after.first().setCount)
        assertEquals(if (removed) 0 else 1, workoutDao.getSetsForSession(session).first().size)
    }

    @Test
    fun metadataAndReorderIgnoreStaleCountsAndPreserveEachOthersUpdates() = runTest {
        val workout = insertWorkout()
        val first = insertExercise(workout, setCount = 3)
        val second = insertExercise(workout, "Other", setCount = 2)
        workoutDao.updateExerciseOrder(second, 1)
        val stale = workoutDao.getExercisesForWorkout(workout).first()
        repeat(3) { workoutDao.incrementSetCount(first) }
        val repository = WorkoutRepository(workoutDao, ApplicationProvider.getApplicationContext())
        repository.updateExercise(stale.single { it.id == first }, "New", "Machine", "Chest")
        repository.reorderExercises(stale.reversed().mapIndexed { index, entity -> entity.copy(orderIndex = index) })
        val after = workoutDao.getExercisesForWorkout(workout).first()
        assertEquals(listOf(second, first), after.map { it.id })
        assertEquals(6, after.last().setCount)
        assertEquals("New", after.last().name)
        assertEquals("Machine", after.last().equipment)
        assertEquals("Chest", after.last().muscleGroups)
    }

    @Test
    fun reorderFailureRollsBackTheWholeBatchAndPreservesMetadataAndCounts() = runTest {
        val workout = insertWorkout()
        val first = insertExercise(workout, setCount = 3)
        val second = insertExercise(workout, "Other", setCount = 2)
        workoutDao.updateExerciseOrder(second, 1)
        val before = workoutDao.getExercisesForWorkout(workout).first()
        val reorder = before.map { it.copy(orderIndex = 1 - it.orderIndex) }
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_second_order BEFORE UPDATE OF orderIndex ON exercises
            WHEN OLD.id = $second
            BEGIN SELECT RAISE(ABORT, 'injected reorder failure'); END
        """.trimIndent())
        try {
            workoutDao.reorderExercises(reorder)
            fail("Expected batch failure")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertEquals(before, workoutDao.getExercisesForWorkout(workout).first())
        }
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_second_order")
        workoutDao.reorderExercises(reorder)
        val after = workoutDao.getExercisesForWorkout(workout).first()
        assertEquals(listOf(second, first), after.map { it.id })
        assertEquals(3, after.last().setCount)
        assertEquals(2, after.first().setCount)
    }

    @Test
    fun addSet_usesStoredCountAndPreservesMetadataHistoryAndOtherExercises() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout, setCount = 1)
        val other = insertExercise(workout, "Other", setCount = 3)
        val session = insertSession(workout)
        insertCompletedSet(session, exercise, 1, 80f, 10)
        val history = workoutDao.getSetsForSession(session).first()
        val stale = workoutDao.getExercisesForWorkout(workout).first().single { it.id == exercise }
        val latest = stale.copy(name = "Renamed", equipment = "Machine", muscleGroups = "Chest",
            orderIndex = 7, setCount = 5)
        workoutDao.updateExercise(latest)
        val repository = WorkoutRepository(workoutDao, ApplicationProvider.getApplicationContext())

        // All calls deliberately carry the old count and metadata, like delayed UI commands.
        repeat(6) { repository.addSet(stale) }
        val after = workoutDao.getExercisesForWorkout(workout).first()
        assertEquals(latest.copy(setCount = 11), after.single { it.id == exercise })
        assertEquals(3, after.single { it.id == other }.setCount)
        assertEquals(history, workoutDao.getSetsForSession(session).first())
    }

    @Test
    fun addSet_concurrentCallsPreserveEveryIncrement() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout, setCount = 3)
        val stale = workoutDao.getExercisesForWorkout(workout).first().single()
        val repository = WorkoutRepository(workoutDao, ApplicationProvider.getApplicationContext())
        val start = CompletableDeferred<Unit>()
        coroutineScope {
            repeat(24) {
                launch(Dispatchers.Default) {
                    start.await()
                    repository.addSet(stale)
                }
            }
            start.complete(Unit)
        }
        val after = workoutDao.getExercisesForWorkout(workout).first().single()
        assertEquals(exercise, after.id)
        assertEquals(27, after.setCount)
    }

    @Test
    fun addSet_missingOrDeletedExerciseDoesNotRecreateRows() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout)
        val session = insertSession(workout)
        insertCompletedSet(session, exercise, 1, 80f, 10)
        val stale = workoutDao.getExercisesForWorkout(workout).first().single()
        val other = insertExercise(workout, "Other", setCount = 2)
        workoutDao.deleteExercise(stale)
        assertEquals(0, workoutDao.incrementSetCount(exercise))
        assertEquals(0, workoutDao.incrementSetCount(-1))
        WorkoutRepository(workoutDao, ApplicationProvider.getApplicationContext()).addSet(stale)
        val remaining = workoutDao.getExercisesForWorkout(workout).first().single()
        assertEquals(other, remaining.id)
        assertEquals(2, remaining.setCount)
        assertTrue(workoutDao.getSetsForSession(session).first().isEmpty())
    }

    @Test
    fun exerciseChartQuery_scopesCompletedHistoryAndRefreshesAfterDeletion() = runTest {
        val workout = insertWorkout("Push")
        val firstExercise = insertExercise(workout, "Bench")
        val secondExercise = insertExercise(workout, "Press")
        val complete = insertSession(workout, isCompleted = true)
        val active = insertSession(workout)
        val expected = insertCompletedSet(complete, firstExercise, 1, 100f, 10)
        insertCompletedSet(complete, secondExercise, 1, 60f, 10)
        insertCompletedSet(active, firstExercise, 1, 999f, 10)
        workoutDao.insertSet(SetLogEntity(sessionId = complete, exerciseId = firstExercise,
            setNumber = 2, weight = 888f, reps = 10, isCompleted = false))
        val history = workoutDao.getCompletedSetLogsForExercise(firstExercise).first()
        assertEquals(listOf(expected), history.map { it.id })
        assertEquals(60f, workoutDao.getCompletedSetLogsForExercise(secondExercise).first().single().weight, 0f)
        val exercise = workoutDao.getExercisesForWorkout(workout).first().first { it.id == firstExercise }
        workoutDao.deleteExercise(exercise)
        assertTrue(workoutDao.getCompletedSetLogsForExercise(firstExercise).first().isEmpty())
        assertEquals(1, workoutDao.getCompletedSetLogsForExercise(secondExercise).first().size)
    }

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
    fun getExercisePrMaxima_combinesIndependentMaximaAndPreservesEligibility() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout)
        val other = insertExercise(workout, "Other")
        val complete = insertSession(workout, isCompleted = true)
        insertCompletedSet(complete, exercise, 1, 100f, 1)
        insertCompletedSet(complete, exercise, 2, 80f, 20) // Higher 1RM from a different row.
        insertCompletedSet(complete, other, 1, 999f, 30)
        insertCompletedSet(insertSession(workout), exercise, 1, 900f, 30)
        workoutDao.insertSet(SetLogEntity(sessionId = complete, exerciseId = exercise,
            setNumber = 3, weight = 800f, reps = 30, isCompleted = false))
        val maxima = workoutDao.getExercisePrMaxima(exercise)
        assertEquals(100f, maxima.maxWeight!!, 0f)
        assertEquals(80f * (1 + 20 / 30f), maxima.max1RM!!, 0.001f)
        assertEquals(workoutDao.getMaxWeightForExercise(exercise), maxima.maxWeight)
        assertEquals(workoutDao.getMax1RMForExercise(exercise), maxima.max1RM)
    }

    @Test
    fun getExercisePrMaxima_returnsNullableMaximaForEmptyOrIneligibleHistory() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout)
        val empty = workoutDao.getExercisePrMaxima(exercise)
        assertNull(empty.maxWeight)
        assertNull(empty.max1RM)
        insertCompletedSet(insertSession(workout), exercise, 1, 100f, 10)
        val complete = insertSession(workout, isCompleted = true)
        workoutDao.insertSet(SetLogEntity(sessionId = complete, exerciseId = exercise,
            setNumber = 2, weight = 900f, reps = 30, isCompleted = false))
        val ineligible = workoutDao.getExercisePrMaxima(exercise)
        assertNull(ineligible.maxWeight)
        assertNull(ineligible.max1RM)
        val missingExercise = workoutDao.getExercisePrMaxima(-1)
        assertNull(missingExercise.maxWeight)
        assertNull(missingExercise.max1RM)
    }

    @Test
    fun getExercisePrMaxima_reflectsSessionFinalizationAndCascadeDeletion() = runTest {
        val workout = insertWorkout()
        val exercise = insertExercise(workout)
        val active = insertSession(workout)
        insertCompletedSet(active, exercise, 1, 100f, 10)
        assertNull(workoutDao.getExercisePrMaxima(exercise).maxWeight)
        workoutDao.updateSession(workoutDao.getActiveSession(workout)!!.copy(isCompleted = true))
        assertEquals(100f, workoutDao.getExercisePrMaxima(exercise).maxWeight!!, 0f)
        workoutDao.deleteExercise(workoutDao.getExercisesForWorkout(workout).first().single())
        val cleared = workoutDao.getExercisePrMaxima(exercise)
        assertNull(cleared.maxWeight)
        assertNull(cleared.max1RM)
    }

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
    fun getPreviousSetsForWorkout_preservesPerSetFallbackAndEligibility() = runTest {
        val workoutId = insertWorkout()
        val exerciseId = insertExercise(workoutId)
        insertExercise(workoutId, "No history")
        val older = insertSession(workoutId, isCompleted = true, timestamp = 1_000)
        insertCompletedSet(older, exerciseId, 1, 70f, 10)
        val olderSecond = insertCompletedSet(older, exerciseId, 2, 65f, 12)
        val latest = insertSession(workoutId, isCompleted = true, timestamp = 2_000)
        val latestFirst = insertCompletedSet(latest, exerciseId, 1, 80f, 8)
        val active = insertSession(workoutId, timestamp = 3_000)
        insertCompletedSet(active, exerciseId, 1, 999f, 1)
        val incomplete = insertSession(workoutId, isCompleted = true, timestamp = 4_000)
        workoutDao.insertSet(
            SetLogEntity(sessionId = incomplete, exerciseId = exerciseId, setNumber = 1,
                weight = 999f, reps = 1, isCompleted = false)
        )
        val otherWorkout = insertWorkout("Pull")
        val otherExercise = insertExercise(otherWorkout)
        val otherSession = insertSession(otherWorkout, isCompleted = true, timestamp = 5_000)
        insertCompletedSet(otherSession, otherExercise, 1, 100f, 10)

        val previous = workoutDao.getPreviousSetsForWorkout(workoutId)
        assertEquals(listOf(latestFirst, olderSecond), previous.map { it.id })
        previous.forEach { log ->
            assertEquals(workoutDao.getPreviousSet(log.exerciseId, log.setNumber), log)
        }
    }

    @Test
    fun getPreviousSetsForWorkout_resolvesTimestampAndDuplicateTiesDeterministically() = runTest {
        val workoutId = insertWorkout()
        val exerciseId = insertExercise(workoutId)
        val earlierSession = insertSession(workoutId, isCompleted = true, timestamp = 1_000)
        val laterSession = insertSession(workoutId, isCompleted = true, timestamp = 1_000)
        insertCompletedSet(laterSession, exerciseId, 1, 80f, 8)
        val expected = insertCompletedSet(laterSession, exerciseId, 1, 85f, 6)
        // A higher log ID in an older tied session must not win over the later session.
        insertCompletedSet(earlierSession, exerciseId, 1, 999f, 1)

        val previous = workoutDao.getPreviousSetsForWorkout(workoutId).single()
        assertEquals(expected, previous.id)
        assertEquals(workoutDao.getPreviousSet(exerciseId, 1), previous)
    }

    @Test
    fun getPreviousSetsForWorkout_returnsEmptyWithoutCompletedHistory() = runTest {
        val workoutId = insertWorkout()
        val exerciseId = insertExercise(workoutId)
        val active = insertSession(workoutId)
        insertCompletedSet(active, exerciseId, 1, 80f, 10)
        assertTrue(workoutDao.getPreviousSetsForWorkout(workoutId).isEmpty())
        assertTrue(workoutDao.getPreviousSetsForWorkout(-1).isEmpty())
    }

    @Test
    fun getPreviousSetsForWorkout_returnsOnlyLatestRowsFromLargeHistory() = runTest {
        val workoutId = insertWorkout()
        val exerciseIds = (1..6).map { insertExercise(workoutId, "Exercise $it", setCount = 4) }
        database.withTransaction {
            repeat(100) { index ->
                val session = insertSession(workoutId, isCompleted = true, timestamp = index * 1_000L)
                exerciseIds.forEach { exerciseId ->
                    (1..4).forEach { setNumber ->
                        insertCompletedSet(session, exerciseId, setNumber, index.toFloat(), 10)
                    }
                }
            }
        }
        val previous = workoutDao.getPreviousSetsForWorkout(workoutId)
        assertEquals(24, previous.size)
        assertEquals(24, previous.map { it.exerciseId to it.setNumber }.distinct().size)
        assertTrue(previous.all { it.weight == 99f })
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
