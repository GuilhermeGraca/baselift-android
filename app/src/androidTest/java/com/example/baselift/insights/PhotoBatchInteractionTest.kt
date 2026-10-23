package com.example.baselift.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.repository.ProgressRepository
import com.example.baselift.R
import com.example.baselift.View.insights.components.PhotoImportFeedback
import com.example.baselift.View.insights.components.PhotoWeightIndex
import com.example.baselift.View.insights.components.VisualDiarySection
import com.example.baselift.ViewModel.progress.PhotoImportResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PhotoBatchInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var database: AppDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val release = CountDownLatch(1)
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    @After fun tearDown() { release.countDown(); scope.cancel(); database?.close() }

    @Test fun oneCommittedBatchPublishesNoIntermediateDiaryListsAndScrollsToTheFinalPhoto() {
        val armed = AtomicBoolean(false)
        val inserts = AtomicInteger()
        val insideTransaction = CountDownLatch(1)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (armed.get() && sql.startsWith("INSERT", ignoreCase = true) && sql.contains("photo_logs") && inserts.incrementAndGet() == 2) {
                    insideTransaction.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "batch test gate timed out" }
                }
            }, Executor { it.run() }).build().also { database = it }
        val repository = ProgressRepository(db.weightLogDao(), db.photoLogDao())
        runBlocking { repository.insertPhotoLogs(listOf("old-a", "old-b"), 1000L) }
        val seen = CopyOnWriteArrayList<Int>()
        compose.setContent { MaterialTheme {
            val photos by repository.allPhotoLogs.collectAsState(initial = emptyList())
            SideEffect { if (seen.lastOrNull() != photos.size) seen += photos.size }
            VisualDiarySection(photos, PhotoWeightIndex(emptyList(), TimeZone.getTimeZone("UTC")), {}, {})
        } }
        compose.waitUntil(5_000) { seen.lastOrNull() == 2 }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        armed.set(true)
        val write = scope.async { repository.insertPhotoLogs((1..40).map { "new-$it" }, 2000L) }
        assertTrue(insideTransaction.await(5, TimeUnit.SECONDS))
        compose.waitForIdle()
        assertEquals(2, seen.last())
        assertFalse(seen.any { it in 3..41 })
        release.countDown()
        runBlocking { withTimeout(5_000) { write.await() } }
        compose.waitUntil(5_000) { seen.lastOrNull() == 42 }
        compose.onNodeWithText(text(R.string.insights_latest)).assertIsDisplayed()
        val scroll = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue("Diary should settle at the completed batch's final item", scroll >= 40f)
        assertEquals(listOf(0, 2, 42), seen.toList())
        val rows = runBlocking { repository.allPhotoLogs.first() }
        assertEquals(42, rows.size); assertEquals(40, rows.count { it.timestamp == 2000L })
        assertEquals(42, rows.map { it.id }.toSet().size)
    }

    @Test fun sqliteFailureRollsBackTheCompleteNewBatchAndLeavesExistingRowsAndOtherData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .build().also { database = it }
        val repository = ProgressRepository(db.weightLogDao(), db.photoLogDao())
        repository.insertPhotoLog("kept", 1000L); repository.insertWeightLog(80f, 1000L)
        val original = repository.allPhotoLogs.first()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_batch_photo BEFORE INSERT ON photo_logs WHEN NEW.photoUri = 'reject' BEGIN SELECT RAISE(ABORT, 'injected batch failure'); END")
        var failed = false
        try { repository.insertPhotoLogs(listOf("first", "reject", "last"), 2000L) }
        catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(original, repository.allPhotoLogs.first())
        assertEquals(1, repository.allWeightLogs.first().size)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_batch_photo")
        repository.insertPhotoLogs(listOf("first", "reject", "last", "first"), 2000L)
        val rows = repository.allPhotoLogs.first()
        assertEquals(5, rows.size); assertEquals(2, rows.count { it.photoUri == "first" })
        assertEquals(original.single(), rows.single { it.photoUri == "kept" })
    }

    @Test fun importFeedbackReportsCountsUsesScopedRetryAndDismissAndPreventsAnotherPickerWhileBusy() {
        val result = mutableStateOf<PhotoImportResult?>(PhotoImportResult(7, 2, 1))
        val importing = mutableStateOf(true)
        var retried: Long? = null; var dismissed: Long? = null; var picks = 0
        compose.setContent { MaterialTheme { Column {
            VisualDiarySection(emptyList(), PhotoWeightIndex(emptyList(), TimeZone.getTimeZone("UTC")), {},
                onAddPhoto = { picks++ }, isImporting = importing.value)
            PhotoImportFeedback(result.value, onRetry = { retried = it; result.value = null },
                onDismiss = { dismissed = it; result.value = null })
        } } }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.insights_photo_import_result, 2, 1)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.insights_photo_import_retry)).performClick()
        assertEquals(7L, retried)
        compose.onNodeWithText(text(R.string.insights_photo_importing)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Add Photo").assertDoesNotExist()
        compose.runOnIdle { result.value = PhotoImportResult(8, 0, 2, wasCancelled = true) }
        compose.onNodeWithText(text(R.string.insights_photo_import_stopped)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.insights_close)).performClick()
        assertEquals(8L, dismissed)
        compose.runOnIdle { importing.value = false }
        compose.onNodeWithContentDescription("Add Photo").performClick()
        assertEquals(1, picks)
    }
}
