package com.example.baselift.dao

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.MealTemplateEntity
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
        val name = "nutrition-migration-11-12"
        helper.createDatabase(name, 11).apply {
            execSQL("INSERT INTO meal_templates (id, name, iconName, calories, protein, carbs, fats) VALUES (7, 'Oats', 'restaurant', 380, 12, 68, 7)")
            execSQL("INSERT INTO nutrition_logs (id, name, calories, protein, carbs, fats, timestamp) VALUES (9, 'Oats', 190, 6, 34, 4, 123456)")
            execSQL("INSERT INTO workouts (id, name, orderIndex, defaultRestTimer) VALUES (3, 'Push', 0, 105)")
            close()
        }
        helper.runMigrationsAndValidate(name, 12, true, AppDatabase.MIGRATION_11_12).close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_11_12).build()
        try {
            val oldTemplate = database.nutritionDao().getAllMealTemplates().first().single()
            assertEquals(7, oldTemplate.id)
            assertEquals("Oats", oldTemplate.name)
            assertEquals(listOf(380, 12, 68, 7),
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
                    protein = 10, carbs = 5, fats = 2, isPer100g = true)
            )
            assertTrue(database.nutritionDao().getAllMealTemplates().first().any { it.name == "Yogurt" && it.isPer100g })
        } finally {
            database.close()
        }
    }

    @Test fun freshVersion12PersistsPer100gFlag() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "nutrition-fresh-12"
        context.deleteDatabase(name)
        val freshDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            freshDatabase.nutritionDao().insertMealTemplate(
                MealTemplateEntity(name = "Milk", iconName = "drink", calories = 64,
                    protein = 3, carbs = 5, fats = 4, isPer100g = true)
            )
        } finally {
            freshDatabase.close()
        }
        val reopenedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            assertTrue(reopenedDatabase.nutritionDao().getAllMealTemplates().first().single().isPer100g)
        } finally {
            reopenedDatabase.close()
        }
    }
}
