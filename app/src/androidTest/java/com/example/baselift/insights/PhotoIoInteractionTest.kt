package com.example.baselift.insights

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.ProgressRepository
import com.example.baselift.Model.repository.UserRepository
import com.example.baselift.ViewModel.progress.ProgressViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class PhotoIoInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var database: AppDatabase? = null
    private var provider: BlockingResolverContext? = null
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    // Delays the resolver boundary, then uses the real framework resolver. No URI grants are fabricated.
    private class BlockingResolverContext(base: Context) : ContextWrapper(base) {
        val release = CountDownLatch(1)
        val threads = CopyOnWriteArrayList<Thread>()
        val timedOut = AtomicBoolean(false)
        override fun getApplicationContext(): Context = this
        override fun getContentResolver(): ContentResolver {
            threads += Thread.currentThread()
            if (!release.await(10, TimeUnit.SECONDS)) {
                timedOut.set(true)
                throw IllegalStateException("test resolver gate timed out")
            }
            return baseContext.contentResolver
        }
    }

    private fun prepare(): Triple<ProgressViewModel, ProgressRepository, UserRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().also { database = it }
        val progress = ProgressRepository(db.weightLogDao(), db.photoLogDao())
        val users = UserRepository(db.userDao())
        runBlocking { users.saveUser(UserEntity(name = "Before", weight = 70f)) }
        provider = BlockingResolverContext(context)
        lateinit var vm: ProgressViewModel
        main { vm = ProgressViewModel(progress, users); store.put("progress", vm) }
        return Triple(vm, progress, users)
    }

    @After fun tearDown() {
        provider?.release?.countDown()
        main { store.clear() }
        database?.close()
    }

    @Test fun slowResolverLeavesMainResponsiveAndProfileWriteUsesFieldsUpdatedWhileItWasBlocked() {
        val (vm, _, users) = prepare()
        val resolverContext = provider!!
        // This URI has no persistable grant. Profile's existing best-effort policy still saves its URI.
        val uri = Uri.parse("content://com.example.baselift.test.ungranted/profile")
        compose.setContent { MaterialTheme {
            val user by vm.user.collectAsStateWithLifecycle()
            var taps by remember { mutableIntStateOf(0) }
            Column {
                Text(user?.name ?: "Loading")
                Button(onClick = { taps++; vm.updateProfileName("During IO") }) { Text("Tap $taps") }
            }
        } }
        compose.waitUntil(5_000) { vm.user.value?.name == "Before" }
        main { vm.updateProfilePhoto(resolverContext, uri) }
        compose.waitUntil(5_000) { resolverContext.threads.size == 1 }
        assertNotSame(Looper.getMainLooper().thread, resolverContext.threads.single())
        assertEquals(1L, resolverContext.release.count)
        compose.onNodeWithText("Tap 0").performClick()
        compose.onNodeWithText("Tap 1").assertIsDisplayed()
        compose.waitUntil(5_000) { vm.user.value?.name == "During IO" }
        compose.onNodeWithText("During IO").assertIsDisplayed()
        assertFalse(resolverContext.timedOut.get())
        assertNull(runBlocking { users.getUser().first() }!!.profilePhotoUri)
        resolverContext.release.countDown()
        compose.waitUntil(5_000) { vm.user.value?.profilePhotoUri == uri.toString() }
        val saved = runBlocking { users.getUser().first() }!!
        assertEquals("During IO", saved.name); assertEquals(70f, saved.weight, 0f)
        assertEquals(uri.toString(), saved.profilePhotoUri)
    }

    @Test fun disposalWhileResolverIsBlockedPreventsDiaryWritesAndRemainingUriRequests() {
        val (vm, progress, _) = prepare()
        val resolverContext = provider!!
        val completed = CountDownLatch(1)
        main {
            vm.viewModelScope.coroutineContext[Job]!!.invokeOnCompletion { completed.countDown() }
            vm.addPhotoLogs(resolverContext, listOf("one", "two", "three").map {
                Uri.parse("content://com.example.baselift.test.ungranted/$it")
            }, 1234L)
        }
        compose.waitUntil(5_000) { resolverContext.threads.size == 1 }
        assertNotSame(Looper.getMainLooper().thread, resolverContext.threads.single())
        main { store.clear() }
        resolverContext.release.countDown()
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertFalse(resolverContext.timedOut.get())
        assertEquals(1, resolverContext.threads.size)
        assertTrue(runBlocking { progress.allPhotoLogs.first().isEmpty() })
    }
}
