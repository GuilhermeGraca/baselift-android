package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.IUserRepository
import com.example.baselift.ViewModel.nutrition.NutritionViewModel
import com.example.baselift.fakes.FakeNutritionRepository
import com.example.baselift.fakes.FakeUserRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionSubscriptionsTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val repository = FakeNutritionRepository()
    private val users = FakeUserRepository()
    private val store = ViewModelStore()
    private lateinit var vm: NutritionViewModel
    private class Counter { var starts = 0; var active = 0; var emissions = 0 }
    private val logs = Counter()
    private val templates = Counter()
    private val targets = Counter()
    private var dayRequests = 0
    private val releaseDelete = CompletableDeferred<Unit>()
    private var deleteFailure: Exception? = null
    private var deleteCalls = 0
    private val meal = MealTemplateEntity(1, "Lunch", "restaurant", 600, 40, 70, 10)

    private fun <T> tracked(source: Flow<T>, counter: Counter): Flow<T> = flow {
        counter.starts++; counter.active++
        try { source.collect { counter.emissions++; emit(it) } }
        finally { counter.active-- }
    }

    private val source = object : INutritionRepository by repository {
        override fun getTodayLogs(): Flow<List<NutritionLogEntity>> {
            dayRequests++
            return tracked(repository.getTodayLogs(), logs)
        }
        override fun getAllMealTemplates() = tracked(repository.getAllMealTemplates(), templates)
        override suspend fun deleteMealTemplate(template: MealTemplateEntity) {
            deleteCalls++
            releaseDelete.await()
            deleteFailure?.let { throw it }
            repository.deleteMealTemplate(template)
        }
    }
    private val userSource = object : IUserRepository by users {
        override fun getUser() = tracked(users.getUser(), targets)
    }

    @After fun tearDown() { store.clear() }
    private fun start() {
        vm = NutritionViewModel(source, userSource)
        store.put("nutrition", vm)
    }
    private fun TestScope.watch() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    private fun assertActive(expected: Int) {
        assertEquals(expected, logs.active)
        assertEquals(expected, templates.active)
        assertEquals(expected, targets.active)
    }

    @Test fun `deletion feedback subscribers alone do not start presentation queries`() = runTest {
        start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.mealTemplateDeletion.collect {} }
        vm.refreshDate(); runCurrent()
        assertActive(0)
        assertEquals(0, dayRequests)
        assertEquals(0, templates.starts)
        assertEquals(0, targets.starts)
    }

    @Test fun `shared presentation stops one second after the final collector and retains its snapshot`() = runTest {
        repository.insertMealTemplate(meal)
        start()
        val first = watch()
        val second = watch(); runCurrent()
        val cached = vm.uiState.value
        first.cancel(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertActive(1)
        assertEquals(1, dayRequests)
        second.cancel(); runCurrent()
        advanceTimeBy(999); runCurrent()
        assertActive(1)
        advanceTimeBy(1); runCurrent()
        assertActive(0)
        assertSame(cached, vm.uiState.value)
    }

    @Test fun `brief absence reuses existing subscriptions and disposal does not wait for the grace period`() = runTest {
        start()
        val first = watch(); runCurrent()
        first.cancel(); runCurrent()
        advanceTimeBy(500); runCurrent()
        watch(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertActive(1)
        assertEquals(1, dayRequests)
        assertEquals(1, templates.starts)
        assertEquals(1, targets.starts)
        store.clear(); runCurrent()
        assertActive(0)
    }

    @Test fun `offscreen updates stay unprocessed and return reopens the daily query with fresh targets logs and templates`() = runTest {
        repository.insertMealTemplate(meal)
        start()
        var subscription = watch(); runCurrent()
        val cached = vm.uiState.value
        val counts = listOf(logs.emissions, templates.emissions, targets.emissions)
        subscription.cancel(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        users.saveUser(UserEntity(dailyCaloriesGoal = 3000))
        vm.addQuickLog(100, isCaloriesOnly = true)
        vm.logMealTemplate(meal)
        vm.saveMealTemplate(meal.copy(name = "Updated"))
        vm.refreshDate(); runCurrent()
        assertActive(0)
        assertEquals(1, dayRequests)
        assertEquals(counts, listOf(logs.emissions, templates.emissions, targets.emissions))
        assertSame(cached, vm.uiState.value)
        subscription = watch(); runCurrent()
        assertEquals(2, dayRequests)
        assertEquals(3000, vm.uiState.value.targetCalories)
        assertEquals(700, vm.uiState.value.consumedCalories)
        assertEquals("Updated", vm.uiState.value.mealTemplates.single().name)
        assertFalse(vm.uiState.value.isLoading)
        subscription.cancel(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        vm.deleteAllNutrition(); runCurrent()
        watch(); runCurrent()
        assertTrue(vm.uiState.value.todayLogs.isEmpty())
        assertTrue(vm.uiState.value.mealTemplates.isEmpty())
        assertEquals(0, vm.uiState.value.consumedCalories)
    }

    @Test fun `accepted template deletion survives presentation detachment and pending feedback cannot keep queries alive`() = runTest {
        repository.insertMealTemplate(meal)
        start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.mealTemplateDeletion.collect {} }
        val subscription = watch(); runCurrent()
        vm.deleteMealTemplate(meal); runCurrent()
        subscription.cancel(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertActive(0)
        vm.deleteMealTemplate(meal); runCurrent()
        assertEquals(1, deleteCalls)
        assertEquals(setOf(1), vm.mealTemplateDeletion.value.pendingTemplateIds)
        releaseDelete.complete(Unit); runCurrent()
        assertTrue(repository.getAllMealTemplates().first().isEmpty())
        assertTrue(vm.mealTemplateDeletion.value.pendingTemplateIds.isEmpty())
        assertActive(0)
        watch(); runCurrent()
        assertTrue(vm.uiState.value.mealTemplates.isEmpty())
    }

    @Test fun `failed offscreen deletion retains feedback and can retry independently of presentation`() = runTest {
        repository.insertMealTemplate(meal)
        start()
        val subscription = watch(); runCurrent()
        vm.deleteMealTemplate(meal); runCurrent()
        subscription.cancel(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        deleteFailure = IllegalStateException("injected")
        releaseDelete.complete(Unit); runCurrent()
        assertEquals(meal, vm.mealTemplateDeletion.value.failedTemplate)
        assertEquals(listOf(meal), repository.getAllMealTemplates().first())
        assertActive(0)
        deleteFailure = null
        vm.deleteMealTemplate(meal); runCurrent()
        assertEquals(2, deleteCalls)
        assertNull(vm.mealTemplateDeletion.value.failedTemplate)
        assertActive(0)
        watch(); runCurrent()
        assertTrue(vm.uiState.value.mealTemplates.isEmpty())
    }
}
