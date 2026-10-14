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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
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
data class SetWriteKey(val sessionId: Int, val exerciseId: Int, val setNumber: Int)

@Stable
data class WorkoutUiState(
    val workouts: List<WorkoutEntity> = emptyList(),
    val selectedWorkout: WorkoutEntity? = null,
    val activeSession: WorkoutSessionEntity? = null,
    val exercises: List<ExerciseUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val isFinalizing: Boolean = false,
    val finalizationFailed: Boolean = false,
    val finalizedSessionId: Int? = null,
    val pendingSetWrites: Set<SetWriteKey> = emptySet(),
    val setSaveFailed: Boolean = false,
    val setAddFailed: Boolean = false,
    val pendingSetRemovals: Set<SetWriteKey> = emptySet(),
    val addingSetExerciseIds: Set<Int> = emptySet(),
    val setRemovalFailed: Boolean = false
)

class WorkoutViewModel(
    private val repository: IWorkoutRepository,
    private val modelDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private class PendingSetWrite(val weight: Float, val reps: Int, val isCompleted: Boolean) {
        var saved = false
    }

    // Owned by Main, like commands and UI state. Keep the guard through Room's UI emission.
    private val pendingSetWrites = mutableMapOf<SetWriteKey, PendingSetWrite>()
    private class PendingSetRemoval { var saved = false }
    private val pendingSetRemovals = mutableMapOf<SetWriteKey, PendingSetRemoval>()
    private val pendingSetAdds = mutableMapOf<Int, Int>()

    private fun hasPendingSetRemoval(exerciseId: Int): Boolean =
        pendingSetRemovals.keys.any { it.exerciseId == exerciseId }

    private fun removePendingSetRemoval(key: SetWriteKey) {
        pendingSetRemovals.remove(key)
        _uiState.update { it.copy(pendingSetRemovals = pendingSetRemovals.keys.toSet()) }
    }

    private fun reconcileSetRemovals(sessionId: Int, exercises: List<ExerciseUiModel>) {
        val acknowledged = pendingSetRemovals.filter { (key, removal) ->
            if (key.sessionId != sessionId || !removal.saved) return@filter false
            val exercise = exercises.find { it.exercise.id == key.exerciseId }
            exercise == null || (exercise.exercise.setCount == key.setNumber - 1 &&
                exercise.sets.none { it.setNumber == key.setNumber })
        }.keys
        if (acknowledged.isNotEmpty()) {
            acknowledged.forEach { pendingSetRemovals.remove(it) }
            _uiState.update { it.copy(pendingSetRemovals = pendingSetRemovals.keys.toSet()) }
        }
    }

    private fun hasPendingSetWrite(exerciseId: Int): Boolean =
        pendingSetWrites.keys.any { it.exerciseId == exerciseId }

    private fun removePendingSetWrite(key: SetWriteKey) {
        pendingSetWrites.remove(key)
        _uiState.update { it.copy(pendingSetWrites = pendingSetWrites.keys.toSet()) }
    }

    private fun reconcileSetWrites(sessionId: Int, exercises: List<ExerciseUiModel>) {
        val acknowledged = pendingSetWrites.filter { (key, write) ->
            if (key.sessionId != sessionId || !write.saved) return@filter false
            val exercise = exercises.find { it.exercise.id == key.exerciseId }
            val log = exercise?.sets?.find { it.setNumber == key.setNumber }?.currentLog
            exercise == null || if (write.isCompleted) {
                log?.isCompleted == true && log.weight == write.weight && log.reps == write.reps
            } else {
                log == null
            }
        }.keys
        if (acknowledged.isNotEmpty()) {
            acknowledged.forEach { pendingSetWrites.remove(it) }
            _uiState.update { it.copy(pendingSetWrites = pendingSetWrites.keys.toSet()) }
        }
    }

    // os mapas de estado sobrevivem a qualquer recomposição
    // o formato da chave é id do exercício e número da série
    val draftWeights = mutableStateMapOf<String, String>()
    val draftReps = mutableStateMapOf<String, String>()

    private fun draftKey(exerciseId: Int, setNumber: Int) = "${exerciseId}_${setNumber}"

    fun updateDraftWeight(exerciseId: Int, setNumber: Int, value: String) {
        if (pendingSetRemovals.keys.any { it.exerciseId == exerciseId && it.setNumber == setNumber }) return
        draftWeights[draftKey(exerciseId, setNumber)] = value
    }

    fun updateDraftReps(exerciseId: Int, setNumber: Int, value: String) {
        if (pendingSetRemovals.keys.any { it.exerciseId == exerciseId && it.setNumber == setNumber }) return
        draftReps[draftKey(exerciseId, setNumber)] = value
    }

    fun getDraftWeight(exerciseId: Int, setNumber: Int): String =
        draftWeights[draftKey(exerciseId, setNumber)] ?: ""

    fun getDraftReps(exerciseId: Int, setNumber: Int): String =
        draftReps[draftKey(exerciseId, setNumber)] ?: ""

    // guarda o trabalho ativo de seleção de treino para poder cancelar antes de começar outro
    private var selectWorkoutJob: kotlinx.coroutines.Job? = null

    init {
        // obter todos os treinos
        viewModelScope.launch {
            val anyActive = repository.getAnyActiveSession()
            repository.allWorkouts.collect { workouts ->
                _uiState.update { state ->
                    val activeWorkout = if (anyActive != null) {
                        workouts.find { it.id == anyActive.workoutId }
                    } else null
                    
                    val newSelected = state.selectedWorkout ?: activeWorkout ?: workouts.firstOrNull()
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
        // Saved rows no longer need a UI acknowledgement when this selection discards them.
        // In-flight commands keep their guards and remain owned by the ViewModel.
        pendingSetWrites.entries.removeAll { it.value.saved }
        pendingSetRemovals.entries.removeAll { it.value.saved }
        _uiState.update {
            it.copy(selectedWorkout = workout, activeSession = null, exercises = emptyList(),
                pendingSetWrites = pendingSetWrites.keys.toSet(),
                pendingSetRemovals = pendingSetRemovals.keys.toSet())
        }

        selectWorkoutJob = viewModelScope.launch {
            // iniciar ou obter a sessão
            val session = repository.startOrGetSession(workout.id)
            _uiState.update { it.copy(activeSession = session) }
            
            // Keep immutable history local to this selection; load before publishing actionable rows.
            val previousSetsByKey = repository.getPreviousSetsForWorkout(workout.id)
                .associateBy { it.exerciseId to it.setNumber }

            // observar exercícios para este treino
            repository.getExercisesForWorkout(workout.id).distinctUntilChanged()
                .combine(repository.getSetsForSession(session.id).distinctUntilChanged()) { exercises, setLogs ->
                    buildExerciseModels(exercises, setLogs, previousSetsByKey)
                }
                .flowOn(modelDispatcher)
                .collect { exerciseModels ->
                    _uiState.update { it.copy(exercises = exerciseModels) }
                    reconcileSetWrites(session.id, exerciseModels)
                    reconcileSetRemovals(session.id, exerciseModels)
                }
        }
    }

    private fun buildExerciseModels(
        exercises: List<ExerciseEntity>,
        setLogs: List<SetLogEntity>,
        previousSetsByKey: Map<Pair<Int, Int>, SetLogEntity>
    ): List<ExerciseUiModel> {
        val setsByExercise = setLogs.groupBy { it.exerciseId }
        return exercises.map { exercise ->
            val currentLogsByNumber = mutableMapOf<Int, SetLogEntity>()
            var maxSetNumber = exercise.setCount
            var maxCompleted = 0
            setsByExercise[exercise.id].orEmpty().forEach { log ->
                // Preserve first-log behavior if current-session data contains duplicates.
                currentLogsByNumber.putIfAbsent(log.setNumber, log)
                maxSetNumber = maxOf(maxSetNumber, log.setNumber)
                if (log.isCompleted) maxCompleted = maxOf(maxCompleted, log.setNumber)
            }

            val uiSets = (1..maxSetNumber).map { setNum ->
                val currentLog = currentLogsByNumber[setNum]
                val prevLog = previousSetsByKey[exercise.id to setNum]
                val isSetCompleted = currentLog?.isCompleted == true
                val isLastCompleted = setNum == maxCompleted
                val isNextAvailable = setNum == maxCompleted + 1
                SetUiModel(
                    setNumber = setNum,
                    currentLog = currentLog,
                    prevWeight = prevLog?.weight,
                    prevReps = prevLog?.reps,
                    isCompleted = isSetCompleted,
                    isLastCompleted = isLastCompleted,
                    canBeChecked = (!isSetCompleted && isNextAvailable) || (isSetCompleted && isLastCompleted),
                    canBeRemoved = setNum == maxSetNumber && maxSetNumber > 1
                )
            }
            ExerciseUiModel(exercise, uiSets)
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

    fun updateWorkoutRestTimer(seconds: Int) {
        val currentWorkout = _uiState.value.selectedWorkout ?: return
        viewModelScope.launch {
            val updatedWorkout = currentWorkout.copy(defaultRestTimer = seconds)
            repository.updateWorkout(updatedWorkout)
            // Atualizar o selectedWorkout no UI state
            _uiState.update { it.copy(selectedWorkout = updatedWorkout) }
        }
    }

    fun addSetToExercise(exerciseId: Int) {
        if (hasPendingSetRemoval(exerciseId)) return
        val exModel = _uiState.value.exercises.find { it.exercise.id == exerciseId } ?: return
        pendingSetAdds[exerciseId] = pendingSetAdds.getOrDefault(exerciseId, 0) + 1
        _uiState.update { it.copy(setAddFailed = false, addingSetExerciseIds = pendingSetAdds.keys.toSet()) }
        viewModelScope.launch {
            try {
                // Each tap is one increment; the repository uses the stored count, not this snapshot.
                repository.addSet(exModel.exercise)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(setAddFailed = true) }
            } finally {
                val remaining = pendingSetAdds.getValue(exerciseId) - 1
                if (remaining == 0) pendingSetAdds.remove(exerciseId) else pendingSetAdds[exerciseId] = remaining
                _uiState.update { it.copy(addingSetExerciseIds = pendingSetAdds.keys.toSet()) }
            }
        }
    }

    fun dismissSetAddError() {
        _uiState.update { it.copy(setAddFailed = false) }
    }

    fun removeLastSet(
        exerciseId: Int,
        expectedSetNumber: Int? = null,
        expectedSessionId: Int? = _uiState.value.activeSession?.id
    ) {
        val state = _uiState.value
        if (hasPendingSetWrite(exerciseId) || hasPendingSetRemoval(exerciseId) ||
            exerciseId in pendingSetAdds || state.isFinalizing) return
        val session = state.activeSession ?: return
        if (session.id != expectedSessionId) return
        val exModel = state.exercises.find { it.exercise.id == exerciseId } ?: return
        if (exModel.exercise.setCount <= 1) return
        val number = expectedSetNumber ?: exModel.exercise.setCount
        if (number != exModel.exercise.setCount || exModel.sets.find { it.setNumber == number }?.canBeRemoved != true) {
            _uiState.update { it.copy(setRemovalFailed = true) }
            return
        }
        val key = SetWriteKey(session.id, exerciseId, number)
        val removal = PendingSetRemoval()
        pendingSetRemovals[key] = removal
        _uiState.update { it.copy(pendingSetRemovals = pendingSetRemovals.keys.toSet(), setRemovalFailed = false) }
        viewModelScope.launch {
            try {
                if (repository.removeLastSet(exModel.exercise, session.id)) {
                    removal.saved = true
                    // Keep drafts through failure; clear only the set the transaction actually removed.
                    draftWeights.remove(draftKey(exerciseId, number))
                    draftReps.remove(draftKey(exerciseId, number))
                    if (_uiState.value.activeSession?.id == session.id) {
                        reconcileSetRemovals(session.id, _uiState.value.exercises)
                    } else {
                        removePendingSetRemoval(key)
                    }
                } else {
                    _uiState.update { it.copy(setRemovalFailed = true) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(setRemovalFailed = true) }
            } finally {
                if (!removal.saved) removePendingSetRemoval(key)
            }
        }
    }

    fun dismissSetRemovalError() {
        _uiState.update { it.copy(setRemovalFailed = false) }
    }

    fun deleteExercise(exerciseId: Int) {
        if (hasPendingSetWrite(exerciseId) || hasPendingSetRemoval(exerciseId) ||
            exerciseId in pendingSetAdds || _uiState.value.isFinalizing) return
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

    fun logSet(
        exerciseId: Int,
        setNumber: Int,
        weight: Float,
        reps: Int,
        isCompleted: Boolean,
        expectedSessionId: Int? = _uiState.value.activeSession?.id
    ) {
        val state = _uiState.value
        val session = state.activeSession ?: return
        if (state.isFinalizing || session.id != expectedSessionId || hasPendingSetWrite(exerciseId) ||
            hasPendingSetRemoval(exerciseId)) return
        val set = state.exercises.find { it.exercise.id == exerciseId }
            ?.sets?.find { it.setNumber == setNumber } ?: return
        // Validate the latest displayed state, not the row captured by a previous composition.
        if (!set.canBeChecked || set.isCompleted == isCompleted) return
        val key = SetWriteKey(session.id, exerciseId, setNumber)
        val write = PendingSetWrite(weight, reps, isCompleted)
        pendingSetWrites[key] = write
        _uiState.update { it.copy(pendingSetWrites = pendingSetWrites.keys.toSet(), setSaveFailed = false) }

        // Accepted saves survive navigation. Different exercises may save independently.
        viewModelScope.launch {
            try {
                repository.logSet(
                    sessionId = session.id,
                    exerciseId = exerciseId,
                    setNumber = setNumber,
                    weight = weight,
                    reps = reps,
                    isCompleted = isCompleted,
                    existingSetId = set.currentLog?.id ?: 0
                )
                write.saved = true
                if (_uiState.value.activeSession?.id == session.id) {
                    reconcileSetWrites(session.id, _uiState.value.exercises)
                } else {
                    // This session has no visible rows; its next selection reloads Room.
                    removePendingSetWrite(key)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(setSaveFailed = true) }
            } finally {
                if (!write.saved) removePendingSetWrite(key)
            }
        }
    }

    fun dismissSetSaveError() {
        _uiState.update { it.copy(setSaveFailed = false) }
    }
    fun moveExercise(from: Int, to: Int) {
        val currentExercises = _uiState.value.exercises.toMutableList()
        if (from in currentExercises.indices && to in currentExercises.indices) {
            val item = currentExercises.removeAt(from)
            currentExercises.add(to, item)
            
            // Atualizar UI imediatamente
            _uiState.update { it.copy(exercises = currentExercises) }
            
            // Guardar ordem na base de dados garantindo que usamos a lista certa
            viewModelScope.launch {
                val updatedEntities = currentExercises.mapIndexed { index, uiModel ->
                    uiModel.exercise.copy(orderIndex = index)
                }
                repository.reorderExercises(updatedEntities)
            }
        }
    }

    fun finalizeWorkout(expectedSessionId: Int? = _uiState.value.activeSession?.id) {
        val state = _uiState.value
        val session = state.activeSession ?: return
        // Reject duplicate taps, including callbacks from the previous session's UI.
        if (state.isFinalizing || session.id != expectedSessionId ||
            state.pendingSetWrites.any { it.sessionId == session.id } ||
            state.pendingSetRemovals.any { it.sessionId == session.id }) return
        val exerciseIds = state.exercises.map { it.exercise.id }.toSet()
        _uiState.update { it.copy(isFinalizing = true, finalizationFailed = false) }

        // Saving belongs to the ViewModel, so leaving the screen cannot cancel it.
        viewModelScope.launch {
            try {
                repository.finalizeSession(session)
                // A different routine may now be visible; preserve its drafts and selection.
                val keysToClear = (draftWeights.keys + draftReps.keys).filter {
                    it.substringBefore('_').toIntOrNull() in exerciseIds
                }
                keysToClear.forEach {
                    draftWeights.remove(it)
                    draftReps.remove(it)
                }
                _uiState.update { it.copy(finalizedSessionId = session.id) }
                if (_uiState.value.activeSession?.id == session.id) {
                    selectWorkout(_uiState.value.selectedWorkout)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(finalizationFailed = true) }
            } finally {
                _uiState.update { it.copy(isFinalizing = false) }
            }
        }
    }

    fun acknowledgeFinalization(sessionId: Int) {
        _uiState.update {
            if (it.finalizedSessionId == sessionId) it.copy(finalizedSessionId = null) else it
        }
    }

    fun dismissFinalizationError() {
        _uiState.update { it.copy(finalizationFailed = false) }
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
