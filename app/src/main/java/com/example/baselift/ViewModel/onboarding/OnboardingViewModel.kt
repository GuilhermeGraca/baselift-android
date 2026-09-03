package com.example.baselift.ViewModel.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.UserRepository
import com.example.baselift.Model.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OnboardingViewModel(
    private val userRepository: com.example.baselift.Model.repository.IUserRepository,
    private val progressRepository: com.example.baselift.Model.repository.IProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserEntity())
    val uiState: StateFlow<UserEntity> = _uiState.asStateFlow()

    private val _isRecalibrating = MutableStateFlow(false)
    val isRecalibrating: StateFlow<Boolean> = _isRecalibrating.asStateFlow()

    private val _hasUser = MutableStateFlow(false)
    val hasUser: StateFlow<Boolean> = _hasUser.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private var oldWeight: Float? = null

    init {
        viewModelScope.launch {
            userRepository.getUser().collect { user ->
                if (user != null) {
                    _uiState.value = user
                    _hasUser.value = true
                    if (oldWeight == null) {
                        oldWeight = user.weight
                    }
                } else {
                    _uiState.value = UserEntity()
                    _hasUser.value = false
                    _isRecalibrating.value = false
                    oldWeight = null
                }
                _isLoaded.value = true
            }
        }
    }

    /**
     * Prepara o ViewModel para uma nova recalibração, capturando o peso atual da base de dados como baseline.
     */
    fun startRecalibration() {
        oldWeight = _uiState.value.weight
        _isRecalibrating.value = true
    }

    // Funções para atualizar cada campo à medida que o utilizador avança nos ecrãs
    fun updateGender(gender: String) { _uiState.update { it.copy(gender = gender) } }
    fun updateAge(age: Int) { _uiState.update { it.copy(age = age) } }
    fun updateWeight(weight: Float, unit: String) { _uiState.update { it.copy(weight = weight, preferredWeightUnit = unit) } }
    fun updateHeight(height: Float, unit: String) { _uiState.update { it.copy(height = height, preferredHeightUnit = unit) } }
    fun updateActivityLevel(level: String) { _uiState.update { it.copy(activityLevel = level) } }
    fun updateGoal(goal: String) { _uiState.update { it.copy(goal = goal) } }

    // Atualiza macros manuais (Custom Targets)
    fun updateCustomTargets(calories: Int, protein: Int, carbs: Int, fat: Int) {
        _uiState.update {
            it.copy(
                dailyCaloriesGoal = calories,
                proteinGoal = protein,
                carbsGoal = carbs,
                fatGoal = fat,
                isCustomTargets = true
            )
        }
    }

    /**
     * Calcula o BMI, Calorias Diárias (TDEE + Goal) e os Macros baseados numa divisão standard
     * e atualiza o estado com os novos valores.
     */
    fun calculateTargets() {
        val user = _uiState.value
        val updatedUser = com.example.baselift.Utils.CalculatorUtils.calculateUserMetrics(user)
        _uiState.value = updatedUser
    }

    /**
     * Força o recálculo dos valores dinâmicos ignorando os custom targets (usado pelo botão de refresh)
     */
    fun refreshTargets() {
        val user = _uiState.value.copy(isCustomTargets = false)
        val updatedUser = com.example.baselift.Utils.CalculatorUtils.calculateUserMetrics(user)
        _uiState.value = updatedUser
    }

    /**
     * Grava o perfil do utilizador na base de dados (concluindo o Onboarding).
     * Se o peso mudou (ou se for o primeiro onboarding), regista uma pesagem automática no histórico.
     */
    fun saveUserProfile() {
        viewModelScope.launch {
            val newUser = _uiState.value
            val isFirstTime = (oldWeight == null)
            val weightChanged = !isFirstTime && (oldWeight != newUser.weight)

            userRepository.saveUser(newUser)

            if (isFirstTime || weightChanged) {
                progressRepository.insertWeightLog(
                    weight = newUser.weight,
                    timestamp = System.currentTimeMillis()
                )
            }
            oldWeight = newUser.weight
            _isRecalibrating.value = false
        }
    }
}

/**
 * Factory necessária para injetar o UserRepository e o ProgressRepository no ViewModel quando fazemos DI Manual
 */
class OnboardingViewModelFactory(
    private val userRepository: com.example.baselift.Model.repository.IUserRepository,
    private val progressRepository: com.example.baselift.Model.repository.IProgressRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(userRepository, progressRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
