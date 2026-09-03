package com.example.baselift.ViewModel.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateMapOf
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import com.example.baselift.Model.repository.WorkoutRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.Stable

@Stable
data class SetUiModel(
    val setNumber: Int,
    val currentLog: SetLogEntity?, // null se ainda n tiver sido preenchido nesta sessao
    val prevWeight: Float?,
    val prevReps: Int?,
    val isCompleted: Boolean = false,
    val isLastCompleted: Boolean = false,
    val canBeChecked: Boolean = false,
    val canBeRemoved: Boolean = false
)

@Stable
data class ExerciseUiModel(
    val exercise: ExerciseEntity,
    val sets: List<SetUiModel>
)

@Stable
data class WorkoutUiState(
    val workouts: List<WorkoutEntity> = emptyList(),
    val selectedWorkout: WorkoutEntity? = null,
    val activeSession: WorkoutSessionEntity? = null,
    val exercises: List<ExerciseUiModel> = emptyList(),
    val isLoading: Boolean = true
)

class WorkoutViewModel(private val repository: IWorkoutRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    // os mapas de estado sobrevivem a qualquer recomposição
    // o formato da chave é id do exercício e número da série
    val draftWeights = mutableStateMapOf<String, String>()
    val draftReps = mutableStateMapOf<String, String>()

    private fun draftKey(exerciseId: Int, setNumber: Int) = "${exerciseId}_${setNumber}"

    fun updateDraftWeight(exerciseId: Int, setNumber: Int, value: String) {
        draftWeights[draftKey(exerciseId, setNumber)] = value
    }

    fun updateDraftReps(exerciseId: Int, setNumber: Int, value: String) {
        draftReps[draftKey(exerciseId, setNumber)] = value
    }

    fun getDraftWeight(exerciseId: Int, setNumber: Int): String =
        draftWeights[draftKey(exerciseId, setNumber)] ?: ""

    fun getDraftReps(exerciseId: Int, setNumber: Int): String =
        draftReps[draftKey(exerciseId, setNumber)] ?: ""

    // cache para as queries de getPreviousSet (evita N+1 queries a cada emissão do combine)
    private val previousSetsCache = mutableMapOf<String, SetLogEntity?>()

    private suspend fun getCachedPreviousSet(exerciseId: Int, setNumber: Int): SetLogEntity? {
        val key = "${exerciseId}_$setNumber"
        if (!previousSetsCache.containsKey(key)) {
            val prev = repository.getPreviousSet(exerciseId, setNumber)
            previousSetsCache[key] = prev
        }
        return previousSetsCache[key]
    }

    private fun clearDrafts() {
        draftWeights.clear()
        draftReps.clear()
        previousSetsCache.clear() // limpar a cache quando o treino termina
    }

    // guarda o trabalho ativo de seleção de treino para poder cancelar antes de começar outro
    private var selectWorkoutJob: kotlinx.coroutines.Job? = null

    init {
        // obter todos os treinos
        viewModelScope.launch {
            repository.allWorkouts.collect { workouts ->
                _uiState.update { state ->
                    val newSelected = state.selectedWorkout ?: workouts.firstOrNull()
                    if (newSelected != state.selectedWorkout) {
                        selectWorkout(newSelected)
                    }
                    state.copy(workouts = workouts, isLoading = false)
                }
            }
        }
    }

    fun selectWorkout(workout: WorkoutEntity?) {
        if (workout == null) return

        // cancelar a coleção anterior antes de começar uma nova
        selectWorkoutJob?.cancel()

        _uiState.update { it.copy(selectedWorkout = workout, exercises = emptyList()) }

        selectWorkoutJob = viewModelScope.launch {
            // iniciar ou obter a sessão
            val session = repository.startOrGetSession(workout.id)
            _uiState.update { it.copy(activeSession = session) }
            
            // limpar cache ao trocar de treino
            previousSetsCache.clear()

            // observar exercícios para este treino
            repository.getExercisesForWorkout(workout.id).combine(repository.getSetsForSession(session.id)) { exercises, setLogs ->
                // construir o modelo de interface
                exercises.map { exercise ->
                    val exerciseSets = setLogs.filter { it.exerciseId == exercise.id }

                    // usar a contagem de séries explícita e mostrar séries adicionadas na sessão
                    val maxSetNumber = maxOf(
                        exercise.setCount,
                        exerciseSets.maxOfOrNull { it.setNumber } ?: 0
                    )

                    val maxCompleted = exerciseSets.filter { it.isCompleted }.maxOfOrNull { it.setNumber } ?: 0

                    val uiSets = (1..maxSetNumber).map { setNum ->
                        val currentLog = exerciseSets.find { it.setNumber == setNum }
                        val prevLog = getCachedPreviousSet(exercise.id, setNum)

                        val isSetCompleted = currentLog?.isCompleted == true
                        val isLastCompleted = setNum == maxCompleted
                        val isNextAvailable = setNum == maxCompleted + 1
                        val canBeChecked = (!isSetCompleted && isNextAvailable) || (isSetCompleted && isLastCompleted)
                        val isLastSet = setNum == maxSetNumber
                        val canBeRemoved = isLastSet && maxSetNumber > 1

                        SetUiModel(
                            setNumber = setNum,
                            currentLog = currentLog,
                            prevWeight = prevLog?.weight,
                            prevReps = prevLog?.reps,
                            isCompleted = isSetCompleted,
                            isLastCompleted = isLastCompleted,
                            canBeChecked = canBeChecked,
                            canBeRemoved = canBeRemoved
                        )
                    }
                    ExerciseUiModel(exercise, uiSets)
                }
            }.flowOn(Dispatchers.IO).collect { exerciseModels ->
                _uiState.update { it.copy(exercises = exerciseModels) }
            }
        }
    }

    fun createWorkout(name: String) {
        viewModelScope.launch {
            val count = _uiState.value.workouts.size
            repository.createWorkout(name, count)
        }
    }

    fun deleteWorkout(workout: WorkoutEntity) {
        viewModelScope.launch {
            repository.deleteWorkout(workout)
            if (_uiState.value.selectedWorkout == workout) {
                // selecionar outro treino ou nenhum
                val remaining = _uiState.value.workouts.filter { it.id != workout.id }
                selectWorkout(remaining.firstOrNull())
            }
        }
    }

    fun createExercise(name: String, equipment: String, muscleGroups: String) {
        val currentWorkoutId = _uiState.value.selectedWorkout?.id ?: return
        viewModelScope.launch {
            val count = _uiState.value.exercises.size
            repository.createExercise(currentWorkoutId, name, equipment, muscleGroups, count)
        }
    }

    fun addSetToExercise(exerciseId: Int) {
        val exModel = _uiState.value.exercises.find { it.exercise.id == exerciseId } ?: return
        viewModelScope.launch {
            repository.addSet(exModel.exercise)
        }
    }

    fun removeLastSet(exerciseId: Int) {
        val session = _uiState.value.activeSession ?: return
        val exModel = _uiState.value.exercises.find { it.exercise.id == exerciseId } ?: return
        if (exModel.exercise.setCount <= 1) return // não baixar de uma série
        val setNumberToRemove = exModel.exercise.setCount
        // limpar rascunho da série removida
        draftWeights.remove("${exerciseId}_${setNumberToRemove}")
        draftReps.remove("${exerciseId}_${setNumberToRemove}")
        viewModelScope.launch {
            repository.removeLastSet(exModel.exercise, session.id)
        }
    }

    fun deleteExercise(exerciseId: Int) {
        val exModel = _uiState.value.exercises.find { it.exercise.id == exerciseId } ?: return
        // limpar todos os rascunhos para este exercício
        exModel.sets.forEach { set ->
            draftWeights.remove("${exerciseId}_${set.setNumber}")
            draftReps.remove("${exerciseId}_${set.setNumber}")
        }
        viewModelScope.launch {
            repository.deleteExercise(exModel.exercise)
        }
    }

    fun updateExercise(exerciseId: Int, name: String, equipment: String, muscleGroups: String) {
        val exModel = _uiState.value.exercises.find { it.exercise.id == exerciseId } ?: return
        viewModelScope.launch {
            repository.updateExercise(exModel.exercise, name, equipment, muscleGroups)
        }
    }

    fun logSet(exerciseId: Int, setNumber: Int, weight: Float, reps: Int, isCompleted: Boolean, existingSetId: Int = 0) {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            repository.logSet(
                sessionId = session.id,
                exerciseId = exerciseId,
                setNumber = setNumber,
                weight = weight,
                reps = reps,
                isCompleted = isCompleted,
                existingSetId = existingSetId
            )
        }
    }

    fun finalizeWorkout() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            repository.finalizeSession(session)
            clearDrafts() // limpar todos os rascunhos para a próxima sessão começar limpa
            selectWorkout(_uiState.value.selectedWorkout)
        }
    }

    fun deleteAllWorkouts() {
        viewModelScope.launch {
            repository.clearAllWorkoutData()
        }
    }
}

class WorkoutViewModelFactory(
    private val repository: IWorkoutRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkoutViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
