package com.example.baselift.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.ViewModel.progress.ProgressViewModel
import com.example.baselift.fakes.FakeProgressRepository
import com.example.baselift.fakes.FakeUserRepository
import com.example.baselift.helpers.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var viewModel: ProgressViewModel
    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockUri: Uri

    @Before
    fun setup() {
        progressRepository = FakeProgressRepository()
        userRepository = FakeUserRepository()
        viewModel = ProgressViewModel(progressRepository, userRepository)

        mockContext = mockk()
        mockContentResolver = mockk(relaxed = true)
        mockUri = mockk(relaxed = true)
        
        every { mockContext.contentResolver } returns mockContentResolver
        every { mockUri.toString() } returns "content://test/uri"
    }

    @Test
    fun `addWeightLog inserts weight into repository`() = runTest {
        val pastTimestamp = 1600000000000L // Some time in the past
        viewModel.addWeightLog(75.5f, pastTimestamp)
        
        runCurrent()
        
        val logs = progressRepository.allWeightLogs.first()
        assertEquals(1, logs.size)
        assertEquals(75.5f, logs[0].weightValue, 0.0f)
        assertEquals(pastTimestamp, logs[0].timestamp)
    }

    @Test
    fun `addWeightLog updates user entity if timestamp is today`() = runTest {
        // Prepare user
        userRepository.saveUser(UserEntity(id = 1, weight = 70f))
        
        val today = Calendar.getInstance().timeInMillis
        viewModel.addWeightLog(78f, today)
        
        runCurrent()
        
        val logs = progressRepository.allWeightLogs.first()
        assertEquals(1, logs.size)
        
        val user = userRepository.getUser().first()
        assertNotNull(user)
        assertEquals(78f, user!!.weight, 0.0f)
    }

    @Test
    fun `setTargetWeight updates user entity`() = runTest {
        userRepository.saveUser(UserEntity(id = 1, targetWeight = 70f))
        
        viewModel.setTargetWeight(65f)
        
        runCurrent()
        
        val user = userRepository.getUser().first()
        assertEquals(65f, user!!.targetWeight)
    }

    @Test
    fun `updateProfileName updates user entity`() = runTest {
        userRepository.saveUser(UserEntity(id = 1, name = "Old Name"))
        
        viewModel.updateProfileName("New Name")
        
        runCurrent()
        
        val user = userRepository.getUser().first()
        assertEquals("New Name", user!!.name)
    }

    @Test
    fun `addPhotoLog takes persistable permission and inserts log`() = runTest {
        viewModel.addPhotoLog(mockContext, mockUri)
        
        runCurrent()
        
        verify(exactly = 1) { 
            mockContentResolver.takePersistableUriPermission(mockUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val logs = progressRepository.allPhotoLogs.first()
        assertEquals(1, logs.size)
        assertEquals("content://test/uri", logs[0].photoUri)
    }

    @Test
    fun `updateProfilePhoto updates user entity and takes permission`() = runTest {
        userRepository.saveUser(UserEntity(id = 1, profilePhotoUri = null))
        
        viewModel.updateProfilePhoto(mockContext, mockUri)
        
        runCurrent()
        
        verify(exactly = 1) { 
            mockContentResolver.takePersistableUriPermission(mockUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val user = userRepository.getUser().first()
        assertEquals("content://test/uri", user!!.profilePhotoUri)
    }

    @Test
    fun `updateProfilePhoto with null clears photo from user entity`() = runTest {
        userRepository.saveUser(UserEntity(id = 1, profilePhotoUri = "content://old/uri"))
        
        viewModel.updateProfilePhoto(mockContext, null)
        
        runCurrent()
        
        val user = userRepository.getUser().first()
        assertNull(user!!.profilePhotoUri)
    }

    @Test
    fun `deleteWeightLog removes log from repository`() = runTest {
        viewModel.addWeightLog(75f, 1000L)
        runCurrent()
        
        val log = progressRepository.allWeightLogs.first()[0]
        
        viewModel.deleteWeightLog(log)
        runCurrent()
        
        val logs = progressRepository.allWeightLogs.first()
        assertEquals(0, logs.size)
    }

    @Test
    fun `deleteAllData clears both user and progress repositories`() = runTest {
        userRepository.saveUser(UserEntity(id = 1, name = "Test"))
        viewModel.addWeightLog(80f, 1000L)
        runCurrent()
        
        viewModel.deleteAllData()
        runCurrent()
        
        val user = userRepository.getUser().first()
        assertNull(user)
        
        val logs = progressRepository.allWeightLogs.first()
        assertEquals(0, logs.size)
    }
}
