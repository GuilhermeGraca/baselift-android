package com.example.baselift.repository

import com.example.baselift.Model.local.dao.WorkoutDao
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.WorkoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRepositoryTest {

    private lateinit var workoutDao: WorkoutDao
    private lateinit var repository: WorkoutRepository

    @Before
    fun setup() {
        workoutDao = mockk(relaxed = true)
        val context: android.content.Context = mockk(relaxed = true)
        repository = WorkoutRepository(workoutDao, context)
    }

    @Test
    fun `checkPR returns WEIGHT when new weight is greater than max weight`() = runTest {
        coEvery { workoutDao.getMaxWeightForExercise(1) } returns 100f
        coEvery { workoutDao.getMax1RMForExercise(1) } returns 120f
        
        val result = repository.checkPR(exerciseId = 1, weight = 105f, reps = 5)
        
        assertEquals("WEIGHT", result)
    }

    @Test
    fun `checkPR returns VOLUME when new 1RM is greater than max 1RM`() = runTest {
        coEvery { workoutDao.getMaxWeightForExercise(1) } returns 100f
        coEvery { workoutDao.getMax1RMForExercise(1) } returns 120f // approx 100 * (1 + 6/30) = 120
        
        // 95 * (1 + 10/30) = 95 * 1.333 = 126.6 > 120
        val result = repository.checkPR(exerciseId = 1, weight = 95f, reps = 10)
        
        assertEquals("VOLUME", result)
    }

    @Test
    fun `checkPR returns NONE when neither max weight nor 1RM is beaten`() = runTest {
        coEvery { workoutDao.getMaxWeightForExercise(1) } returns 100f
        coEvery { workoutDao.getMax1RMForExercise(1) } returns 120f
        
        // 90 * (1 + 5/30) = 90 * 1.16 = 105 < 120
        val result = repository.checkPR(exerciseId = 1, weight = 90f, reps = 5)
        
        assertEquals("NONE", result)
    }

    @Test
    fun `logSet checks PR only when completed and sets PR type`() = runTest {
        coEvery { workoutDao.getMaxWeightForExercise(1) } returns 100f
        coEvery { workoutDao.getMax1RMForExercise(1) } returns 120f
        
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
    }

    @Test
    fun `startOrGetSession returns active session if it exists`() = runTest {
        val existingSession = WorkoutSessionEntity(id = 2, workoutId = 1, timestamp = 1234L)
        coEvery { workoutDao.getActiveSession(1) } returns existingSession
        
        val session = repository.startOrGetSession(workoutId = 1)
        
        assertEquals(2, session.id)
        coVerify(exactly = 0) { workoutDao.insertSession(any()) }
    }
}
