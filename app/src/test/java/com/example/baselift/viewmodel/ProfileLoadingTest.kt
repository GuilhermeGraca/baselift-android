package com.example.baselift.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.IUserRepository
import com.example.baselift.ViewModel.onboarding.OnboardingViewModel
import com.example.baselift.fakes.FakeProgressRepository
import com.example.baselift.helpers.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ProfileLoadingTest {
    @get:Rule val main = MainDispatcherRule()
    private val users = mockk<IUserRepository>()
    private val progress = FakeProgressRepository()

    @Test fun `delayed first read is distinct from confirmed missing user and healthy retry is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        every { users.getUser() } returns flow { gate.await(); emit(null); awaitCancellation() }
        val vm = OnboardingViewModel(users, progress)
        assertTrue(vm.profileLoadState.value.isLoading)
        assertNull(vm.profileLoadState.value.profile)
        vm.retryProfileLoad()
        verify(exactly = 1) { users.getUser() }
        gate.complete(Unit)
        assertFalse(vm.profileLoadState.value.isLoading)
        assertNotNull(vm.profileLoadState.value.profile)
        assertNull(vm.profileLoadState.value.profile!!.user)
        vm.viewModelScope.cancel()
    }

    @Test fun `initial failure is not absence and retry has one owner until fresh profile arrives`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val saved = UserEntity(name = "Saved", weight = 80f)
        every { users.getUser() } returnsMany listOf(
            flow { throw IOException("read failed") },
            flow { gate.await(); emit(saved); awaitCancellation() }
        )
        val vm = OnboardingViewModel(users, progress)
        assertTrue(vm.profileLoadState.value.hasError)
        assertFalse(vm.profileLoadState.value.isLoading)
        assertNull(vm.profileLoadState.value.profile)
        vm.retryProfileLoad()
        vm.retryProfileLoad()
        assertTrue(vm.profileLoadState.value.isLoading)
        assertTrue(vm.profileLoadState.value.hasError)
        verify(exactly = 2) { users.getUser() }
        gate.complete(Unit)
        assertEquals(saved, vm.profileLoadState.value.profile!!.user)
        assertEquals(saved, vm.uiState.value)
        assertFalse(vm.profileLoadState.value.isLoading)
        assertFalse(vm.profileLoadState.value.hasError)
        vm.viewModelScope.cancel()
    }

    @Test fun `failure after loading retains confirmed profile recalibration and draft during retry`() = runTest {
        val fail = CompletableDeferred<Unit>()
        val recover = CompletableDeferred<Unit>()
        val saved = UserEntity(name = "Before", weight = 70f)
        every { users.getUser() } returnsMany listOf(
            flow { emit(saved); fail.await(); throw IOException("later read failed") },
            flow { recover.await(); emit(saved); awaitCancellation() }
        )
        val vm = OnboardingViewModel(users, progress)
        vm.startRecalibration()
        vm.updateWeight(75f, "kg")
        fail.complete(Unit)
        assertEquals(saved, vm.profileLoadState.value.profile!!.user)
        assertTrue(vm.profileLoadState.value.hasError)
        assertTrue(vm.isRecalibrating.value)
        assertEquals(75f, vm.uiState.value.weight, 0f)
        vm.retryProfileLoad()
        assertEquals(saved, vm.profileLoadState.value.profile!!.user)
        assertEquals(75f, vm.uiState.value.weight, 0f)
        recover.complete(Unit)
        assertEquals(saved, vm.profileLoadState.value.profile!!.user)
        assertEquals(75f, vm.uiState.value.weight, 0f)
        assertTrue(vm.isRecalibrating.value)
        assertFalse(vm.profileLoadState.value.hasError)
        vm.viewModelScope.cancel()
    }

    @Test fun `failed retry keeps absence unresolved and allows another explicit attempt`() = runTest {
        every { users.getUser() } returnsMany listOf(
            flow { throw IOException() }, flow { throw IOException() }, flow { emit(null); awaitCancellation() }
        )
        val vm = OnboardingViewModel(users, progress)
        vm.retryProfileLoad()
        assertTrue(vm.profileLoadState.value.hasError)
        assertNull(vm.profileLoadState.value.profile)
        vm.retryProfileLoad()
        assertNotNull(vm.profileLoadState.value.profile)
        assertNull(vm.profileLoadState.value.profile!!.user)
        assertFalse(vm.profileLoadState.value.hasError)
        verify(exactly = 3) { users.getUser() }
        vm.viewModelScope.cancel()
    }

    @Test fun `actual deletion clears the confirmed profile draft and recalibration`() = runTest {
        val data = MutableStateFlow<UserEntity?>(UserEntity(name = "Before", weight = 70f))
        every { users.getUser() } returns data
        val vm = OnboardingViewModel(users, progress)
        vm.startRecalibration()
        data.value = null
        assertNotNull(vm.profileLoadState.value.profile)
        assertNull(vm.profileLoadState.value.profile!!.user)
        assertEquals(UserEntity(), vm.uiState.value)
        assertFalse(vm.isRecalibrating.value)
        vm.viewModelScope.cancel()
    }

    @Test fun `scope cancellation does not report a storage error`() = runTest {
        val waiting = CompletableDeferred<Unit>()
        every { users.getUser() } returns flow { waiting.await(); emit(UserEntity()) }
        val vm = OnboardingViewModel(users, progress)
        vm.viewModelScope.cancel()
        assertFalse(vm.profileLoadState.value.hasError)
        assertNull(vm.profileLoadState.value.profile)
        vm.retryProfileLoad()
        verify(exactly = 1) { users.getUser() }
    }
}
