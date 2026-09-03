package com.example.baselift.viewmodel

import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.ViewModel.nutrition.NutritionViewModel
import com.example.baselift.fakes.FakeNutritionRepository
import com.example.baselift.fakes.FakeUserRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var nutritionRepository: FakeNutritionRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var viewModel: NutritionViewModel

    @Before
    fun setup() {
        nutritionRepository = FakeNutritionRepository()
        userRepository = FakeUserRepository()
        viewModel = NutritionViewModel(nutritionRepository, userRepository)
    }

    @Test
    fun `initial uiState computes user targets and empty lists`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        userRepository.saveUser(
            UserEntity(
                id = 1,
                dailyCaloriesGoal = 2500,
                proteinGoal = 150,
                carbsGoal = 300,
                fatGoal = 80
            )
        )
        
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(2500, state.targetCalories)
        assertEquals(150, state.targetProtein)
        assertEquals(300, state.targetCarbs)
        assertEquals(80, state.targetFats)
        
        assertEquals(0, state.consumedCalories)
        assertEquals(0, state.consumedProtein)
        assertEquals(0, state.consumedCarbs)
        assertEquals(0, state.consumedFats)
        
        assertTrue(state.todayLogs.isEmpty())
        assertTrue(state.mealTemplates.isEmpty())
        assertFalse(state.isLoading)
        job.cancel()
    }

    @Test
    fun `addQuickLog inserts valid log into repository and updates state`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addQuickLog(calories = 500, protein = 30, carbs = 50, fats = 20, isCaloriesOnly = false)
        
        runCurrent()
        
        val logs = nutritionRepository.getAllNutritionLogs().first()
        assertEquals(1, logs.size)
        assertEquals("Detailed Macros", logs[0].name)
        assertEquals(500, logs[0].calories)
        
        val state = viewModel.uiState.value
        assertEquals(500, state.consumedCalories)
        assertEquals(30, state.consumedProtein)
        assertEquals(50, state.consumedCarbs)
        assertEquals(20, state.consumedFats)
        job.cancel()
    }

    @Test
    fun `addQuickLog with zero macros does nothing`() = runTest {
        viewModel.addQuickLog(calories = 0, protein = 0, carbs = 0, fats = 0, isCaloriesOnly = true)
        
        runCurrent()
        
        val logs = nutritionRepository.getAllNutritionLogs().first()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `deleteLog removes log from repository and state`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addQuickLog(calories = 500, protein = 30, carbs = 50, fats = 20, isCaloriesOnly = false)
        runCurrent()
        
        val logToDelete = nutritionRepository.getAllNutritionLogs().first()[0]
        
        viewModel.deleteLog(logToDelete)
        runCurrent()
        
        val logs = nutritionRepository.getAllNutritionLogs().first()
        assertTrue(logs.isEmpty())
        assertEquals(0, viewModel.uiState.value.consumedCalories)
        job.cancel()
    }

    @Test
    fun `resetTodayLogs clears all today logs`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addQuickLog(calories = 300, isCaloriesOnly = true)
        viewModel.addQuickLog(calories = 200, isCaloriesOnly = true)
        runCurrent()
        
        viewModel.resetTodayLogs()
        runCurrent()
        
        assertTrue(viewModel.uiState.value.todayLogs.isEmpty())
        assertEquals(0, viewModel.uiState.value.consumedCalories)
        job.cancel()
    }

    @Test
    fun `saveMealTemplate and logMealTemplate`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val template = MealTemplateEntity(
            name = "Chicken Rice",
            iconName = "Restaurant",
            calories = 600,
            protein = 40,
            carbs = 70,
            fats = 10
        )
        
        viewModel.saveMealTemplate(template)
        runCurrent()
        
        val templates = nutritionRepository.getAllMealTemplates().first()
        assertEquals(1, templates.size)
        assertEquals("Chicken Rice", templates[0].name)
        
        val savedTemplate = templates[0]
        viewModel.logMealTemplate(savedTemplate)
        runCurrent()
        
        val logs = nutritionRepository.getAllNutritionLogs().first()
        assertEquals(1, logs.size)
        assertEquals("Chicken Rice", logs[0].name)
        assertEquals(600, viewModel.uiState.value.consumedCalories)
        job.cancel()
    }

    @Test
    fun `deleteMealTemplate removes template`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val template = MealTemplateEntity(id = 1, name = "Test", iconName = "Fastfood", calories = 100, protein = 10, carbs = 10, fats = 10)
        viewModel.saveMealTemplate(template)
        runCurrent()
        
        val savedTemplate = nutritionRepository.getAllMealTemplates().first()[0]
        viewModel.deleteMealTemplate(savedTemplate)
        runCurrent()
        
        val templatesAfter = nutritionRepository.getAllMealTemplates().first()
        assertTrue(templatesAfter.isEmpty())
        assertTrue(viewModel.uiState.value.mealTemplates.isEmpty())
        job.cancel()
    }

    @Test
    fun `deleteAllNutrition clears logs and templates`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addQuickLog(calories = 100, isCaloriesOnly = true)
        viewModel.saveMealTemplate(MealTemplateEntity(name = "T", iconName = "Icon", calories = 100, protein = 10, carbs = 10, fats = 10))
        runCurrent()
        
        viewModel.deleteAllNutrition()
        runCurrent()
        
        assertTrue(viewModel.uiState.value.todayLogs.isEmpty())
        assertTrue(viewModel.uiState.value.mealTemplates.isEmpty())
        job.cancel()
    }
}
