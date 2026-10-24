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

    private class ControlledInsertRepository(
        private val source: INutritionRepository
    ) : INutritionRepository by source {
        val startedLogs = mutableListOf<NutritionLogEntity>()
        val release = CompletableDeferred<Unit>()
        var failure: Exception? = null

        override suspend fun insertNutritionLog(log: NutritionLogEntity) {
            startedLogs += log
            release.await()
            failure?.let { throw it }
            source.insertNutritionLog(log)
        }
    }

    private class ControlledTemplateRepository(
        private val source: INutritionRepository
    ) : INutritionRepository by source {
        val startedTemplates = mutableListOf<MealTemplateEntity>()
        val release = CompletableDeferred<Unit>()
        var failure: Exception? = null

        override suspend fun insertMealTemplate(template: MealTemplateEntity) {
            startedTemplates += template
            release.await()
            failure?.let { throw it }
            source.insertMealTemplate(template)
        }
    }

    private fun useControlledTemplateSave(): ControlledTemplateRepository {
        val controlled = ControlledTemplateRepository(nutritionRepository)
        viewModel = NutritionViewModel(controlled, userRepository)
        viewModelStore.put("nutrition", viewModel)
        return controlled
    }

    private fun useControlledInsert(clock: () -> Long = System::currentTimeMillis): ControlledInsertRepository {
        val controlled = ControlledInsertRepository(nutritionRepository)
        viewModel = NutritionViewModel(controlled, userRepository, clock)
        viewModelStore.put("nutrition", viewModel)
        return controlled
    }

    private fun useControlledDeletion(): ControlledDeletionRepository {
        val controlled = ControlledDeletionRepository(nutritionRepository)
        viewModel = NutritionViewModel(controlled, userRepository)
        viewModelStore.put("nutrition", viewModel)
        return controlled
    }

    private fun meal(id: Int) = MealTemplateEntity(
        id = id, name = "Meal $id", iconName = "restaurant",
        calories = 600, protein = 40.0, carbs = 70.0, fats = 10.0
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
        assertEquals(0.0, state.consumedProtein, 0.000001)
        assertEquals(0.0, state.consumedCarbs, 0.000001)
        assertEquals(0.0, state.consumedFats, 0.000001)
        
        assertTrue(state.todayLogs.isEmpty())
        assertTrue(state.mealTemplates.isEmpty())
        assertFalse(state.isLoading)
        job.cancel()
    }

    @Test
    fun `addQuickLog inserts valid log into repository and updates state`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addQuickLog(calories = 500, protein = 30.0, carbs = 50.0, fats = 20.0, isCaloriesOnly = false)
        
        runCurrent()
        
        val logs = nutritionRepository.getAllNutritionLogs().first()
        assertEquals(1, logs.size)
        assertEquals("Detailed Macros", logs[0].name)
        assertEquals(500, logs[0].calories)
        
        val state = viewModel.uiState.value
        assertEquals(500, state.consumedCalories)
        assertEquals(30.0, state.consumedProtein, 0.000001)
        assertEquals(50.0, state.consumedCarbs, 0.000001)
        assertEquals(20.0, state.consumedFats, 0.000001)
        job.cancel()
    }

    @Test
    fun `addQuickLog with zero macros does nothing`() = runTest {
        viewModel.addQuickLog(calories = 0, protein = 0.0, carbs = 0.0, fats = 0.0, isCaloriesOnly = true)
        
        runCurrent()
        
        val logs = nutritionRepository.getAllNutritionLogs().first()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `quick log scales by consumed grams and ignores hidden macros in calories only mode`() = runTest {
        val detailedId = viewModel.addQuickLog(200, 10.0, 20.0, 5.0,
            isCaloriesOnly = false, isPer100g = true, grams = 150.0)
        runCurrent()
        assertEquals(detailedId, viewModel.quickLogSubmission.value.completedRequestId)
        val detailed = nutritionRepository.getAllNutritionLogs().first().single()
        assertEquals(listOf(300, 15.0, 30.0, 7.5),
            listOf(detailed.calories, detailed.protein, detailed.carbs, detailed.fats))

        viewModel.addQuickLog(200, 99.0, 99.0, 99.0,
            isCaloriesOnly = true, isPer100g = true, grams = 125.0)
        runCurrent()
        val caloriesOnly = nutritionRepository.getAllNutritionLogs().first().single { it.name == "Quick Add" }
        assertEquals(listOf(250, 0.0, 0.0, 0.0),
            listOf(caloriesOnly.calories, caloriesOnly.protein, caloriesOnly.carbs, caloriesOnly.fats))
    }

    @Test
    fun `quick log rejects missing amount, rounded zero and overflow before inserting`() = runTest {
        assertNull(viewModel.addQuickLog(200, isCaloriesOnly = true, isPer100g = true))
        assertNull(viewModel.addQuickLog(200, isCaloriesOnly = true, isPer100g = true, grams = 0.0))
        assertNull(viewModel.addQuickLog(1, isCaloriesOnly = true, isPer100g = true, grams = 1.0))
        assertNull(viewModel.addQuickLog(Int.MAX_VALUE, isCaloriesOnly = true, isPer100g = true, grams = 101.0))
        runCurrent()
        assertTrue(nutritionRepository.getAllNutritionLogs().first().isEmpty())
        assertNull(viewModel.quickLogSubmission.value.pendingRequestId)
    }

    @Test
    fun `quick log reports failure and retries the same values without a phantom entry`() = runTest {
        val controlled = useControlledInsert()
        val firstRequest = viewModel.addQuickLog(200, 10.0, 20.0, 5.0,
            isCaloriesOnly = false, isPer100g = true, grams = 150.0)
        assertEquals(firstRequest, viewModel.quickLogSubmission.value.pendingRequestId)
        runCurrent()
        assertEquals(1, controlled.startedLogs.size)
        controlled.failure = IllegalStateException("Database unavailable")
        controlled.release.complete(Unit)
        runCurrent()
        assertEquals(firstRequest, viewModel.quickLogSubmission.value.failedRequestId)
        assertTrue(nutritionRepository.getAllNutritionLogs().first().isEmpty())

        controlled.failure = null
        val retryRequest = viewModel.addQuickLog(200, 10.0, 20.0, 5.0,
            isCaloriesOnly = false, isPer100g = true, grams = 150.0)
        runCurrent()
        assertEquals(retryRequest, viewModel.quickLogSubmission.value.completedRequestId)
        assertEquals(2, controlled.startedLogs.size)
        assertEquals(1, nutritionRepository.getAllNutritionLogs().first().size)
    }

    @Test
    fun `quick log accepted offscreen keeps its original timestamp and completes`() = runTest {
        var now = 1_000L
        val controlled = useControlledInsert { now }
        val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        val request = viewModel.addQuickLog(200,
            isCaloriesOnly = true, isPer100g = true, grams = 150.0)
        runCurrent()
        subscription.cancel()
        now = 100_000L
        controlled.release.complete(Unit)
        runCurrent()
        val stored = nutritionRepository.getAllNutritionLogs().first().single()
        assertEquals(1_000L, stored.timestamp)
        assertEquals(request, viewModel.quickLogSubmission.value.completedRequestId)
    }

    @Test
    fun `decimal quick log and templates preserve fractions in logs and daily totals`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addQuickLog(95, 2.5, 4.12, 0.04, isCaloriesOnly = false, isPer100g = true, grams = 12.5)
        runCurrent()
        val template = meal(1).copy(calories = 95, protein = 2.5, carbs = 4.12, fats = 0.04, isPer100g = true)
        viewModel.saveMealTemplate(template)
        runCurrent()
        val savedTemplate = nutritionRepository.getAllMealTemplates().first().single()
        assertEquals(template, savedTemplate)
        viewModel.logMealTemplate(savedTemplate, 12.5)
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals(2, state.todayLogs.size)
        assertEquals(24, state.consumedCalories)
        assertEquals(0.62, state.consumedProtein, 0.000001)
        assertEquals(1.04, state.consumedCarbs, 0.000001)
        assertEquals(0.02, state.consumedFats, 0.000001)
        state.todayLogs.forEach {
            assertEquals(0.31, it.protein, 0.0)
            assertEquals(0.52, it.carbs, 0.0)
            assertEquals(0.01, it.fats, 0.0)
        }
        viewModel.logMealTemplate(savedTemplate.copy(isPer100g = false))
        runCurrent()
        assertEquals(3.12, viewModel.uiState.value.consumedProtein, 0.000001)
    }

    @Test
    fun `non finite macros cannot be saved or logged from templates`() = runTest {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.01)) {
            val template = meal(1).copy(protein = invalid)
            assertNull(viewModel.saveMealTemplate(template))
            assertNull(viewModel.logMealTemplate(template))
            assertNull(viewModel.logMealTemplate(template.copy(isPer100g = true), 100.0))
        }
        runCurrent()
        assertTrue(nutritionRepository.getAllMealTemplates().first().isEmpty())
        assertTrue(nutritionRepository.getAllNutritionLogs().first().isEmpty())
    }

    @Test
    fun `deleteLog removes log from repository and state`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addQuickLog(calories = 500, protein = 30.0, carbs = 50.0, fats = 20.0, isCaloriesOnly = false)
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
            protein = 40.0,
            carbs = 70.0,
            fats = 10.0
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
    fun `per 100 g template needs grams and produces independent proportional logs`() = runTest {
        val template = meal(1).copy(calories = 200, protein = 10.0, carbs = 20.0, fats = 5.0, isPer100g = true)
        val saveRequest = viewModel.saveMealTemplate(template)
        runCurrent()
        assertEquals(saveRequest, viewModel.mealTemplateSave.value.completedRequestId)
        assertNull(viewModel.logMealTemplate(template))
        assertNull(viewModel.logMealTemplate(template, 0.0))
        assertNull(viewModel.logMealTemplate(template, Int.MAX_VALUE.toDouble()))
        runCurrent()
        assertTrue(nutritionRepository.getAllNutritionLogs().first().isEmpty())

        val firstRequest = viewModel.logMealTemplate(template, 50.0)
        runCurrent()
        assertEquals(firstRequest, viewModel.mealLogSubmission.value.completedRequestId)
        val secondRequest = viewModel.logMealTemplate(template, 150.0)
        runCurrent()
        assertEquals(secondRequest, viewModel.mealLogSubmission.value.completedRequestId)
        val logs = nutritionRepository.getAllNutritionLogs().first()
        assertEquals(2, logs.size)
        assertTrue(logs.any { listOf(it.calories, it.protein, it.carbs, it.fats) == listOf(100, 5.0, 10.0, 2.5) })
        assertTrue(logs.any { listOf(it.calories, it.protein, it.carbs, it.fats) == listOf(300, 15.0, 30.0, 7.5) })
        assertEquals(template, nutritionRepository.getAllMealTemplates().first().single())
    }

    @Test
    fun `per 100 g meal log failure leaves no entry and retry succeeds`() = runTest {
        val controlled = useControlledInsert()
        val template = meal(1).copy(isPer100g = true)
        val firstRequest = viewModel.logMealTemplate(template, 150.0)
        runCurrent()
        assertEquals(1, controlled.startedLogs.size)
        controlled.failure = IllegalStateException("Database unavailable")
        controlled.release.complete(Unit)
        runCurrent()
        assertEquals(firstRequest, viewModel.mealLogSubmission.value.failedRequestId)
        assertTrue(nutritionRepository.getAllNutritionLogs().first().isEmpty())

        controlled.failure = null
        val retry = viewModel.logMealTemplate(template, 150.0)
        runCurrent()
        assertEquals(retry, viewModel.mealLogSubmission.value.completedRequestId)
        assertEquals(2, controlled.startedLogs.size)
        assertEquals(1, nutritionRepository.getAllNutritionLogs().first().size)
    }

    @Test
    fun `per 100 g meal accepts one pending request and keeps its original time offscreen`() = runTest {
        var now = 1_000L
        val controlled = useControlledInsert { now }
        val template = meal(1).copy(isPer100g = true)
        val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        val request = viewModel.logMealTemplate(template, 150.0)
        runCurrent()
        assertEquals(request, viewModel.logMealTemplate(template, 150.0))
        runCurrent()
        assertEquals(1, controlled.startedLogs.size)
        subscription.cancel()
        now = 100_000L
        controlled.release.complete(Unit)
        runCurrent()
        assertEquals(1_000L, nutritionRepository.getAllNutritionLogs().first().single().timestamp)
        assertEquals(request, viewModel.mealLogSubmission.value.completedRequestId)
    }

    @Test
    fun `deleting a per 100 g template leaves its logged totals intact`() = runTest {
        val template = meal(1).copy(isPer100g = true)
        nutritionRepository.insertMealTemplate(template)
        viewModel.logMealTemplate(template, 150.0)
        runCurrent()
        val log = nutritionRepository.getAllNutritionLogs().first().single()
        viewModel.deleteMealTemplate(template)
        runCurrent()
        assertTrue(nutritionRepository.getAllMealTemplates().first().isEmpty())
        assertEquals(log, nutritionRepository.getAllNutritionLogs().first().single())
    }

    @Test
    fun `failed template save preserves reference for retry`() = runTest {
        val controlled = useControlledTemplateSave()
        val template = meal(1).copy(isPer100g = true)
        val firstRequest = viewModel.saveMealTemplate(template)
        runCurrent()
        assertEquals(firstRequest, viewModel.mealTemplateSave.value.pendingRequestId)
        controlled.failure = IllegalStateException("Database unavailable")
        controlled.release.complete(Unit)
        runCurrent()
        assertEquals(firstRequest, viewModel.mealTemplateSave.value.failedRequestId)
        assertTrue(nutritionRepository.getAllMealTemplates().first().isEmpty())

        controlled.failure = null
        val retry = viewModel.saveMealTemplate(template)
        runCurrent()
        assertEquals(retry, viewModel.mealTemplateSave.value.completedRequestId)
        assertEquals(listOf(template, template), controlled.startedTemplates)
        assertEquals(template, nutritionRepository.getAllMealTemplates().first().single())
    }

    @Test
    fun `deleteMealTemplate removes template`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val template = MealTemplateEntity(id = 1, name = "Test", iconName = "Fastfood", calories = 100, protein = 10.0, carbs = 10.0, fats = 10.0)
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
        viewModel.saveMealTemplate(MealTemplateEntity(name = "T", iconName = "Icon", calories = 100, protein = 10.0, carbs = 10.0, fats = 10.0))
        runCurrent()
        
        viewModel.deleteAllNutrition()
        runCurrent()
        
        assertTrue(viewModel.uiState.value.todayLogs.isEmpty())
        assertTrue(viewModel.uiState.value.mealTemplates.isEmpty())
        job.cancel()
    }
}
