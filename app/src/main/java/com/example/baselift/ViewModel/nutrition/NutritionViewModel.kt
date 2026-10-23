package com.example.baselift.ViewModel.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.nutrition.NutritionPortionCalculator
import com.example.baselift.Model.nutrition.NutritionValues
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.IUserRepository
import com.example.baselift.Model.repository.NutritionDay
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.compose.runtime.Stable

// estado da vista de nutrição
@Stable
data class NutritionUiState(
    val targetCalories: Int = 0,
    val targetProtein: Int = 0,
    val targetCarbs: Int = 0,
    val targetFats: Int = 0,

    val consumedCalories: Int = 0,
    val consumedProtein: Int = 0,
    val consumedCarbs: Int = 0,
    val consumedFats: Int = 0,

    val todayLogs: List<NutritionLogEntity> = emptyList(),
    val mealTemplates: List<MealTemplateEntity> = emptyList(),
    
    val isLoading: Boolean = true
)

data class MealTemplateDeletionState(
    val pendingTemplateIds: Set<Int> = emptySet(),
    val failedTemplate: MealTemplateEntity? = null
)

@Stable
data class QuickLogSubmissionState(
    val pendingRequestId: Long? = null,
    val completedRequestId: Long? = null,
    val failedRequestId: Long? = null
)

@Stable
data class MealWriteState(
    val pendingRequestId: Long? = null,
    val completedRequestId: Long? = null,
    val failedRequestId: Long? = null
)

data class MealLogResult(val templateName: String, val succeeded: Boolean)

// modelo de vista responsável pela aba de nutrição
class NutritionViewModel(
    private val nutritionRepository: INutritionRepository,
    private val userRepository: IUserRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault
) : ViewModel() {

    private val dateRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _mealTemplateDeletion = MutableStateFlow(MealTemplateDeletionState())
    val mealTemplateDeletion: StateFlow<MealTemplateDeletionState> = _mealTemplateDeletion.asStateFlow()
    private val _quickLogSubmission = MutableStateFlow(QuickLogSubmissionState())
    val quickLogSubmission: StateFlow<QuickLogSubmissionState> = _quickLogSubmission.asStateFlow()
    private var nextQuickLogRequestId = 0L
    private val _mealTemplateSave = MutableStateFlow(MealWriteState())
    val mealTemplateSave: StateFlow<MealWriteState> = _mealTemplateSave.asStateFlow()
    private val _mealLogSubmission = MutableStateFlow(MealWriteState())
    val mealLogSubmission: StateFlow<MealWriteState> = _mealLogSubmission.asStateFlow()
    private val _mealLogResults = MutableSharedFlow<MealLogResult>(extraBufferCapacity = 16)
    val mealLogResults = _mealLogResults.asSharedFlow()
    private var nextMealSaveRequestId = 0L
    private var nextMealLogRequestId = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NutritionUiState> = combine(
        userRepository.getUser(),
        dateRefresh.onStart { emit(Unit) }.flatMapLatest {
            flow {
                while (currentCoroutineContext().isActive) {
                    val now = clockMillis()
                    val day = NutritionDay.at(now, timeZone())
                    emit(day)
                    delay((day.endExclusiveMillis - now).coerceAtLeast(1))
                }
            }
        }.distinctUntilChanged().flatMapLatest(nutritionRepository::getLogsForDay),
        nutritionRepository.getAllMealTemplates()
    ) { user, logs, templates ->
        
        val consumedKcal = logs.sumOf { it.calories }
        val consumedP = logs.sumOf { it.protein }
        val consumedC = logs.sumOf { it.carbs }
        val consumedF = logs.sumOf { it.fats }

        NutritionUiState(
            targetCalories = user?.dailyCaloriesGoal ?: 0,
            targetProtein = user?.proteinGoal ?: 0,
            targetCarbs = user?.carbsGoal ?: 0,
            targetFats = user?.fatGoal ?: 0,
            consumedCalories = consumedKcal,
            consumedProtein = consumedP,
            consumedCarbs = consumedC,
            consumedFats = consumedF,
            todayLogs = logs,
            mealTemplates = templates,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_000),
        initialValue = NutritionUiState()
    )

    fun refreshDate() {
        // Restart the midnight wait after clock/zone/resume events; equal days keep their query.
        dateRefresh.tryEmit(Unit)
    }

    // adiciona um registo rápido
    fun addQuickLog(
        calories: Int,
        protein: Int = 0,
        carbs: Int = 0,
        fats: Int = 0,
        isCaloriesOnly: Boolean,
        isPer100g: Boolean = false,
        grams: Int? = null
    ): Long? {
        val totals = NutritionPortionCalculator.calculate(
            NutritionValues(calories, if (isCaloriesOnly) 0 else protein,
                if (isCaloriesOnly) 0 else carbs, if (isCaloriesOnly) 0 else fats),
            per100g = isPer100g,
            grams = grams
        ) ?: return null
        val logName = if (isCaloriesOnly) "Quick Add" else "Detailed Macros"
        val acceptedAt = clockMillis()
        val requestId = ++nextQuickLogRequestId
        _quickLogSubmission.value = QuickLogSubmissionState(pendingRequestId = requestId)
        viewModelScope.launch {
            try {
                nutritionRepository.insertNutritionLog(
                    NutritionLogEntity(
                        name = logName,
                        calories = totals.calories,
                        protein = totals.protein,
                        carbs = totals.carbs,
                        fats = totals.fats,
                        timestamp = acceptedAt
                    )
                )
                _quickLogSubmission.update { state ->
                    if (state.pendingRequestId == requestId) {
                        QuickLogSubmissionState(completedRequestId = requestId)
                    } else state
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _quickLogSubmission.update { state ->
                    if (state.pendingRequestId == requestId) {
                        QuickLogSubmissionState(failedRequestId = requestId)
                    } else state
                }
            }
        }
        return requestId
    }

    // apagar um registo
    fun deleteLog(log: NutritionLogEntity) {
        viewModelScope.launch {
            nutritionRepository.deleteNutritionLog(log)
        }
    }

    // apagar todos os registos de hoje
    fun resetTodayLogs() {
        viewModelScope.launch {
            nutritionRepository.resetTodayLogs()
        }
    }

    // adicionar ou atualizar um template
    fun saveMealTemplate(template: MealTemplateEntity): Long? {
        _mealTemplateSave.value.pendingRequestId?.let { return it }
        if (template.name.isBlank() || listOf(template.calories, template.protein,
                template.carbs, template.fats).any { it < 0 }) return null
        if (template.isPer100g && listOf(template.calories, template.protein,
                template.carbs, template.fats).all { it == 0 }) return null
        val requestId = ++nextMealSaveRequestId
        _mealTemplateSave.value = MealWriteState(pendingRequestId = requestId)
        viewModelScope.launch {
            try {
                nutritionRepository.insertMealTemplate(template)
                _mealTemplateSave.update { state ->
                    if (state.pendingRequestId == requestId) MealWriteState(completedRequestId = requestId) else state
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _mealTemplateSave.update { state ->
                    if (state.pendingRequestId == requestId) MealWriteState(failedRequestId = requestId) else state
                }
            }
        }
        return requestId
    }

    // apagar um template
    fun deleteMealTemplate(template: MealTemplateEntity) {
        if (template.id in _mealTemplateDeletion.value.pendingTemplateIds) return
        _mealTemplateDeletion.update {
            it.copy(pendingTemplateIds = it.pendingTemplateIds + template.id, failedTemplate = null)
        }
        // The command starts now; row exit animations and screen disposal do not own it.
        viewModelScope.launch {
            try {
                nutritionRepository.deleteMealTemplate(template)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _mealTemplateDeletion.update { it.copy(failedTemplate = template) }
            } finally {
                _mealTemplateDeletion.update { it.copy(pendingTemplateIds = it.pendingTemplateIds - template.id) }
            }
        }
    }

    fun dismissMealTemplateDeletionError() {
        _mealTemplateDeletion.update { it.copy(failedTemplate = null) }
    }

    // usar um template para registar (Quick Add Meal)
    fun logMealTemplate(template: MealTemplateEntity, grams: Int? = null): Long? {
        if (template.isPer100g) _mealLogSubmission.value.pendingRequestId?.let { return it }
        val totals = if (template.isPer100g) {
            NutritionPortionCalculator.calculate(
                NutritionValues(template.calories, template.protein, template.carbs, template.fats),
                per100g = true, grams = grams
            ) ?: return null
        } else {
            NutritionValues(template.calories, template.protein, template.carbs, template.fats)
                .takeIf { it.calories >= 0 && it.protein >= 0 && it.carbs >= 0 && it.fats >= 0 }
                ?: return null
        }
        val acceptedAt = clockMillis()
        val requestId = ++nextMealLogRequestId
        if (template.isPer100g) _mealLogSubmission.value = MealWriteState(pendingRequestId = requestId)
        viewModelScope.launch {
            try {
                nutritionRepository.insertNutritionLog(
                    NutritionLogEntity(
                        name = template.name,
                        calories = totals.calories,
                        protein = totals.protein,
                        carbs = totals.carbs,
                        fats = totals.fats,
                        timestamp = acceptedAt
                    )
                )
                if (template.isPer100g) {
                    _mealLogSubmission.update { state ->
                        if (state.pendingRequestId == requestId) MealWriteState(completedRequestId = requestId) else state
                    }
                } else _mealLogResults.tryEmit(MealLogResult(template.name, succeeded = true))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (template.isPer100g) {
                    _mealLogSubmission.update { state ->
                        if (state.pendingRequestId == requestId) MealWriteState(failedRequestId = requestId) else state
                    }
                } else _mealLogResults.tryEmit(MealLogResult(template.name, succeeded = false))
            }
        }
        return requestId
    }

    fun deleteAllNutrition() {
        viewModelScope.launch {
            nutritionRepository.clearAllNutritionData()
        }
    }
}

// fábrica para instanciar o modelo de vista
class NutritionViewModelFactory(
    private val nutritionRepository: INutritionRepository,
    private val userRepository: IUserRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NutritionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NutritionViewModel(nutritionRepository, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
