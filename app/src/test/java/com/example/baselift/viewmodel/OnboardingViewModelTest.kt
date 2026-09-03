package com.example.baselift.viewmodel

import app.cash.turbine.test
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.ViewModel.onboarding.OnboardingViewModel
import com.example.baselift.fakes.FakeProgressRepository
import com.example.baselift.fakes.FakeUserRepository
import com.example.baselift.helpers.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var userRepository: FakeUserRepository
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        userRepository = FakeUserRepository()
        progressRepository = FakeProgressRepository()
        // O ViewModel faz collect no construtor (no init), por isso instanciamos depois de configurar fakes
        viewModel = OnboardingViewModel(userRepository, progressRepository)
    }

    @Test
    fun `init without user sets hasUser to false and isLoaded to true`() = runTest {
        viewModel.isLoaded.test {
            assertTrue(awaitItem()) // should be true because flow initializes with false then goes to true very quickly
            // if we missed the false, we just check it is true now
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.hasUser.value)
    }

    @Test
    fun `init with user sets hasUser to true`() = runTest {
        userRepository.saveUser(UserEntity(id = 1, name = "Test"))
        
        val newViewModel = OnboardingViewModel(userRepository, progressRepository)
        
        newViewModel.hasUser.test {
            val value = awaitItem()
            if (!value) {
                assertTrue(awaitItem()) // It might emit false first then true
            } else {
                assertTrue(value)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update functions modify uiState correctly`() {
        viewModel.updateGender("Female")
        assertEquals("Female", viewModel.uiState.value.gender)

        viewModel.updateAge(25)
        assertEquals(25, viewModel.uiState.value.age)

        viewModel.updateWeight(65f, "kg")
        assertEquals(65f, viewModel.uiState.value.weight, 0.0f)
        assertEquals("kg", viewModel.uiState.value.preferredWeightUnit)

        viewModel.updateHeight(170f, "cm")
        assertEquals(170f, viewModel.uiState.value.height, 0.0f)
        assertEquals("cm", viewModel.uiState.value.preferredHeightUnit)

        viewModel.updateActivityLevel("Active")
        assertEquals("Active", viewModel.uiState.value.activityLevel)

        viewModel.updateGoal("Build Muscle")
        assertEquals("Build Muscle", viewModel.uiState.value.goal)
    }

    @Test
    fun `calculateTargets updates calculated fields`() {
        viewModel.updateGender("Male")
        viewModel.updateAge(20)
        viewModel.updateWeight(80f, "kg")
        viewModel.updateHeight(180f, "cm")
        viewModel.updateActivityLevel("Sedentary")
        viewModel.updateGoal("Maintain Weight")

        viewModel.calculateTargets()

        val state = viewModel.uiState.value
        assertTrue(state.bmi > 0)
        assertTrue(state.dailyCaloriesGoal > 0)
        assertTrue(state.proteinGoal > 0)
        assertTrue(state.carbsGoal > 0)
        assertTrue(state.fatGoal > 0)
        assertFalse(state.isCustomTargets)
    }

    @Test
    fun `updateCustomTargets flags user with isCustomTargets`() {
        viewModel.updateCustomTargets(2500, 150, 200, 80)
        val state = viewModel.uiState.value
        assertEquals(2500, state.dailyCaloriesGoal)
        assertEquals(150, state.proteinGoal)
        assertEquals(200, state.carbsGoal)
        assertEquals(80, state.fatGoal)
        assertTrue(state.isCustomTargets)
    }

    @Test
    fun `refreshTargets forces recalculation ignoring custom targets`() {
        viewModel.updateGender("Male")
        viewModel.updateAge(20)
        viewModel.updateWeight(80f, "kg")
        viewModel.updateHeight(180f, "cm")
        viewModel.updateActivityLevel("Sedentary")
        viewModel.updateGoal("Maintain Weight")

        // User sets custom targets manually
        viewModel.updateCustomTargets(5000, 300, 500, 200)
        assertTrue(viewModel.uiState.value.isCustomTargets)

        // Then clicks refresh
        viewModel.refreshTargets()

        val state = viewModel.uiState.value
        assertFalse(state.isCustomTargets)
        assertNotEquals(5000, state.dailyCaloriesGoal)
    }

    @Test
    fun `saveUserProfile saves user to repository`() = runTest {
        viewModel.updateGender("Male")
        viewModel.updateAge(30)
        
        viewModel.saveUserProfile()
        
        val savedUser = userRepository.getUser().first()
        assertNotNull(savedUser)
        assertEquals("Male", savedUser?.gender)
        assertEquals(30, savedUser?.age)
    }

    @Test
    fun `saveUserProfile inserts weight log on first time`() = runTest {
        viewModel.updateWeight(75f, "kg")
        
        viewModel.saveUserProfile()
        
        val weightLogs = progressRepository.allWeightLogs.first()
        assertEquals(1, weightLogs.size)
        assertEquals(75f, weightLogs[0].weightValue, 0.0f)
    }

    @Test
    fun `saveUserProfile inserts weight log on weight change during recalibration`() = runTest {
        // Setup initial user
        userRepository.saveUser(UserEntity(id = 1, weight = 70f))
        val newViewModel = OnboardingViewModel(userRepository, progressRepository)
        
        // Wait for coroutines to complete initialization
        runCurrent()
        
        assertEquals(70f, newViewModel.uiState.value.weight, 0.0f)

        newViewModel.startRecalibration()
        assertTrue(newViewModel.isRecalibrating.value)

        newViewModel.updateWeight(80f, "kg") // Weight changed
        newViewModel.saveUserProfile()

        val weightLogs = progressRepository.allWeightLogs.first()
        assertEquals(1, weightLogs.size)
        assertEquals(80f, weightLogs[0].weightValue, 0.0f)
        assertFalse(newViewModel.isRecalibrating.value)
    }
}
