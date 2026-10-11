package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.ViewModel.nutrition.NutritionViewModel
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.fakes.FakeNutritionRepository
import com.example.baselift.fakes.FakeUserRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.After
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
    private val viewModelStore = ViewModelStore()

    @Before
    fun setup() {
        nutritionRepository = FakeNutritionRepository()
        userRepository = FakeUserRepository()
        viewModel = NutritionViewModel(nutritionRepository, userRepository)
        viewModelStore.put("nutrition", viewModel)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    private class ControlledDeletionRepository(
        private val source: INutritionRepository
    ) : INutritionRepository by source {
        val startedTemplates = mutableListOf<MealTemplateEntity>()
        val release = CompletableDeferred<Unit>()
        var failure: Exception? = null

        override suspend fun deleteMealTemplate(template: MealTemplateEntity) {
            startedTemplates += template
            release.await()
            failure?.let { throw it }
            source.deleteMealTemplate(template)
        }
    }

    private fun useControlledDeletion(): ControlledDeletionRepository {
        val controlled = ControlledDeletionRepository(nutritionRepository)
        viewModel = NutritionViewModel(controlled, userRepository)
        viewModelStore.put("nutrition", viewModel)
        return controlled
    }

    private fun meal(id: Int) = MealTemplateEntity(
        id = id, name = "Meal $id", iconName = "restaurant",
        calories = 600, protein = 40, carbs = 70, fats = 10
    )

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
    fun `deletion starts without animation delay and ignores duplicate confirmations`() = runTest {
        val template = meal(1)
        nutritionRepository.insertMealTemplate(template)
        val controlled = useControlledDeletion()
        viewModel.deleteMealTemplate(template)
        assertEquals(setOf(template.id), viewModel.mealTemplateDeletion.value.pendingTemplateIds)
        viewModel.deleteMealTemplate(template)
        runCurrent()

        assertEquals(listOf(template), controlled.startedTemplates)
        assertEquals(listOf(template), nutritionRepository.getAllMealTemplates().first())
        controlled.release.complete(Unit)
        runCurrent()
        assertEquals(0L, testScheduler.currentTime)
        assertTrue(nutritionRepository.getAllMealTemplates().first().isEmpty())
        assertTrue(viewModel.mealTemplateDeletion.value.pendingTemplateIds.isEmpty())
        assertNull(viewModel.mealTemplateDeletion.value.failedTemplate)
    }

    @Test
    fun `failed deletion preserves template and logged nutrition and can be retried`() = runTest {
        val template = meal(1)
        nutritionRepository.insertMealTemplate(template)
        viewModel.logMealTemplate(template)
        runCurrent()
        val loggedMeals = nutritionRepository.getAllNutritionLogs().first()
        val controlled = useControlledDeletion()
        controlled.failure = IllegalStateException("Database unavailable")
        controlled.release.complete(Unit)

        viewModel.deleteMealTemplate(template)
        runCurrent()
        assertEquals(template, viewModel.mealTemplateDeletion.value.failedTemplate)
        assertTrue(viewModel.mealTemplateDeletion.value.pendingTemplateIds.isEmpty())
        assertEquals(listOf(template), nutritionRepository.getAllMealTemplates().first())
        assertEquals(loggedMeals, nutritionRepository.getAllNutritionLogs().first())
        viewModel.dismissMealTemplateDeletionError()
        assertNull(viewModel.mealTemplateDeletion.value.failedTemplate)

        controlled.failure = null
        viewModel.deleteMealTemplate(template)
        runCurrent()
        assertEquals(2, controlled.startedTemplates.size)
        assertTrue(nutritionRepository.getAllMealTemplates().first().isEmpty())
        assertEquals(loggedMeals, nutritionRepository.getAllNutritionLogs().first())
        assertNull(viewModel.mealTemplateDeletion.value.failedTemplate)
    }

    @Test
    fun `different meal templates can be deleted concurrently`() = runTest {
        val first = meal(1)
        val second = meal(2)
        val retained = meal(3)
        listOf(first, second, retained).forEach { nutritionRepository.insertMealTemplate(it) }
        val controlled = useControlledDeletion()
        viewModel.deleteMealTemplate(first)
        viewModel.deleteMealTemplate(second)
        viewModel.deleteMealTemplate(first)
        runCurrent()

        assertEquals(setOf(first.id, second.id), viewModel.mealTemplateDeletion.value.pendingTemplateIds)
        assertEquals(listOf(first, second), controlled.startedTemplates)
        controlled.release.complete(Unit)
        runCurrent()
        assertTrue(viewModel.mealTemplateDeletion.value.pendingTemplateIds.isEmpty())
        assertEquals(listOf(retained), nutritionRepository.getAllMealTemplates().first())
    }

    @Test
    fun `leaving nutrition screen does not cancel an accepted deletion`() = runTest {
        val template = meal(1)
        nutritionRepository.insertMealTemplate(template)
        val controlled = useControlledDeletion()
        val screenSubscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        viewModel.deleteMealTemplate(template)
        runCurrent()
        screenSubscription.cancel()
        controlled.release.complete(Unit)
        runCurrent()

        assertTrue(nutritionRepository.getAllMealTemplates().first().isEmpty())
        assertTrue(viewModel.mealTemplateDeletion.value.pendingTemplateIds.isEmpty())
        assertNull(viewModel.mealTemplateDeletion.value.failedTemplate)
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
