package com.example.baselift.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.dao.WorkoutDao
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionCreationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: WorkoutDao

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.workoutDao()
    }

    @After fun tearDown() { database.close() }

    private fun sessionCount(db: AppDatabase, workoutId: Int): Int =
        db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM workout_sessions WHERE workoutId = ?", arrayOf(workoutId)
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    @Test fun concurrentCreationReturnsOneStoredSession() = runTest {
        val workout = dao.insertWorkout(WorkoutEntity(name = "Push")).toInt()
        val start = CompletableDeferred<Unit>()
        val sessions = coroutineScope {
            val requests = (1..24).map {
                async(Dispatchers.Default) { start.await(); dao.getOrCreateActiveSession(workout) }
            }
            start.complete(Unit)
            requests.awaitAll()
        }
        val stored = dao.getActiveSession(workout)!!
        assertTrue(stored.id > 0)
        assertFalse(stored.isCompleted)
        assertNull(stored.endTime)
        assertEquals(workout, stored.workoutId)
        assertTrue(sessions.all { it == stored })
        assertEquals(1, sessionCount(database, workout))
    }

    @Test fun recoveryKeepsSessionTimestampAndLoggedSets() = runTest {
        val workout = dao.insertWorkout(WorkoutEntity(name = "Push")).toInt()
        val exercise = dao.insertExercise(ExerciseEntity(workoutId = workout, name = "Bench")).toInt()
        val sessionId = dao.insertSession(WorkoutSessionEntity(workoutId = workout, timestamp = 1_000L)).toInt()
        dao.insertSet(SetLogEntity(sessionId = sessionId, exerciseId = exercise, setNumber = 1,
            weight = 80f, reps = 10, isCompleted = true, prType = "WEIGHT"))
        val existing = dao.getActiveSession(workout)!!
        val logs = dao.getSetsForSession(sessionId).first()
        repeat(4) { assertEquals(existing, dao.getOrCreateActiveSession(workout)) }
        assertEquals(1_000L, dao.getActiveSession(workout)!!.timestamp)
        assertEquals(logs, dao.getSetsForSession(sessionId).first())
        assertEquals(1, sessionCount(database, workout))
    }

    @Test fun finalizationStartsANewSessionAndRetainsCompletedHistory() = runTest {
        val workout = dao.insertWorkout(WorkoutEntity(name = "Push")).toInt()
        val exercise = dao.insertExercise(ExerciseEntity(workoutId = workout, name = "Bench")).toInt()
        val first = dao.getOrCreateActiveSession(workout)
        dao.insertSet(SetLogEntity(sessionId = first.id, exerciseId = exercise, setNumber = 1,
            weight = 80f, reps = 10, isCompleted = true))
        val logs = dao.getSetsForSession(first.id).first()
        val completed = first.copy(isCompleted = true, endTime = System.currentTimeMillis())
        dao.updateSession(completed)
        val next = dao.getOrCreateActiveSession(workout)
        assertNotEquals(first.id, next.id)
        assertFalse(next.isCompleted)
        assertNull(next.endTime)
        assertEquals(next, dao.getActiveSession(workout))
        assertEquals(completed, dao.getAllCompletedSessions().first().single())
        assertEquals(logs, dao.getSetsForSession(first.id).first())
        assertTrue(dao.getSetsForSession(next.id).first().isEmpty())
        assertEquals(first.id, dao.getPreviousSet(exercise, 1)!!.sessionId)
        assertEquals(2, sessionCount(database, workout))
    }

    @Test fun concurrentRequestsRemainScopedToEachWorkout() = runTest {
        val workouts = listOf("Push", "Pull").map { dao.insertWorkout(WorkoutEntity(name = it)).toInt() }
        val start = CompletableDeferred<Unit>()
        val sessions = coroutineScope {
            val requests = (1..24).map { index ->
                async(Dispatchers.Default) {
                    start.await()
                    dao.getOrCreateActiveSession(workouts[index % workouts.size])
                }
            }
            start.complete(Unit)
            requests.awaitAll()
        }
        assertEquals(2, sessions.map { it.id }.distinct().size)
        workouts.forEach { workout ->
            val stored = dao.getActiveSession(workout)!!
            assertTrue(sessions.filter { it.workoutId == workout }.all { it == stored })
            assertEquals(1, sessionCount(database, workout))
        }
    }

    @Test fun failedInsertLeavesNoPartialSessionAndAllowsRetry() = runTest {
        val workout = dao.insertWorkout(WorkoutEntity(name = "Push")).toInt()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_session_insert BEFORE INSERT ON workout_sessions
            BEGIN SELECT RAISE(ABORT, 'injected session creation failure'); END
        """.trimIndent())
        try {
            dao.getOrCreateActiveSession(workout)
            fail("Expected insert failure")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertEquals(0, sessionCount(database, workout))
            assertNull(dao.getActiveSession(workout))
        }
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_session_insert")
        val created = dao.getOrCreateActiveSession(workout)
        assertEquals(created, dao.getActiveSession(workout))
        assertEquals(1, sessionCount(database, workout))
    }

    @Test fun legacyActiveDuplicatesAndTheirLogsAreRetainedWithoutCreatingAnother() = runTest {
        val workout = dao.insertWorkout(WorkoutEntity(name = "Push")).toInt()
        val exercise = dao.insertExercise(ExerciseEntity(workoutId = workout, name = "Bench")).toInt()
        val first = dao.insertSession(WorkoutSessionEntity(workoutId = workout, timestamp = 1_000L)).toInt()
        val second = dao.insertSession(WorkoutSessionEntity(workoutId = workout, timestamp = 2_000L)).toInt()
        listOf(first, second).forEach { session ->
            dao.insertSet(SetLogEntity(sessionId = session, exerciseId = exercise, setNumber = 1,
                weight = session.toFloat(), reps = 10, isCompleted = true))
        }
        val expected = dao.getActiveSession(workout)!!
        val logs = listOf(first, second).associateWith { dao.getSetsForSession(it).first() }
        assertEquals(expected, dao.getOrCreateActiveSession(workout))
        assertEquals(2, sessionCount(database, workout))
        logs.forEach { (session, records) -> assertEquals(records, dao.getSetsForSession(session).first()) }
    }

    @Test fun independentRoomInstancesShareOneCreatedSession() = runTest {
        val name = "baselift_session_creation_test_${UUID.randomUUID()}"
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val second = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val workout = first.workoutDao().insertWorkout(WorkoutEntity(name = "Push")).toInt()
            val start = CompletableDeferred<Unit>()
            val sessions = coroutineScope {
                val requests = (1..12).map { index ->
                    async(Dispatchers.Default) {
                        start.await()
                        val dao = if (index % 2 == 0) first.workoutDao() else second.workoutDao()
                        dao.getOrCreateActiveSession(workout)
                    }
                }
                start.complete(Unit)
                requests.awaitAll()
            }
            assertEquals(1, sessions.map { it.id }.distinct().size)
            assertEquals(first.workoutDao().getActiveSession(workout), second.workoutDao().getActiveSession(workout))
            assertEquals(1, sessionCount(first, workout))
        } finally {
            first.close()
            second.close()
            // Only this test's UUID-named database; never the application's persisted database.
            context.deleteDatabase(name)
        }
    }
}
