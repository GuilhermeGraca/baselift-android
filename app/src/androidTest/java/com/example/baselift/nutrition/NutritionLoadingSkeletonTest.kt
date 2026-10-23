package com.example.baselift.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.NutritionRepository
import com.example.baselift.Model.repository.UserRepository
import com.example.baselift.View.nutrition.NutritionScreen
import com.example.baselift.ViewModel.nutrition.NutritionViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionLoadingSkeletonTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var database: AppDatabase? = null

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database?.close()
    }

    @Test fun quickLogWorksWhileInitialDataIsPendingAndSkeletonsDisappearWhenReady() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        database = db
        val real = NutritionRepository(db.nutritionDao())
        val releaseTemplates = CompletableDeferred<Unit>()
        val source = object : INutritionRepository by real {
            override fun getAllMealTemplates(): Flow<List<MealTemplateEntity>> = flow {
                releaseTemplates.await()
                emitAll(real.getAllMealTemplates())
            }
        }
        lateinit var vm: NutritionViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = NutritionViewModel(source, UserRepository(db.userDao()))
            store.put("nutrition", vm)
        }

        compose.setContent { MaterialTheme { NutritionScreen(vm, {}) } }
        compose.onNodeWithTag("nutrition_progress_loading").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithTag("nutrition_quick_log").assertIsDisplayed()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("+ ADD ENTRY").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithTag("nutrition_meals_loading").assertIsDisplayed()

        runBlocking {
            withTimeout(5_000) { real.getAllNutritionLogs().first { it.size == 1 } }
        }
        assertEquals(true, vm.uiState.value.isLoading)
        releaseTemplates.complete(Unit)
        compose.waitUntil(5_000) { !vm.uiState.value.isLoading }
        assertEquals(100, vm.uiState.value.consumedCalories)
        compose.onNodeWithTag("nutrition_meals_loading").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithTag("nutrition_quick_log").assertIsDisplayed()
    }
}
