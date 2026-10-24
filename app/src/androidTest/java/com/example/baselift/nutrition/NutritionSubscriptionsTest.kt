package com.example.baselift.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.NutritionDay
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.IUserRepository
import com.example.baselift.Model.repository.NutritionRepository
import com.example.baselift.Model.repository.UserRepository
import com.example.baselift.View.nutrition.NutritionScreen
import com.example.baselift.ViewModel.nutrition.NutritionViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class NutritionSubscriptionsTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var database: AppDatabase? = null

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database?.close()
    }

    @Test fun hiddenNutritionScreenStopsQueriesKeepsCommandsAndReturnsFreshData() {
        val dailyQueries = AtomicInteger()
        val templateQueries = AtomicInteger()
        val dailyActive = AtomicInteger()
        val templatesActive = AtomicInteger()
        val targetsActive = AtomicInteger()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.startsWith("SELECT", ignoreCase = true)) {
                    if (sql.contains("FROM nutrition_logs WHERE", ignoreCase = true)) dailyQueries.incrementAndGet()
                    if (sql.contains("FROM meal_templates", ignoreCase = true)) templateQueries.incrementAndGet()
                }
            }, Executor { it.run() }).build()
        database = db
        val real = NutritionRepository(db.nutritionDao())
        val users = UserRepository(db.userDao())
        val meal = MealTemplateEntity(1, "Lunch", "restaurant", 600, 40.0, 70.0, 10.0)
        runBlocking {
            users.saveUser(UserEntity(dailyCaloriesGoal = 2500))
            real.insertNutritionLog(NutritionLogEntity(name = "Breakfast", calories = 100,
                protein = 10.0, carbs = 10.0, fats = 2.0, timestamp = System.currentTimeMillis()))
            real.insertMealTemplate(meal)
        }
        fun <T> tracked(rows: Flow<T>, active: AtomicInteger): Flow<T> = flow {
            active.incrementAndGet()
            try { emitAll(rows) } finally { active.decrementAndGet() }
        }
        val source = object : INutritionRepository by real {
            override fun getLogsForDay(day: NutritionDay) = tracked(real.getLogsForDay(day), dailyActive)
            override fun getAllMealTemplates() = tracked(real.getAllMealTemplates(), templatesActive)
        }
        val userSource = object : IUserRepository by users {
            override fun getUser() = tracked(users.getUser(), targetsActive)
        }
        lateinit var vm: NutritionViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = NutritionViewModel(source, userSource)
            store.put("nutrition", vm)
        }
        assertEquals(0, dailyQueries.get())
        assertEquals(0, templateQueries.get())
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (visible.value) NutritionScreen(vm, {}) } }
        compose.waitUntil(5_000) { !vm.uiState.value.isLoading }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText("Lunch").assertIsDisplayed()
        assertEquals(100, vm.uiState.value.consumedCalories)
        val cached = vm.uiState.value
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(5_000) {
            dailyActive.get() == 0 && templatesActive.get() == 0 && targetsActive.get() == 0
        }
        val dailyBefore = dailyQueries.get()
        val templatesBefore = templateQueries.get()
        assertTrue(dailyBefore > 0)
        assertTrue(templatesBefore > 0)
        compose.runOnIdle { vm.logMealTemplate(meal) }
        runBlocking {
            // This unbounded test read is excluded from the daily range-query counter.
            withTimeout(5_000) { db.nutritionDao().getAllNutritionLogs().first { it.size == 2 } }
            real.deleteMealTemplate(meal)
            users.saveUser(UserEntity(dailyCaloriesGoal = 3000))
        }
        assertEquals(dailyBefore, dailyQueries.get())
        assertEquals(templatesBefore, templateQueries.get())
        assertSame(cached, vm.uiState.value)
        assertEquals(0, dailyActive.get())
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(5_000) {
            vm.uiState.value.consumedCalories == 700 && vm.uiState.value.targetCalories == 3000 &&
                vm.uiState.value.mealTemplates.isEmpty()
        }
        assertEquals(setOf("Breakfast", "Lunch"), vm.uiState.value.todayLogs.map { it.name }.toSet())
        assertEquals(1, dailyActive.get())
        assertEquals(1, templatesActive.get())
        assertEquals(1, targetsActive.get())
        assertTrue(dailyQueries.get() > dailyBefore)
        assertTrue(templateQueries.get() > templatesBefore)
    }
}
