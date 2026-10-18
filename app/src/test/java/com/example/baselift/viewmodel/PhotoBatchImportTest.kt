package com.example.baselift.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.repository.IProgressRepository
import com.example.baselift.ViewModel.progress.ProgressViewModel
import com.example.baselift.fakes.FakeProgressRepository
import com.example.baselift.fakes.FakeUserRepository
import com.example.baselift.helpers.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoBatchImportTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val store = ViewModelStore()
    private val progress = FakeProgressRepository()
    private val context = mockk<Context>()
    private val application = mockk<Context>()
    private val resolver = mockk<ContentResolver>(relaxed = true)
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private lateinit var vm: ProgressViewModel
    private val first = uri("first")
    private val second = uri("second")
    private val third = uri("third")
    private fun uri(value: String): Uri = mockk<Uri>().also { every { it.toString() } returns "content://test/$value" }
    private val selected get() = listOf(first, second, third)

    private class Source(private val source: IProgressRepository) : IProgressRepository by source {
        val calls = mutableListOf<Pair<List<String>, Long>>()
        var beforeWrite: suspend () -> Unit = {}
        var afterWrite: suspend () -> Unit = {}
        var historyStarts = 0
        override val allPhotoLogs = flow { historyStarts++; emitAll(source.allPhotoLogs) }
        override suspend fun insertPhotoLogs(photoUris: List<String>, timestamp: Long) {
            calls += photoUris.toList() to timestamp
            beforeWrite(); source.insertPhotoLogs(photoUris, timestamp); afterWrite()
        }
        override suspend fun insertPhotoLog(photoUri: String, timestamp: Long) { error("import must use one batch") }
    }
    private val source = Source(progress)
    private fun start() {
        every { context.applicationContext } returns application
        every { application.contentResolver } returns resolver
        vm = ProgressViewModel(source, FakeUserRepository(), main.testDispatcher)
        store.put("progress", vm)
    }
    private fun result() = vm.photoImport.value.result!!
    private suspend fun stored() = progress.allPhotoLogs.first()
    @After fun tearDown() { store.clear() }

    @Test fun `acceptance synchronously blocks duplicate submissions until one complete batch is saved`() = runTest {
        start(); val release = CompletableDeferred<Unit>(); source.beforeWrite = { release.await() }
        assertTrue(vm.addPhotoLogs(context, selected, 1234L))
        assertTrue(vm.photoImport.value.isImporting)
        repeat(4) { assertFalse(vm.addPhotoLogs(context, selected, 1234L)) }
        runCurrent(); assertEquals(1, source.calls.size); assertTrue(stored().isEmpty())
        assertEquals(selected.map { it.toString() } to 1234L, source.calls.single())
        release.complete(Unit); runCurrent()
        assertEquals(3, stored().size); assertEquals(3, result().savedCount); assertEquals(0, result().failedCount)
        assertFalse(vm.photoImport.value.isImporting)
    }

    @Test fun `permission failures form one readable subset and retry only unsaved photos with the same date`() = runTest {
        start(); every { resolver.takePersistableUriPermission(second, flags) } throws SecurityException("denied")
        vm.addPhotoLogs(context, selected, 1234L); runCurrent()
        assertEquals(listOf(first, third).map { it.toString() }, source.calls.single().first)
        assertEquals(2, result().savedCount); assertEquals(1, result().failedCount)
        val savedIds = stored().map { it.id }
        every { resolver.takePersistableUriPermission(second, flags) } just Runs
        vm.retryPhotoImport(context, result().id); runCurrent()
        assertEquals(listOf(second.toString()) to 1234L, source.calls.last())
        assertEquals(3, stored().size); assertTrue(stored().map { it.id }.containsAll(savedIds))
        verify(exactly = 1) { resolver.takePersistableUriPermission(first, flags) }
        verify(exactly = 1) { resolver.takePersistableUriPermission(third, flags) }
        assertEquals(1, result().savedCount); assertEquals(0, result().failedCount)
    }

    @Test fun `all permission failures skip the database and expose every unsaved photo`() = runTest {
        start(); every { resolver.takePersistableUriPermission(any(), flags) } throws SecurityException("denied")
        vm.addPhotoLogs(context, selected, 1234L); runCurrent()
        assertTrue(source.calls.isEmpty()); assertTrue(stored().isEmpty())
        assertEquals(0, result().savedCount); assertEquals(3, result().failedCount)
        assertFalse(result().wasCancelled); assertFalse(vm.photoImport.value.isImporting)
    }

    @Test fun `atomic persistence failure keeps existing history and retries the complete unsaved selection`() = runTest {
        progress.insertPhotoLog("old", 1L); start(); source.beforeWrite = { error("database failed") }
        vm.addPhotoLogs(context, selected, 1234L); runCurrent()
        assertEquals(listOf("old"), stored().map { it.photoUri })
        assertEquals(0, result().savedCount); assertEquals(3, result().failedCount)
        source.beforeWrite = {}; vm.retryPhotoImport(context, result().id); runCurrent()
        assertEquals(4, stored().size); assertEquals(2, source.calls.size)
        assertEquals(selected.map { it.toString() }, source.calls.last().first)
    }

    @Test fun `repeated retry taps submit one command and obsolete dismissals cannot erase a new result`() = runTest {
        start(); every { resolver.takePersistableUriPermission(second, flags) } throws SecurityException("denied")
        vm.addPhotoLogs(context, selected, 1234L); runCurrent(); val old = result().id
        every { resolver.takePersistableUriPermission(second, flags) } just Runs
        val release = CompletableDeferred<Unit>(); source.beforeWrite = { release.await() }
        repeat(5) { vm.retryPhotoImport(context, old) }; runCurrent()
        assertEquals(2, source.calls.size); assertTrue(vm.photoImport.value.isImporting)
        release.complete(Unit); runCurrent(); val current = result()
        vm.dismissPhotoImportResult(old); vm.retryPhotoImport(context, old); runCurrent()
        assertEquals(current, result()); assertEquals(2, source.calls.size); assertEquals(3, stored().size)
    }

    @Test fun `dismissal releases retry data and an empty selection does not change feedback`() = runTest {
        start(); every { resolver.takePersistableUriPermission(first, flags) } throws SecurityException("denied")
        vm.addPhotoLogs(context, listOf(first), 1234L); runCurrent(); val failure = result()
        assertFalse(vm.addPhotoLogs(context, emptyList(), 1234L)); assertEquals(failure, result())
        vm.dismissPhotoImportResult(failure.id); vm.retryPhotoImport(context, failure.id); runCurrent()
        assertNull(vm.photoImport.value.result); assertTrue(source.calls.isEmpty())
    }

    @Test fun `a new selection replaces older retry ownership without duplicating saved photos`() = runTest {
        start(); every { resolver.takePersistableUriPermission(first, flags) } throws SecurityException("denied")
        vm.addPhotoLogs(context, listOf(first), 1234L); runCurrent(); val old = result().id
        vm.addPhotoLogs(context, listOf(second), 5678L); runCurrent()
        vm.retryPhotoImport(context, old); runCurrent()
        assertEquals(listOf(second.toString()), stored().map { it.photoUri }); assertEquals(1, source.calls.size)
    }

    @Test fun `cancelling permission preparation saves none of that selection and preserves older batches`() = runTest {
        progress.insertPhotoLog("old", 1L); start()
        every { resolver.takePersistableUriPermission(second, flags) } throws CancellationException("cancelled")
        vm.addPhotoLogs(context, selected, 1234L); runCurrent()
        assertTrue(result().wasCancelled); assertEquals(3, result().failedCount); assertEquals(0, result().savedCount)
        assertTrue(source.calls.isEmpty()); assertEquals(listOf("old"), stored().map { it.photoUri })
        verify(exactly = 0) { resolver.takePersistableUriPermission(third, flags) }
        every { resolver.takePersistableUriPermission(second, flags) } just Runs
        vm.retryPhotoImport(context, result().id); runCurrent()
        assertEquals(4, stored().size); assertFalse(result().wasCancelled)
    }

    @Test fun `disposal after commit acceptance finishes the atomic write and records success`() = runTest {
        start(); val release = CompletableDeferred<Unit>(); source.beforeWrite = { release.await() }
        vm.addPhotoLogs(context, selected, 1234L); runCurrent()
        assertEquals(1, source.calls.size); store.clear(); runCurrent()
        assertTrue(stored().isEmpty()); release.complete(Unit); runCurrent()
        assertEquals(3, stored().size); assertEquals(3, result().savedCount)
        assertEquals(0, result().failedCount); assertFalse(result().wasCancelled)
    }

    @Test fun `disposal after database success cannot turn a committed batch into a retryable failure`() = runTest {
        start(); val acknowledgement = CompletableDeferred<Unit>(); source.afterWrite = { acknowledgement.await() }
        vm.addPhotoLogs(context, selected, 1234L); runCurrent()
        assertEquals(3, stored().size); assertTrue(vm.photoImport.value.isImporting)
        store.clear(); acknowledgement.complete(Unit); runCurrent()
        assertEquals(3, result().savedCount); assertEquals(0, result().failedCount); assertFalse(result().wasCancelled)
    }

    @Test fun `feedback observers alone never start photo history and data publishes in one list change`() = runTest {
        start(); val snapshots = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.photoImport.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            progress.allPhotoLogs.collect { snapshots += it.map { row -> row.photoUri } }
        }
        vm.addPhotoLogs(context, selected, 1234L); runCurrent()
        assertEquals(0, source.historyStarts)
        assertEquals(listOf(emptyList(), selected.map { it.toString() }), snapshots)
        assertEquals(3, result().savedCount)
    }
}
