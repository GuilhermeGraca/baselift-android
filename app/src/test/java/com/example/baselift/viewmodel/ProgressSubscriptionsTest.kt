package com.example.baselift.viewmodel

import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.repository.IProgressRepository
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.ViewModel.progress.ProgressViewModel
import com.example.baselift.fakes.FakeProgressRepository
import com.example.baselift.fakes.FakeUserRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressSubscriptionsTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val repository = FakeProgressRepository()
    private val users = FakeUserRepository()
    private val store = ViewModelStore()
    private lateinit var vm: ProgressViewModel

    private class Counter { var starts = 0; var active = 0; var emissions = 0 }
    private val weights = Counter()
    private val photos = Counter()
    private fun <T> track(source: Flow<T>, counter: Counter): Flow<T> = flow {
        counter.starts++; counter.active++
        try { source.collect { counter.emissions++; emit(it) } }
        finally { counter.active-- }
    }
    private val tracked = object : IProgressRepository by repository {
        override val allWeightLogs = track(repository.allWeightLogs, weights)
        override val allPhotoLogs = track(repository.allPhotoLogs, photos)
    }

    @After fun tearDown() { store.clear() }

    private fun initialize(source: IProgressRepository = tracked) {
        vm = ProgressViewModel(source, users)
        store.put("progress", vm)
    }

    private fun TestScope.watchHistories() = listOf(
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.weightLogs.collect {} },
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.photoLogs.collect {} }
    )

    @Test fun `profile header collection alone does not start history queries`() = runTest {
        initialize()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.user.collect {} }
        users.saveUser(UserEntity(name = "Before")); runCurrent()
        assertEquals("Before", vm.user.value?.name)
        users.saveUser(UserEntity(name = "After")); runCurrent()
        assertEquals("After", vm.user.value?.name)
        assertEquals(0, weights.starts)
        assertEquals(0, photos.starts)
    }

    @Test fun `last subscriber stops after one second and snapshots remain cached`() = runTest {
        repository.insertWeightLog(80f, 1L)
        repository.insertPhotoLog("photo-a", 1L)
        initialize()
        val subscriptions = watchHistories(); runCurrent()
        val lastWeights = vm.weightLogs.value
        val lastPhotos = vm.photoLogs.value
        subscriptions.forEach { it.cancel() }; runCurrent()
        advanceTimeBy(999); runCurrent()
        assertEquals(1, weights.active)
        assertEquals(1, photos.active)
        advanceTimeBy(1); runCurrent()
        assertEquals(0, weights.active)
        assertEquals(0, photos.active)
        assertSame(lastWeights, vm.weightLogs.value)
        assertSame(lastPhotos, vm.photoLogs.value)
    }

    @Test fun `brief tab gap reuses the existing subscriptions`() = runTest {
        initialize()
        val first = watchHistories(); runCurrent()
        first.forEach { it.cancel() }; runCurrent()
        advanceTimeBy(500); runCurrent()
        watchHistories(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, weights.starts)
        assertEquals(1, photos.starts)
        assertEquals(1, weights.active)
        assertEquals(1, photos.active)
    }

    @Test fun `weight and photo demand is independent and all consumers must leave`() = runTest {
        initialize()
        val both = watchHistories()
        val anotherWeight = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.weightLogs.collect {} }
        runCurrent()
        both.forEach { it.cancel() }; runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, weights.starts)
        assertEquals(1, weights.active)
        assertEquals(0, photos.active)
        anotherWeight.cancel(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0, weights.active)
    }

    @Test fun `offscreen updates do not process history and resume delivers fresh inserts deletions and resets`() = runTest {
        repository.insertWeightLog(80f, 1L)
        repository.insertPhotoLog("photo-a", 1L)
        initialize()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.user.collect {} }
        var subscriptions = watchHistories(); runCurrent()
        val oldWeights = vm.weightLogs.value
        val oldPhotos = vm.photoLogs.value
        subscriptions.forEach { it.cancel() }; runCurrent()
        advanceTimeBy(1_000); runCurrent()
        val weightEmissions = weights.emissions
        val photoEmissions = photos.emissions
        repository.deleteWeightLog(oldWeights.single())
        repository.deletePhotoLog(oldPhotos.single())
        repository.insertWeightLog(81f, 2L)
        repository.insertPhotoLog("photo-b", 2L)
        users.saveUser(UserEntity(name = "Updated header")); runCurrent()
        assertEquals(weightEmissions, weights.emissions)
        assertEquals(photoEmissions, photos.emissions)
        assertEquals("Updated header", vm.user.value?.name)
        assertSame(oldWeights, vm.weightLogs.value)
        assertSame(oldPhotos, vm.photoLogs.value)
        subscriptions = watchHistories(); runCurrent()
        assertEquals(81f, vm.weightLogs.value.single().weightValue, 0f)
        assertEquals("photo-b", vm.photoLogs.value.single().photoUri)
        assertEquals(2, weights.starts)
        assertEquals(2, photos.starts)
        subscriptions.forEach { it.cancel() }; runCurrent()
        advanceTimeBy(1_000); runCurrent()
        repository.clearProgressData(); runCurrent()
        assertEquals(1, vm.weightLogs.value.size)
        watchHistories(); runCurrent()
        assertTrue(vm.weightLogs.value.isEmpty())
        assertTrue(vm.photoLogs.value.isEmpty())
    }

    @Test fun `accepted weight write survives history detachment and ViewModel disposal stops streams immediately`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delayed = object : IProgressRepository by tracked {
            override suspend fun insertWeightLog(weight: Float, timestamp: Long) {
                started.complete(Unit)
                release.await()
                repository.insertWeightLog(weight, timestamp)
            }
        }
        initialize(delayed)
        val subscriptions = watchHistories(); runCurrent()
        vm.addWeightLog(82f, 1L); runCurrent()
        assertTrue(started.isCompleted)
        subscriptions.forEach { it.cancel() }; runCurrent()
        advanceTimeBy(1_000); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals(0, weights.active)
        assertEquals(82f, repository.allWeightLogs.first().single().weightValue, 0f)
        watchHistories(); runCurrent()
        assertEquals(82f, vm.weightLogs.value.single().weightValue, 0f)
        store.clear(); runCurrent()
        assertEquals(0, weights.active)
        assertEquals(0, photos.active)
    }
}
