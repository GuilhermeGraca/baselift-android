package com.example.baselift.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.NutritionDay
import com.example.baselift.Model.repository.NutritionRepository
import com.example.baselift.Model.repository.UserRepository
import com.example.baselift.View.nutrition.NutritionScreen
import com.example.baselift.ViewModel.nutrition.NutritionViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NutritionDateRefreshTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var database: AppDatabase? = null
    private val dailyQueries = AtomicInteger()
    private fun instant(value: String) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)!!.time
    private class ScreenLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private fun db(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.startsWith("SELECT * FROM nutrition_logs WHERE", ignoreCase = true)) dailyQueries.incrementAndGet()
            }, Executor { it.run() }).build().also { database = it }
    }
    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database?.close()
    }

    @Test fun resumeKeepsSameDayQueryAndChangedDateZoneAndResetUseExactStoredBounds() {
        val db = db()
        val now = AtomicLong(instant("2026-10-17T12:00:00.000Z"))
        val zone = AtomicReference(TimeZone.getTimeZone("UTC"))
        val real = NutritionRepository(db.nutritionDao(), now::get, zone::get)
        val users = UserRepository(db.userDao())
        val requests = AtomicInteger()
        runBlocking {
            users.saveUser(UserEntity(dailyCaloriesGoal = 2500))
            listOf(
                Triple("Previous", "2026-10-16T12:00:00.000Z", 100),
                Triple("First", "2026-10-17T00:00:00.000Z", 200),
                Triple("Last", "2026-10-17T23:59:59.999Z", 300),
                Triple("Next", "2026-10-18T00:00:00.000Z", 400)
            ).forEach { (name, time, calories) ->
                real.insertNutritionLog(NutritionLogEntity(name = name, calories = calories,
                    protein = 0, carbs = 0, fats = 0, timestamp = instant(time)))
            }
        }
        val source = object : INutritionRepository by real {
            override fun getLogsForDay(day: NutritionDay) = real.getLogsForDay(day).also { requests.incrementAndGet() }
        }
        lateinit var vm: NutritionViewModel
        lateinit var owner: ScreenLifecycle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            owner = ScreenLifecycle().also { it.registry.currentState = Lifecycle.State.RESUMED }
            vm = NutritionViewModel(source, users, now::get, zone::get); store.put("nutrition", vm)
        }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            MaterialTheme { NutritionScreen(vm, {}) }
        } }
        compose.waitUntil(5_000) { vm.uiState.value.consumedCalories == 500 }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        compose.onNodeWithText("Last").assertIsDisplayed()
        assertEquals(listOf("First", "Last"), vm.uiState.value.todayLogs.map { it.name })
        val queriesBefore = dailyQueries.get()
        repeat(3) {
            compose.runOnIdle {
                owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        }
        compose.waitForIdle()
        assertEquals(1, requests.get()); assertEquals(queriesBefore, dailyQueries.get())
        now.set(instant("2026-10-18T00:30:00.000Z"))
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.waitUntil(5_000) { vm.uiState.value.consumedCalories == 400 }
        assertEquals(2, requests.get()); assertEquals(listOf("Next"), vm.uiState.value.todayLogs.map { it.name })
        zone.set(TimeZone.getTimeZone("Pacific/Honolulu"))
        compose.runOnIdle { vm.refreshDate() }
        compose.waitUntil(5_000) { vm.uiState.value.consumedCalories == 700 }
        assertEquals(3, requests.get())
        assertEquals(listOf("Last", "Next"), vm.uiState.value.todayLogs.map { it.name })
        compose.runOnIdle { vm.resetTodayLogs() }
        compose.waitUntil(5_000) { vm.uiState.value.todayLogs.isEmpty() }
        assertEquals(setOf("Previous", "First"), runBlocking { real.getAllNutritionLogs().first().map { it.name }.toSet() })
        compose.runOnIdle { vm.addQuickLog(50, isCaloriesOnly = true) }
        compose.waitUntil(5_000) { vm.uiState.value.consumedCalories == 50 }
        assertEquals(now.get(), vm.uiState.value.todayLogs.single().timestamp)
        assertEquals(3, requests.get())
    }

    @Test fun sqliteReadAndResetIncludeBothBoundaryMillisecondsAndPreserveAdjacentDaysAcrossDst() = runBlocking {
        val db = db()
        val dao = db.nutritionDao()
        val meal = MealTemplateEntity(1, "Kept", "restaurant", 600, 40, 70, 10)
        dao.insertMealTemplate(meal)
        listOf(
            Triple("Europe/Lisbon", "2026-03-29T00:00:00.000Z", "2026-03-29T23:00:00.000Z"),
            Triple("Europe/Lisbon", "2026-10-24T23:00:00.000Z", "2026-10-26T00:00:00.000Z"),
            Triple("America/Havana", "2026-11-01T04:00:00.000Z", "2026-11-02T05:00:00.000Z"),
            Triple("America/Sao_Paulo", "2018-11-04T03:00:00.000Z", "2018-11-05T02:00:00.000Z")
        ).forEach { (zoneId, startString, endString) ->
            dao.clearNutritionLogsTable()
            val start = instant(startString); val end = instant(endString)
            val zone = TimeZone.getTimeZone(zoneId)
            val now = start + 12 * 3_600_000L
            val repository = NutritionRepository(dao, { now }, { zone })
            listOf(start - 1, start, end - 1, end).forEachIndexed { index, time ->
                dao.insertNutritionLog(NutritionLogEntity(id = index + 1, name = "Boundary $index", calories = 100,
                    protein = 0, carbs = 0, fats = 0, timestamp = time))
            }
            val day = NutritionDay.at(now, zone)
            assertEquals(start, day.startMillis); assertEquals(end, day.endExclusiveMillis)
            assertEquals(listOf(2, 3), repository.getLogsForDay(day).first().map { it.id })
            repository.resetTodayLogs()
            assertEquals(listOf(1, 4), dao.getAllNutritionLogs().first().map { it.id })
            assertEquals(listOf(meal), dao.getAllMealTemplates().first())
        }
    }
}
