package com.example.baselift.dao

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun upgradeFrom11PreservesTemplatesAndLogsAndAllowsNewPer100gTemplates() = runBlocking {
        val name = "nutrition-migration-11-13"
        helper.createDatabase(name, 11).apply {
            execSQL("INSERT INTO meal_templates (id, name, iconName, calories, protein, carbs, fats) VALUES (7, 'Oats', 'restaurant', 380, 12, 68, 7)")
            execSQL("INSERT INTO nutrition_logs (id, name, calories, protein, carbs, fats, timestamp) VALUES (9, 'Oats', 190, 6, 34, 4, 123456)")
            execSQL("INSERT INTO workouts (id, name, orderIndex, defaultRestTimer) VALUES (3, 'Push', 0, 105)")
            close()
        }
        helper.runMigrationsAndValidate(name, 13, true, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13).close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13).build()
        try {
            val oldTemplate = database.nutritionDao().getAllMealTemplates().first().single()
            assertEquals(7, oldTemplate.id)
            assertEquals("Oats", oldTemplate.name)
            assertEquals(listOf(380, 12.0, 68.0, 7.0),
                listOf(oldTemplate.calories, oldTemplate.protein, oldTemplate.carbs, oldTemplate.fats))
            assertFalse(oldTemplate.isPer100g)
            val oldLog = database.nutritionDao().getAllNutritionLogs().first().single()
            assertEquals(9, oldLog.id)
            assertEquals(190, oldLog.calories)
            assertEquals(123456L, oldLog.timestamp)
            database.openHelper.readableDatabase.query("SELECT name, defaultRestTimer FROM workouts WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Push", cursor.getString(0))
                assertEquals(105, cursor.getInt(1))
            }

            database.nutritionDao().insertMealTemplate(
                MealTemplateEntity(name = "Yogurt", iconName = "restaurant", calories = 90,
                    protein = 10.0, carbs = 5.0, fats = 2.0, isPer100g = true)
            )
            assertTrue(database.nutritionDao().getAllMealTemplates().first().any { it.name == "Yogurt" && it.isPer100g })
        } finally {
            database.close()
        }
    }

    @Test fun upgradeFrom12PreservesBothMealBasesAndLogsAndPersistsDecimalsAfterReopen() = runBlocking {
        val name = "nutrition-migration-12-13"
        helper.createDatabase(name, 12).apply {
            execSQL("INSERT INTO meal_templates (id, name, iconName, calories, protein, carbs, fats, isPer100g) VALUES (7, 'Oats', 'restaurant', 380, 12, 68, 7, 1), (8, 'Lunch', 'restaurant', 600, 40, 70, 10, 0)")
            execSQL("INSERT INTO nutrition_logs (id, name, calories, protein, carbs, fats, timestamp) VALUES (9, NULL, 190, 6, 34, 4, 123456)")
            close()
        }
        helper.runMigrationsAndValidate(name, 13, true, AppDatabase.MIGRATION_12_13).close()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val oldLog = NutritionLogEntity(9, null, 190, 6.0, 34.0, 4.0, 123456L)
        val fractionalLog = NutritionLogEntity(name = "Yogurt", calories = 12,
            protein = 0.31, carbs = 0.52, fats = 0.01, timestamp = 123457L)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val templates = database.nutritionDao().getAllMealTemplates().first().associateBy { it.id }
            assertEquals(MealTemplateEntity(7, "Oats", "restaurant", 380, 12.0, 68.0, 7.0, true), templates[7])
            assertEquals(MealTemplateEntity(8, "Lunch", "restaurant", 600, 40.0, 70.0, 10.0, false), templates[8])
            assertEquals(listOf(oldLog), database.nutritionDao().getAllNutritionLogs().first())
            database.nutritionDao().insertNutritionLog(fractionalLog)
        } finally {
            database.close()
        }
        val reopenedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val logs = reopenedDatabase.nutritionDao().getAllNutritionLogs().first()
            assertTrue(logs.contains(oldLog))
            val saved = logs.single { it.name == "Yogurt" }
            assertTrue(saved.id > oldLog.id)
            assertEquals(fractionalLog.copy(id = saved.id), saved)
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test fun freshVersion13PersistsDecimalMacrosAndPer100gFlag() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "nutrition-fresh-13"
        context.deleteDatabase(name)
        val freshDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            freshDatabase.nutritionDao().insertMealTemplate(
                MealTemplateEntity(name = "Milk", iconName = "drink", calories = 64,
                    protein = 3.25, carbs = 5.1, fats = 0.04, isPer100g = true)
            )
        } finally {
            freshDatabase.close()
        }
        val reopenedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val saved = reopenedDatabase.nutritionDao().getAllMealTemplates().first().single()
            assertTrue(saved.isPer100g)
            assertEquals(3.25, saved.protein, 0.0)
            assertEquals(5.1, saved.carbs, 0.0)
            assertEquals(0.04, saved.fats, 0.0)
        } finally {
            reopenedDatabase.close()
        }
    }
}
