package com.example.baselift.insights

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.IProgressRepository
import com.example.baselift.Model.repository.ProgressRepository
import com.example.baselift.Model.repository.UserRepository
import com.example.baselift.ViewModel.progress.ProgressViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ProgressSubscriptionsTest {
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var database: AppDatabase? = null
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    @After fun tearDown() {
        main { store.clear(); scope.cancel() }
        database?.close()
    }

    @Test fun realRoomHistoriesStopWhileHeaderRemainsLiveAndReturnWithFreshOrderedData() {
        val weightQueries = AtomicInteger()
        val photoQueries = AtomicInteger()
        val weightCollectors = AtomicInteger()
        val photoCollectors = AtomicInteger()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.startsWith("SELECT", ignoreCase = true)) {
                    if (sql.contains("FROM weight_logs", ignoreCase = true)) weightQueries.incrementAndGet()
                    if (sql.contains("FROM photo_logs", ignoreCase = true)) photoQueries.incrementAndGet()
                }
            }, Executor { it.run() }).build()
        database = db
        val real = ProgressRepository(db.weightLogDao(), db.photoLogDao())
        val users = UserRepository(db.userDao())
        fun <T> tracked(rows: Flow<T>, collectors: AtomicInteger): Flow<T> = flow {
            collectors.incrementAndGet()
            try { emitAll(rows) } finally { collectors.decrementAndGet() }
        }
        val source = object : IProgressRepository by real {
            override val allWeightLogs = tracked(real.allWeightLogs, weightCollectors)
            override val allPhotoLogs = tracked(real.allPhotoLogs, photoCollectors)
        }
        runBlocking {
            real.insertWeightLog(80f, 1_000L)
            real.insertPhotoLog("photo-a", 1_000L)
            users.saveUser(UserEntity(name = "Before"))
        }
        lateinit var vm: ProgressViewModel
        main {
            vm = ProgressViewModel(source, users)
            store.put("progress", vm)
            scope.launch { vm.user.collect {} } // Same global profile demand as the app header.
        }
        await { vm.user.value?.name == "Before" }
        assertEquals(0, weightQueries.get())
        assertEquals(0, photoQueries.get())

        var watchers = emptyList<Job>()
        fun watchHistories() = main {
            watchers = listOf(scope.launch { vm.weightLogs.collect {} }, scope.launch { vm.photoLogs.collect {} })
        }
        watchHistories()
        await { vm.weightLogs.value.size == 1 && vm.photoLogs.value.size == 1 }
        val cachedWeights = vm.weightLogs.value
        val cachedPhotos = vm.photoLogs.value
        main { watchers.forEach { it.cancel() } }
        await { weightCollectors.get() == 0 && photoCollectors.get() == 0 }
        val pausedWeightQueries = weightQueries.get()
        val pausedPhotoQueries = photoQueries.get()
        assertTrue(pausedWeightQueries > 0)
        assertTrue(pausedPhotoQueries > 0)

        runBlocking {
            real.deleteWeightLog(cachedWeights.single())
            real.deletePhotoLog(cachedPhotos.single())
            real.insertWeightLog(83f, 3_000L)
            real.insertWeightLog(82f, 2_000L)
            real.insertPhotoLog("photo-b", 3_000L)
            real.insertPhotoLog("photo-c", 2_000L)
            users.saveUser(UserEntity(name = "After"))
        }
        await { vm.user.value?.name == "After" }
        assertEquals(pausedWeightQueries, weightQueries.get())
        assertEquals(pausedPhotoQueries, photoQueries.get())
        assertSame(cachedWeights, vm.weightLogs.value)
        assertSame(cachedPhotos, vm.photoLogs.value)

        watchHistories()
        await { vm.weightLogs.value.size == 2 && vm.photoLogs.value.size == 2 }
        assertEquals(listOf(82f, 83f), vm.weightLogs.value.map { it.weightValue })
        assertEquals(listOf("photo-b", "photo-c"), vm.photoLogs.value.map { it.photoUri })
        assertTrue(weightQueries.get() > pausedWeightQueries)
        assertTrue(photoQueries.get() > pausedPhotoQueries)
        assertEquals(1, weightCollectors.get())
        assertEquals(1, photoCollectors.get())
    }
}
