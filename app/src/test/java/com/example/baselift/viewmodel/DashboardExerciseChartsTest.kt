package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.ViewModel.dashboard.DashboardViewModel
import com.example.baselift.ViewModel.dashboard.ExerciseCharts
import com.example.baselift.fakes.FakeNutritionRepository
import com.example.baselift.fakes.FakeWorkoutRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardExerciseChartsTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()

    @After fun teardown() = store.clear()

    private class ExerciseQueries : IWorkoutRepository by FakeWorkoutRepository() {
        val requestedIds = mutableListOf<Int>()
        val active = mutableMapOf<Int, Int>()
        private val results = mutableMapOf<Int, MutableSharedFlow<List<SetLogEntity>>>()
        var fail = false

        private fun result(id: Int) = results.getOrPut(id) {
            MutableSharedFlow<List<SetLogEntity>>(replay = 1).apply { tryEmit(emptyList()) }
        }

        suspend fun emit(id: Int, logs: List<SetLogEntity>) = result(id).emit(logs)

        override fun getCompletedSetLogsForExercise(exerciseId: Int): Flow<List<SetLogEntity>> {
            requestedIds += exerciseId
            if (fail) throw IllegalStateException("Database unavailable")
            return flow {
                active[exerciseId] = (active[exerciseId] ?: 0) + 1
                try { emitAll(result(exerciseId)) }
                finally { active[exerciseId] = active.getValue(exerciseId) - 1 }
            }
        }
    }

    private fun dashboard(repository: IWorkoutRepository) = DashboardViewModel(
        repository, FakeNutritionRepository(), main.testDispatcher
    ).also { store.put("dashboard", it) }

    private fun log(exercise: Int, weight: Float) = SetLogEntity(
        id = exercise, exerciseId = exercise, sessionId = 1, setNumber = 1,
        timestamp = 1_780_000_000_000, weight = weight, reps = 10, isCompleted = true
    )

    @Test
    fun `Dashboard and uncollected detail flows create no exercise queries`() = runTest {
        val repository = ExerciseQueries()
        val dashboard = dashboard(repository)
        dashboard.exerciseCharts(1)
        dashboard.exerciseCharts(2)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        assertFalse(dashboard.uiState.value.isLoading)
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(repository.active.isEmpty())
    }

    @Test
    fun `panels observe only their exercise stop on close and reopen with fresh history`() = runTest {
        val repository = ExerciseQueries()
        repository.emit(1, listOf(log(1, 100f)))
        repository.emit(2, listOf(log(2, 60f)))
        val dashboard = dashboard(repository)
        val firstStates = mutableListOf<ExerciseCharts>()
        val secondStates = mutableListOf<ExerciseCharts>()
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dashboard.exerciseCharts(1).collect { firstStates += it }
        }
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dashboard.exerciseCharts(2).collect { secondStates += it }
        }
        runCurrent()
        assertEquals(listOf(1, 2), repository.requestedIds)
        assertEquals(mapOf(1 to 1, 2 to 1), repository.active)
        assertTrue(firstStates.first().isLoading)
        assertEquals(100f, firstStates.last().maxWeightTrend.single().yValue, 0f)
        assertEquals(60f, secondStates.last().maxWeightTrend.single().yValue, 0f)

        val emissions = firstStates.size
        repository.emit(1, listOf(log(1, 100f))) // Equal Room invalidation result.
        runCurrent()
        assertEquals(emissions, firstStates.size)
        first.cancel()
        runCurrent()
        assertEquals(0, repository.active[1])
        repository.emit(1, listOf(log(1, 120f)))
        runCurrent()
        assertEquals(emissions, firstStates.size)
        assertEquals(60f, secondStates.last().maxWeightTrend.single().yValue, 0f)
        repository.emit(2, listOf(log(2, 70f)))
        runCurrent()
        assertEquals(70f, secondStates.last().maxWeightTrend.single().yValue, 0f)
        val reopened = dashboard.exerciseCharts(1).first { !it.isLoading }
        assertEquals(120f, reopened.maxWeightTrend.single().yValue, 0f)
        repository.emit(2, emptyList())
        runCurrent()
        assertTrue(secondStates.last().volumeTrend.isEmpty())
        assertFalse(secondStates.last().loadFailed)
        second.cancel()
        runCurrent()
        assertEquals(mapOf(1 to 0, 2 to 0), repository.active)
        assertTrue(firstStates.none { it.loadFailed })
    }

    @Test
    fun `failed history load offers a fresh collection for retry`() = runTest {
        val repository = ExerciseQueries().apply { fail = true }
        val dashboard = dashboard(repository)
        val failed = dashboard.exerciseCharts(1).first { it.loadFailed }
        assertFalse(failed.isLoading)
        repository.fail = false
        repository.emit(1, listOf(log(1, 100f)))
        val retried = dashboard.exerciseCharts(1).first { !it.isLoading }
        assertFalse(retried.loadFailed)
        assertEquals(1_000f, retried.volumeTrend.single().yValue, 0f)
        assertEquals(listOf(1, 1), repository.requestedIds)
    }

    @Test
    fun `exercise history preparation runs on the analytics dispatcher`() = runTest {
        val reads = ConcurrentLinkedQueue<Thread>()
        val logs = object : AbstractList<SetLogEntity>() {
            override val size = 1
            override fun get(index: Int): SetLogEntity { reads += Thread.currentThread(); return log(1, 100f) }
        }
        val repository = object : IWorkoutRepository by FakeWorkoutRepository() {
            override fun getCompletedSetLogsForExercise(exerciseId: Int) = flowOf(logs)
        }
        val thread = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "exercise-chart-test").also { thread.set(it) }
        }.asCoroutineDispatcher().use { dispatcher ->
            val dashboard = DashboardViewModel(repository, FakeNutritionRepository(), dispatcher)
            val localStore = ViewModelStore().apply { put("dashboard", dashboard) }
            try {
                val chart = dashboard.exerciseCharts(1).first { !it.isLoading }
                assertEquals(1_000f, chart.volumeTrend.single().yValue, 0f)
                assertTrue(reads.isNotEmpty())
                assertTrue(reads.all { it === thread.get() })
            } finally { localStore.clear() }
        }
    }
}
