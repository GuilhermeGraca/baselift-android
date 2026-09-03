package com.example.baselift.repository

import com.example.baselift.Model.local.dao.NutritionDao
import com.example.baselift.Model.repository.NutritionRepository
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionRepositoryTest {

    private lateinit var nutritionDao: NutritionDao
    private lateinit var repository: NutritionRepository

    @Before
    fun setup() {
        nutritionDao = mockk(relaxed = true)
        repository = NutritionRepository(nutritionDao)
    }

    @Test
    fun `resetTodayLogs calls deleteLogsInTimeRange with correct start and end of day`() = runTest {
        repository.resetTodayLogs()
        
        // Calculate expected range
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val expectedStart = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val expectedEnd = calendar.timeInMillis
        
        // We use a small tolerance in case the clock ticked between repo call and our calculation
        val tolerance = 100L // 100 ms
        
        coVerify(exactly = 1) { 
            nutritionDao.deleteLogsInTimeRange(
                match { it >= expectedStart - tolerance && it <= expectedStart + tolerance },
                match { it >= expectedEnd - tolerance && it <= expectedEnd + tolerance }
            ) 
        }
    }

    @Test
    fun `getTodayLogs calls getLogsForTimeRange with correct start and end of day`() {
        repository.getTodayLogs()
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val expectedStart = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val expectedEnd = calendar.timeInMillis
        
        val tolerance = 100L
        
        verify(exactly = 1) {
            nutritionDao.getLogsForTimeRange(
                match { it >= expectedStart - tolerance && it <= expectedStart + tolerance },
                match { it >= expectedEnd - tolerance && it <= expectedEnd + tolerance }
            )
        }
    }
}
