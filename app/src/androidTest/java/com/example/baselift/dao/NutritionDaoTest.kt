package com.example.baselift.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.dao.NutritionDao
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes instrumentados para o NutritionDao.
 *
 * Verifica que as queries de inserção, remoção e filtragem por intervalo
 * de timestamps funcionam corretamente para logs de nutrição e templates.
 */
@RunWith(AndroidJUnit4::class)
class NutritionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var nutritionDao: NutritionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        nutritionDao = database.nutritionDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // =========================================================================
    // TESTES DE NUTRITION LOGS
    // =========================================================================

    @Test
    fun insertLog_appearsInTimeRange() = runTest {
        val timestamp = 1000L
        val log = NutritionLogEntity(
            name = "Almoço", calories = 500, protein = 30,
            carbs = 50, fats = 15, timestamp = timestamp
        )

        nutritionDao.insertNutritionLog(log)
        val results = nutritionDao.getLogsForTimeRange(0L, 2000L).first()

        assertEquals(1, results.size)
        assertEquals("Almoço", results[0].name)
        assertEquals(500, results[0].calories)
    }

    @Test
    fun getLogsForTimeRange_excludesOutOfRange() = runTest {
        nutritionDao.insertNutritionLog(
            NutritionLogEntity(calories = 300, protein = 20, carbs = 30, fats = 10, timestamp = 500L)
        )
        nutritionDao.insertNutritionLog(
            NutritionLogEntity(calories = 600, protein = 40, carbs = 60, fats = 20, timestamp = 1500L)
        )
        nutritionDao.insertNutritionLog(
            NutritionLogEntity(calories = 400, protein = 25, carbs = 40, fats = 15, timestamp = 2500L)
        )

        // filtrar apenas o intervalo 1000-2000
        val results = nutritionDao.getLogsForTimeRange(1000L, 2000L).first()

        assertEquals(1, results.size)
        assertEquals(600, results[0].calories)
    }

    @Test
    fun deleteLog_removesSpecificEntry() = runTest {
        val log = NutritionLogEntity(
            calories = 500, protein = 30, carbs = 50, fats = 15, timestamp = 1000L
        )
        nutritionDao.insertNutritionLog(log)

        val inserted = nutritionDao.getAllNutritionLogs().first()[0]
        nutritionDao.deleteNutritionLog(inserted)

        val results = nutritionDao.getAllNutritionLogs().first()
        assertTrue(results.isEmpty())
    }

    @Test
    fun deleteLogsInTimeRange_onlyDeletesTargetRange() = runTest {
        nutritionDao.insertNutritionLog(
            NutritionLogEntity(calories = 300, protein = 0, carbs = 0, fats = 0, timestamp = 500L)
        )
        nutritionDao.insertNutritionLog(
            NutritionLogEntity(calories = 600, protein = 0, carbs = 0, fats = 0, timestamp = 1500L)
        )

        nutritionDao.deleteLogsInTimeRange(1000L, 2000L)

        val remaining = nutritionDao.getAllNutritionLogs().first()
        assertEquals(1, remaining.size)
        assertEquals(300, remaining[0].calories)
    }

    @Test
    fun getAllNutritionLogs_returnsAllEntries() = runTest {
        repeat(5) { i ->
            nutritionDao.insertNutritionLog(
                NutritionLogEntity(
                    calories = 100 * (i + 1), protein = 10, carbs = 20, fats = 5,
                    timestamp = (i * 1000L)
                )
            )
        }

        val all = nutritionDao.getAllNutritionLogs().first()
        assertEquals(5, all.size)
    }

    // =========================================================================
    // TESTES DE MEAL TEMPLATES
    // =========================================================================

    @Test
    fun insertMealTemplate_appearsInGetAll() = runTest {
        val template = MealTemplateEntity(
            name = "Batido Proteico", iconName = "shake",
            calories = 350, protein = 40, carbs = 20, fats = 10
        )

        nutritionDao.insertMealTemplate(template)
        val results = nutritionDao.getAllMealTemplates().first()

        assertEquals(1, results.size)
        assertEquals("Batido Proteico", results[0].name)
        assertEquals(40, results[0].protein)
    }

    @Test
    fun deleteMealTemplate_removesEntry() = runTest {
        val template = MealTemplateEntity(
            name = "Aveia", iconName = "bowl",
            calories = 300, protein = 10, carbs = 50, fats = 8
        )
        nutritionDao.insertMealTemplate(template)

        val inserted = nutritionDao.getAllMealTemplates().first()[0]
        nutritionDao.deleteMealTemplate(inserted)

        val results = nutritionDao.getAllMealTemplates().first()
        assertTrue(results.isEmpty())
    }

    // =========================================================================
    // TESTES DE CLEAR
    // =========================================================================

    @Test
    fun clearNutritionLogsTable_removesAllLogs() = runTest {
        repeat(3) {
            nutritionDao.insertNutritionLog(
                NutritionLogEntity(calories = 100, protein = 0, carbs = 0, fats = 0, timestamp = 1000L)
            )
        }

        nutritionDao.clearNutritionLogsTable()

        val remaining = nutritionDao.getAllNutritionLogs().first()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun clearMealTemplatesTable_removesAllTemplates() = runTest {
        nutritionDao.insertMealTemplate(
            MealTemplateEntity(name = "A", iconName = "a", calories = 100, protein = 0, carbs = 0, fats = 0)
        )
        nutritionDao.insertMealTemplate(
            MealTemplateEntity(name = "B", iconName = "b", calories = 200, protein = 0, carbs = 0, fats = 0)
        )

        nutritionDao.clearMealTemplatesTable()

        val remaining = nutritionDao.getAllMealTemplates().first()
        assertTrue(remaining.isEmpty())
    }
}
