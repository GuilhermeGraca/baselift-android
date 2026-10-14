package com.example.baselift.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.baselift.Model.local.dao.WorkoutDao
import com.example.baselift.Model.local.dao.ExercisePrMaxima
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.WorkoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRepositoryTest {

    private lateinit var workoutDao: WorkoutDao
    private lateinit var repository: WorkoutRepository
    private lateinit var workManager: WorkManager

    @Before
    fun setup() {
        workoutDao = mockk(relaxed = true)
        val context: android.content.Context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(context) } returns workManager
        repository = WorkoutRepository(workoutDao, context)
    }

    @After
    fun tearDown() {
        unmockkStatic(WorkManager::class)
    }

    @Test
    fun `adding a set uses one targeted increment instead of writing a stale exercise copy`() = runTest {
        val stale = ExerciseEntity(id = 7, workoutId = 1, name = "Old name", setCount = 1)
        repository.addSet(stale)
        coVerify(exactly = 1) { workoutDao.incrementSetCount(7) }
        coVerify(exactly = 0) { workoutDao.updateExercise(any()) }
        coVerify(exactly = 0) { workoutDao.updateExercises(any()) }
    }

    @Test
    fun `removal delegates its identity to one transaction and propagates conflicts`() = runTest {
        val exercise = ExerciseEntity(id = 7, workoutId = 1, name = "Bench", setCount = 3)
        coEvery { workoutDao.removeLastSet(7, 2, 3) } returnsMany listOf(true, false)
        assertEquals(true, repository.removeLastSet(exercise, 2))
        assertEquals(false, repository.removeLastSet(exercise, 2))
        coVerify(exactly = 2) { workoutDao.removeLastSet(7, 2, 3) }
        coVerify(exactly = 0) { workoutDao.deleteSetByNumber(any(), any(), any()) }
        coVerify(exactly = 0) { workoutDao.updateExercise(any()) }
    }

    @Test
    fun `metadata edits target only metadata columns`() = runTest {
        val exercise = ExerciseEntity(id = 7, workoutId = 1, name = "Old", setCount = 1)
        repository.updateExercise(exercise, "New", "Machine", "Chest")
        coVerify(exactly = 1) { workoutDao.updateExerciseMetadata(7, "New", "Machine", "Chest") }
        coVerify(exactly = 0) { workoutDao.updateExercise(any()) }
    }

    @Test
    fun `reorder delegates one batch of order updates instead of whole exercise writes`() = runTest {
        val exercises = listOf(ExerciseEntity(id = 7, workoutId = 1, name = "Bench", orderIndex = 1),
            ExerciseEntity(id = 8, workoutId = 1, name = "Press", orderIndex = 0))
        repository.reorderExercises(exercises)
        coVerify(exactly = 1) { workoutDao.reorderExercises(exercises) }
        coVerify(exactly = 0) { workoutDao.updateExercises(any()) }
        coVerify(exactly = 0) { workoutDao.updateExercise(any()) }
    }

    @Test
    fun `checkPR returns WEIGHT when new weight is greater than max weight`() = runTest {
        coEvery { workoutDao.getExercisePrMaxima(1) } returns ExercisePrMaxima(100f, 120f)
        
        val result = repository.checkPR(exerciseId = 1, weight = 105f, reps = 5)
        
        assertEquals("WEIGHT", result)
        coVerify(exactly = 1) { workoutDao.getExercisePrMaxima(1) }
        coVerify(exactly = 0) { workoutDao.getMaxWeightForExercise(any()) }
        coVerify(exactly = 0) { workoutDao.getMax1RMForExercise(any()) }
    }

    @Test
    fun `checkPR returns VOLUME when new 1RM is greater than max 1RM`() = runTest {
        coEvery { workoutDao.getExercisePrMaxima(1) } returns ExercisePrMaxima(100f, 120f)
        
        // 95 * (1 + 10/30) = 95 * 1.333 = 126.6 > 120
        val result = repository.checkPR(exerciseId = 1, weight = 95f, reps = 10)
        
        assertEquals("VOLUME", result)
    }

    @Test
    fun `checkPR returns NONE when neither max weight nor 1RM is beaten`() = runTest {
        coEvery { workoutDao.getExercisePrMaxima(1) } returns ExercisePrMaxima(100f, 120f)
        
        // 90 * (1 + 5/30) = 90 * 1.16 = 105 < 120
        val result = repository.checkPR(exerciseId = 1, weight = 90f, reps = 5)
        
        assertEquals("NONE", result)
    }

    @Test
    fun `empty PR history defaults both maxima to zero and preserves weight precedence`() = runTest {
        coEvery { workoutDao.getExercisePrMaxima(1) } returns ExercisePrMaxima(null, null)
        assertEquals("WEIGHT", repository.checkPR(1, 10f, 10))
        coVerify(exactly = 1) { workoutDao.getExercisePrMaxima(1) }
    }

    @Test
    fun `weight PR retains its epsilon threshold`() = runTest {
        coEvery { workoutDao.getExercisePrMaxima(1) } returns ExercisePrMaxima(100f, 1_000f)
        assertEquals("NONE", repository.checkPR(1, 100.009f, 1))
        assertEquals("WEIGHT", repository.checkPR(1, 100.02f, 1))
        coVerify(exactly = 2) { workoutDao.getExercisePrMaxima(1) }
    }

    @Test
    fun `estimated 1RM PR retains its epsilon threshold`() = runTest {
        coEvery { workoutDao.getExercisePrMaxima(1) } returnsMany listOf(
            ExercisePrMaxima(100f, 119.995f), ExercisePrMaxima(100f, 119.98f)
        )
        assertEquals("NONE", repository.checkPR(1, 90f, 10))
        assertEquals("VOLUME", repository.checkPR(1, 90f, 10))
    }

    @Test
    fun `completed sets with nonpositive inputs skip history reads and keep NONE`() = runTest {
        listOf(0f to 10, -1f to 10, 80f to 0, 80f to -1).forEach { (weight, reps) ->
            repository.logSet(1, 1, 1, weight, reps, isCompleted = true)
        }
        coVerify(exactly = 0) { workoutDao.getExercisePrMaxima(any()) }
        coVerify(exactly = 4) { workoutDao.insertSet(match { it.prType == "NONE" && it.isCompleted }) }
    }

    @Test
    fun `updating a completed set uses one history query and updates the intended row`() = runTest {
        coEvery { workoutDao.getExercisePrMaxima(2) } returns ExercisePrMaxima(100f, 120f)
        repository.logSet(3, 2, 4, 95f, 10, isCompleted = true, existingSetId = 5)
        coVerify(exactly = 1) { workoutDao.getExercisePrMaxima(2) }
        coVerify(exactly = 1) { workoutDao.updateSet(match {
            it.id == 5 && it.sessionId == 3 && it.exerciseId == 2 && it.setNumber == 4 &&
                it.weight == 95f && it.reps == 10 && it.prType == "VOLUME"
        }) }
        coVerify(exactly = 0) { workoutDao.insertSet(any()) }
    }

    @Test
    fun `unchecking an unpersisted set performs no history read or write`() = runTest {
        repository.logSet(1, 1, 1, 100f, 10, isCompleted = false)
        coVerify(exactly = 0) { workoutDao.getExercisePrMaxima(any()) }
        coVerify(exactly = 0) { workoutDao.deleteSet(any()) }
        coVerify(exactly = 0) { workoutDao.insertSet(any()) }
        coVerify(exactly = 0) { workoutDao.updateSet(any()) }
    }

    @Test
    fun `logSet checks PR only when completed and sets PR type`() = runTest {
        coEvery { workoutDao.getExercisePrMaxima(1) } returns ExercisePrMaxima(100f, 120f)
        
        repository.logSet(
            sessionId = 1,
            exerciseId = 1,
            setNumber = 1,
            weight = 110f, // New PR!
            reps = 1,
            isCompleted = true,
            existingSetId = 0
        )
        
        coVerify(exactly = 1) { 
            workoutDao.insertSet(match { 
                it.weight == 110f && it.prType == "WEIGHT" && it.isCompleted 
            }) 
        }
        coVerify(exactly = 1) { workoutDao.getExercisePrMaxima(1) }
    }

    @Test
    fun `logSet deletes existing set if marked as not completed`() = runTest {
        repository.logSet(
            sessionId = 1,
            exerciseId = 1,
            setNumber = 1,
            weight = 100f,
            reps = 10,
            isCompleted = false,
            existingSetId = 5
        )
        
        coVerify(exactly = 1) { 
            workoutDao.deleteSet(match { it.id == 5 })
        }
        coVerify(exactly = 0) { workoutDao.getExercisePrMaxima(any()) }
        coVerify(exactly = 0) { 
            workoutDao.insertSet(any())
        }
        coVerify(exactly = 0) { 
            workoutDao.updateSet(any())
        }
    }

    @Test
    fun `startOrGetSession creates new session if none exists`() = runTest {
        coEvery { workoutDao.getActiveSession(1) } returns null
        coEvery { workoutDao.insertSession(any()) } returns 5L
        
        val session = repository.startOrGetSession(workoutId = 1)
        
        assertEquals(5, session.id)
        assertEquals(1, session.workoutId)
        coVerify(exactly = 1) { workoutDao.insertSession(any()) }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork("REMINDER_5", ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `startOrGetSession returns active session if it exists`() = runTest {
        val existingSession = WorkoutSessionEntity(id = 2, workoutId = 1, timestamp = 1234L)
        coEvery { workoutDao.getActiveSession(1) } returns existingSession
        
        val session = repository.startOrGetSession(workoutId = 1)
        
        assertEquals(2, session.id)
        coVerify(exactly = 0) { workoutDao.insertSession(any()) }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork("REMINDER_2", ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>())
        }
    }
}
