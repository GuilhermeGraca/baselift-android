package com.example.baselift.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.IProgressRepository
import com.example.baselift.Model.repository.IUserRepository
import com.example.baselift.ViewModel.progress.ProgressViewModel
import com.example.baselift.fakes.FakeProgressRepository
import com.example.baselift.fakes.FakeUserRepository
import com.example.baselift.helpers.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressPhotoIoTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())
    private val store = ViewModelStore()
    private val progress = FakeProgressRepository()
    private val users = FakeUserRepository()
    private val context = mockk<Context>()
    private val application = mockk<Context>()
    private val resolver = mockk<ContentResolver>(relaxed = true)
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private lateinit var vm: ProgressViewModel
    private val first = uri("first")
    private val second = uri("second")
    private val third = uri("third")
    private fun uri(name: String): Uri = mockk<Uri>().also { every { it.toString() } returns "content://test/$name" }

    private fun start(source: IProgressRepository = progress, userSource: IUserRepository = users) {
        every { context.applicationContext } returns application
        every { application.contentResolver } returns resolver
        vm = ProgressViewModel(source, userSource, main.testDispatcher)
        store.put("progress", vm)
    }
    @After fun tearDown() { store.clear() }

    @Test fun `bulk selection is copied at acceptance and preserves order timestamps and duplicates`() = runTest {
        start()
        val selected = mutableListOf(first, second, first)
        vm.addPhotoLogs(context, selected, 1234L)
        selected.clear(); selected += third
        runCurrent()
        val rows = progress.allPhotoLogs.first()
        assertEquals(listOf(first, second, first).map { it.toString() }, rows.map { it.photoUri })
        assertTrue(rows.all { it.timestamp == 1234L })
        verify(exactly = 2) { resolver.takePersistableUriPermission(first, flags) }
        verify(exactly = 1) { resolver.takePersistableUriPermission(second, flags) }
        verify(exactly = 0) { context.contentResolver }
    }

    @Test fun `denied URI does not persist a diary row and later selected URIs still save`() = runTest {
        start(); every { resolver.takePersistableUriPermission(second, flags) } throws SecurityException("denied")
        vm.addPhotoLogs(context, listOf(first, second, third), 1234L); runCurrent()
        assertEquals(listOf(first, third).map { it.toString() }, progress.allPhotoLogs.first().map { it.photoUri })
        verify(exactly = 1) { resolver.takePersistableUriPermission(third, flags) }
    }

    @Test fun `individual persistence failure retains prior success and continues the existing partial import policy`() = runTest {
        val source = object : IProgressRepository by progress {
            override suspend fun insertPhotoLog(photoUri: String, timestamp: Long) {
                if (photoUri == second.toString()) error("write failed")
                progress.insertPhotoLog(photoUri, timestamp)
            }
        }
        start(source); vm.addPhotoLogs(context, listOf(first, second, third), 1234L); runCurrent()
        assertEquals(listOf(first, third).map { it.toString() }, progress.allPhotoLogs.first().map { it.photoUri })
    }

    @Test fun `provider cancellation stops the selection without treating it as a failed item`() = runTest {
        start(); every { resolver.takePersistableUriPermission(second, flags) } throws CancellationException("cancelled")
        vm.addPhotoLogs(context, listOf(first, second, third), 1234L); runCurrent()
        assertEquals(listOf(first.toString()), progress.allPhotoLogs.first().map { it.photoUri })
        verify(exactly = 0) { resolver.takePersistableUriPermission(third, flags) }
    }

    @Test fun `repository cancellation stops remaining permission requests and inserts`() = runTest {
        val source = object : IProgressRepository by progress {
            override suspend fun insertPhotoLog(photoUri: String, timestamp: Long) {
                if (photoUri == second.toString()) throw CancellationException("cancelled")
                progress.insertPhotoLog(photoUri, timestamp)
            }
        }
        start(source); vm.addPhotoLogs(context, listOf(first, second, third), 1234L); runCurrent()
        assertEquals(1, progress.allPhotoLogs.first().size)
        verify(exactly = 0) { resolver.takePersistableUriPermission(third, flags) }
    }

    @Test fun `empty selection performs no permission or repository operations`() = runTest {
        start(); vm.addPhotoLogs(context, emptyList(), 1234L); runCurrent()
        verify(exactly = 0) { resolver.takePersistableUriPermission(any(), any()) }
        assertTrue(progress.allPhotoLogs.first().isEmpty())
    }

    @Test fun `single import persists only after permission with a current timestamp`() = runTest {
        var permitted = false
        val source = object : IProgressRepository by progress {
            override suspend fun insertPhotoLog(photoUri: String, timestamp: Long) {
                assertTrue(permitted); progress.insertPhotoLog(photoUri, timestamp)
            }
        }
        start(source); every { resolver.takePersistableUriPermission(first, flags) } answers { permitted = true }
        val before = System.currentTimeMillis(); vm.addPhotoLog(context, first); runCurrent()
        val row = progress.allPhotoLogs.first().single()
        assertEquals(first.toString(), row.photoUri)
        assertTrue(row.timestamp in before..System.currentTimeMillis())
    }

    @Test fun `single import permission rejection does not insert a diary row`() = runTest {
        start(); every { resolver.takePersistableUriPermission(first, flags) } throws SecurityException("denied")
        vm.addPhotoLog(context, first); runCurrent()
        assertTrue(progress.allPhotoLogs.first().isEmpty())
    }

    @Test fun `profile keeps best effort permission policy but reads current fields after provider work`() = runTest {
        users.saveUser(UserEntity(name = "Before", weight = 70f)); start()
        every { resolver.takePersistableUriPermission(first, flags) } answers {
            runBlocking { users.saveUser(UserEntity(name = "After", weight = 80f, targetWeight = 65f)) }
            throw SecurityException("provider cannot retain permission")
        }
        vm.updateProfilePhoto(context, first); runCurrent()
        val row = users.getUser().first()!!
        assertEquals(first.toString(), row.profilePhotoUri); assertEquals("After", row.name)
        assertEquals(80f, row.weight, 0f); assertEquals(65f, row.targetWeight)
    }

    @Test fun `a later photo choice supersedes an older provider result before persistence`() = runTest {
        users.saveUser(UserEntity(name = "User")); start()
        every { resolver.takePersistableUriPermission(first, flags) } answers { vm.updateProfilePhoto(context, second) }
        vm.updateProfilePhoto(context, first); runCurrent()
        assertEquals(second.toString(), users.getUser().first()!!.profilePhotoUri)
    }

    @Test fun `clearing the profile photo supersedes an older pending provider result`() = runTest {
        users.saveUser(UserEntity(profilePhotoUri = "old")); start()
        every { resolver.takePersistableUriPermission(first, flags) } answers { vm.updateProfilePhoto(context, null) }
        vm.updateProfilePhoto(context, first); runCurrent()
        assertNull(users.getUser().first()!!.profilePhotoUri)
        verify(exactly = 1) { resolver.takePersistableUriPermission(any(), any()) }
    }

    @Test fun `profile deletion during provider work is not resurrected`() = runTest {
        users.saveUser(UserEntity(name = "User")); start()
        every { resolver.takePersistableUriPermission(first, flags) } answers { runBlocking { users.clearUserData() } }
        vm.updateProfilePhoto(context, first); runCurrent()
        assertNull(users.getUser().first())
    }

    @Test fun `queued stale profile requests do no provider work and null clearing needs no permission`() = runTest {
        users.saveUser(UserEntity(profilePhotoUri = "old")); start()
        vm.updateProfilePhoto(context, first); vm.updateProfilePhoto(context, second); vm.updateProfilePhoto(context, null)
        runCurrent()
        verify(exactly = 0) { resolver.takePersistableUriPermission(any(), any()) }
        assertNull(users.getUser().first()!!.profilePhotoUri)
    }

    @Test fun `profile provider cancellation does not save even under best effort policy`() = runTest {
        users.saveUser(UserEntity(profilePhotoUri = "old")); start()
        every { resolver.takePersistableUriPermission(first, flags) } throws CancellationException("cancelled")
        vm.updateProfilePhoto(context, first); runCurrent()
        assertEquals("old", users.getUser().first()!!.profilePhotoUri)
    }

    @Test fun `disposal during provider completion prevents a diary write and all following URI attempts`() = runTest {
        start(); every { resolver.takePersistableUriPermission(first, flags) } answers { store.clear() }
        vm.addPhotoLogs(context, listOf(first, second), 1234L); runCurrent()
        assertTrue(progress.allPhotoLogs.first().isEmpty())
        verify(exactly = 0) { resolver.takePersistableUriPermission(second, flags) }
    }
}
