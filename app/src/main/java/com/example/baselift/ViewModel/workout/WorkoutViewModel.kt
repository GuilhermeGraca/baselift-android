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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
data class ExerciseEditState(
    val exerciseId: Int,
    val workoutId: Int,
    val name: String,
    val equipment: String,
    val muscleGroups: String,
    val isOpen: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false
)

@Stable
data class WorkoutUiState(
    val workouts: List<WorkoutEntity> = emptyList(),
    val selectedWorkout: WorkoutEntity? = null,
    val activeSession: WorkoutSessionEntity? = null,
    val exercises: List<ExerciseUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val workoutsLoadFailed: Boolean = false,
    val isSelectionLoading: Boolean = false,
    val selectionLoadFailed: Boolean = false,
    val exerciseEdits: Map<Int, ExerciseEditState> = emptyMap(),
    val isFinalizing: Boolean = false,
    val finalizationFailed: Boolean = false,
    val finalizedSessionId: Int? = null,
    val pendingSetWrites: Set<SetWriteKey> = emptySet(),
    val setSaveFailed: Boolean = false,
    val setAddFailed: Boolean = false,
    val pendingSetRemovals: Set<SetWriteKey> = emptySet(),
    val addingSetExerciseIds: Set<Int> = emptySet(),
    val setRemovalFailed: Boolean = false,
    val reorderFailedWorkoutIds: Set<Int> = emptySet()
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
    private class PendingExerciseEdit(val draft: ExerciseEditState) { var saved = false }
    private val pendingExerciseEdits = mutableMapOf<Int, PendingExerciseEdit>()

    private fun finishExerciseEdit(exerciseId: Int) {
        pendingExerciseEdits.remove(exerciseId)
        _uiState.update { it.copy(exerciseEdits = it.exerciseEdits - exerciseId) }
    }

    private fun reconcileExerciseEdits(models: List<ExerciseUiModel>) {
        if (_uiState.value.exerciseEdits.isEmpty()) return
        val byId = models.associateBy { it.exercise.id }
        val removed = _uiState.value.exerciseEdits.values.filter {
            it.workoutId == _uiState.value.selectedWorkout?.id && it.exerciseId !in byId &&
                it.exerciseId !in pendingExerciseEdits
        }.map { it.exerciseId }
        removed.forEach(::finishExerciseEdit)
        val finished = pendingExerciseEdits.filter { (_, request) ->
            if (!request.saved || request.draft.workoutId != _uiState.value.selectedWorkout?.id) return@filter false
            val row = byId[request.draft.exerciseId]?.exercise
            row == null || (row.name == request.draft.name && row.equipment == request.draft.equipment &&
                row.muscleGroups == request.draft.muscleGroups)
        }.keys.toList()
        finished.forEach(::finishExerciseEdit)
    }

    fun beginExerciseEdit(exerciseId: Int) {
        if (!hasCurrentExerciseModels()) return
        val row = _uiState.value.exercises.find { it.exercise.id == exerciseId }?.exercise ?: return
        _uiState.update { state ->
            val draft = state.exerciseEdits[exerciseId] ?: ExerciseEditState(
                row.id, row.workoutId, row.name, row.equipment, row.muscleGroups)
            state.copy(exerciseEdits = state.exerciseEdits.mapValues { (_, edit) -> edit.copy(isOpen = false) } +
                (exerciseId to draft.copy(isOpen = true)))
        }
    }

    fun updateExerciseEditDraft(
        exerciseId: Int, name: String? = null, equipment: String? = null, muscleGroups: String? = null
    ) {
        _uiState.update { state ->
            val draft = state.exerciseEdits[exerciseId] ?: return@update state
            if (draft.isSaving) return@update state
            state.copy(exerciseEdits = state.exerciseEdits + (exerciseId to draft.copy(
                name = name ?: draft.name, equipment = equipment ?: draft.equipment,
                muscleGroups = muscleGroups ?: draft.muscleGroups, saveFailed = false)))
        }
    }

    fun dismissExerciseEdit(exerciseId: Int) {
        _uiState.update { state ->
            val draft = state.exerciseEdits[exerciseId] ?: return@update state
            state.copy(exerciseEdits = if (draft.isSaving) {
                state.exerciseEdits + (exerciseId to draft.copy(isOpen = false))
            } else state.exerciseEdits - exerciseId)
        }
    }

    fun saveExerciseEdit(exerciseId: Int) {
        val draft = _uiState.value.exerciseEdits[exerciseId] ?: return
        if (!draft.isOpen || draft.workoutId != _uiState.value.selectedWorkout?.id) return
        updateExercise(exerciseId, draft.name, draft.equipment, draft.muscleGroups)
    }

    private class PendingReorder(val exercises: List<ExerciseEntity>) {
        val rankById = exercises.associate { it.id to it.orderIndex }
        var saved = false
    }

    // One durable worker per routine; only its newest unsaved order is queued.
    private val pendingReorders = mutableMapOf<Int, PendingReorder>()
    private val reorderJobs = mutableMapOf<Int, Job>()
    private var observedExerciseModels: List<ExerciseUiModel>? = null
    private var observingSelection = false

    private fun hasCurrentExerciseModels() = routinesReady.value && observingSelection && observedExerciseModels != null

    private fun releaseSavedAcknowledgements() {
        pendingSetWrites.entries.removeAll { it.value.saved }
        pendingSetRemovals.entries.removeAll { it.value.saved }
        pendingReorders.entries.removeAll { it.value.saved }
        pendingExerciseEdits.filterValues { it.saved }.keys.toList().forEach(::finishExerciseEdit)
        _uiState.update { it.copy(pendingSetWrites = pendingSetWrites.keys.toSet(),
            pendingSetRemovals = pendingSetRemovals.keys.toSet()) }
    }

    private fun displayedExerciseOrder(workoutId: Int, models: List<ExerciseUiModel>): List<ExerciseUiModel> {
        val pending = pendingReorders[workoutId] ?: return models
        if (pending.saved && models.all { model ->
                pending.rankById[model.exercise.id]?.let { it == model.exercise.orderIndex } ?: true
            }) {
            pendingReorders.remove(workoutId)
            return models
        }
        // Sort fresh models, retaining incoming set/metadata changes and newly added rows.
        return models.sortedBy { pending.rankById[it.exercise.id] ?: Int.MAX_VALUE }
    }

    private fun refreshDisplayedExerciseOrder(workoutId: Int) {
        if (_uiState.value.selectedWorkout?.id != workoutId) return
        val models = observedExerciseModels ?: return
        val displayed = displayedExerciseOrder(workoutId, models)
        _uiState.update { it.copy(exercises = displayed) }
    }

    private fun startReorderWorker(workoutId: Int) {
        if (workoutId in reorderJobs) return
        // Register before starting: an immediate repository can finish without suspending.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                while (true) {
                    val request = pendingReorders[workoutId]?.takeUnless { it.saved } ?: break
                    try {
                        repository.reorderExercises(request.exercises)
                        request.saved = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A superseded failure must not discard or interrupt the newer intent.
                        if (pendingReorders[workoutId] === request) {
                            pendingReorders.remove(workoutId)
                            _uiState.update {
                                it.copy(reorderFailedWorkoutIds = it.reorderFailedWorkoutIds + workoutId)
                            }
                            refreshDisplayedExerciseOrder(workoutId)
                        }
                    }
                    if (pendingReorders[workoutId] === request && request.saved) {
                        if (_uiState.value.selectedWorkout?.id == workoutId && observingSelection) {
                            refreshDisplayedExerciseOrder(workoutId)
                        } else {
                            // Returning to this routine will query its saved order afresh.
                            pendingReorders.remove(workoutId)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                pendingReorders.remove(workoutId)
                refreshDisplayedExerciseOrder(workoutId)
                throw cancelled
            } finally {
                reorderJobs.remove(workoutId)
            }
        }
        reorderJobs[workoutId] = job
        job.start()
    }

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
    private var selectionToken: Any = Any()
    // True only after the routine list has emitted for the current presentation demand.
    private val routinesReady = MutableStateFlow(false)
    private val routinesRetry = MutableStateFlow(0)
    private var recoveredInitialSelection = false
    private var recoveredWorkoutId: Int? = null

    init {
        viewModelScope.launch {
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_000)
                .command(_uiState.subscriptionCount).collectLatest { command ->
                    if (command != SharingCommand.START) return@collectLatest
                    routinesRetry.collectLatest {
                        _uiState.update { it.copy(isLoading = true, workoutsLoadFailed = false) }
                        try {
                            // Recover the initial routine once, only when Workout is first requested.
                            if (!recoveredInitialSelection) {
                                val active = repository.getAnyActiveSession()
                                currentCoroutineContext().ensureActive()
                                recoveredWorkoutId = active?.workoutId
                                recoveredInitialSelection = true
                            }
                            repository.allWorkouts.collect { workouts ->
                                currentCoroutineContext().ensureActive()
                                val selectedId = _uiState.value.selectedWorkout?.id
                                val selected = workouts.find { it.id == selectedId }
                                    ?: workouts.find { it.id == recoveredWorkoutId }
                                    ?: workouts.firstOrNull()
                                _uiState.update {
                                    it.copy(workouts = workouts, isLoading = false,
                                        selectedWorkout = if (selected?.id == selectedId) selected else it.selectedWorkout)
                                }
                                // Validate the selected ID before allowing current-row observation.
                                if (selected?.id != selectedId) loadWorkout(selected)
                                routinesReady.value = true
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            currentCoroutineContext().ensureActive()
                            _uiState.update { it.copy(isLoading = false, workoutsLoadFailed = true) }
                        } finally {
                            routinesReady.value = false
                            _uiState.update { it.copy(isLoading = !it.workoutsLoadFailed) }
                        }
                    }
                }
        }
    }

    fun retryWorkouts() {
        val state = _uiState.value
        if (!state.workoutsLoadFailed || state.isLoading || _uiState.subscriptionCount.value == 0) return
        _uiState.update { it.copy(isLoading = true, workoutsLoadFailed = false) }
        routinesRetry.value += 1
    }

    fun selectWorkout(workout: WorkoutEntity?) {
        if (!routinesReady.value) return
        if (workout != null && _uiState.value.workouts.none { it.id == workout.id }) return
        loadWorkout(workout)
    }

    fun retryWorkoutSelection(expectedWorkoutId: Int) {
        val state = _uiState.value
        if (routinesReady.value && state.selectedWorkout?.id == expectedWorkoutId && state.selectionLoadFailed) {
            loadWorkout(state.selectedWorkout, force = true)
        }
    }

    private fun loadWorkout(workout: WorkoutEntity?, force: Boolean = false) {
        if (!force && workout != null && _uiState.value.selectedWorkout?.id == workout.id &&
            selectWorkoutJob?.isActive == true) return

        val token = Any()
        selectionToken = token

        // cancelar a coleção anterior antes de começar uma nova
        selectWorkoutJob?.cancel()
        // Saved rows no longer need a UI acknowledgement when this selection discards them.
        // In-flight commands keep their guards and remain owned by the ViewModel.
        observingSelection = false
        releaseSavedAcknowledgements()
        observedExerciseModels = null
        _uiState.update {
            it.copy(selectedWorkout = workout, activeSession = null, exercises = emptyList(),
                isSelectionLoading = workout != null, selectionLoadFailed = false,
                pendingSetWrites = pendingSetWrites.keys.toSet(),
                pendingSetRemovals = pendingSetRemovals.keys.toSet())
        }
        if (workout == null) {
            selectWorkoutJob = null
            return
        }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                // A post-finalization renewal may be requested after presentation has stopped.
                routinesReady.first { it }
                // iniciar ou obter a sessão
                val session = repository.startOrGetSession(workout.id)
                currentCoroutineContext().ensureActive()
                if (selectionToken !== token) return@launch
                _uiState.update { it.copy(activeSession = session) }

                // Keep immutable history local to this selection; load before publishing actionable rows.
                val previousSets = repository.getPreviousSetsForWorkout(workout.id)
                currentCoroutineContext().ensureActive()
                if (selectionToken !== token) return@launch
                val previousSetsByKey = previousSets.associateBy { it.exerciseId to it.setNumber }

                // Presentation demand owns Room observation, not accepted durable commands.
                // Keep this selection's session/history across tab changes; resume only its rows.
                routinesReady.collectLatest { ready ->
                    if (!ready) return@collectLatest
                    observingSelection = true
                    _uiState.update { it.copy(isSelectionLoading = true) }
                    try {
                        repository.getExercisesForWorkout(workout.id).distinctUntilChanged()
                            .combine(repository.getSetsForSession(session.id).distinctUntilChanged()) { exercises, setLogs ->
                                buildExerciseModels(exercises, setLogs, previousSetsByKey)
                            }
                            .flowOn(modelDispatcher)
                            .collect { exerciseModels ->
                                if (selectionToken !== token || _uiState.value.selectedWorkout?.id != workout.id ||
                                    _uiState.value.activeSession?.id != session.id) return@collect
                                observedExerciseModels = exerciseModels
                                val displayed = displayedExerciseOrder(workout.id, exerciseModels)
                                _uiState.update { it.copy(exercises = displayed, isSelectionLoading = false) }
                                reconcileSetWrites(session.id, exerciseModels)
                                reconcileSetRemovals(session.id, exerciseModels)
                                reconcileExerciseEdits(exerciseModels)
                            }
                    } finally {
                        if (selectionToken === token) {
                            observingSelection = false
                            observedExerciseModels = null
                            releaseSavedAcknowledgements()
                            _uiState.update { it.copy(isSelectionLoading = true) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (selectionToken === token) {
                    pendingExerciseEdits.filterValues { it.saved }.keys.toList().forEach(::finishExerciseEdit)
                    observedExerciseModels = null
                    _uiState.update {
                        it.copy(activeSession = null, exercises = emptyList(), selectionLoadFailed = true)
                    }
                }
            } finally {
                if (selectionToken === token) {
                    _uiState.update { it.copy(isSelectionLoading = false) }
                }
            }
        }
        selectWorkoutJob = job
        job.start()
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
        if (!routinesReady.value) return
        val count = _uiState.value.workouts.size
        viewModelScope.launch {
            repository.createWorkout(name, count)
        }
    }

    fun deleteWorkout(workout: WorkoutEntity) {
        if (!routinesReady.value) return
        viewModelScope.launch {
            repository.deleteWorkout(workout)
            // The next current routine-list emission owns replacement selection, including offscreen saves.
        }
    }

    fun createExercise(name: String, equipment: String, muscleGroups: String) {
        if (!hasCurrentExerciseModels()) return
        val currentWorkoutId = _uiState.value.selectedWorkout?.id ?: return
        viewModelScope.launch {
            val count = _uiState.value.exercises.size
            repository.createExercise(currentWorkoutId, name, equipment, muscleGroups, count)
        }
    }

    fun updateWorkoutRestTimer(seconds: Int) {
        if (!routinesReady.value) return
        val currentWorkout = _uiState.value.selectedWorkout ?: return
        viewModelScope.launch {
            val updatedWorkout = currentWorkout.copy(defaultRestTimer = seconds)
            repository.updateWorkout(updatedWorkout)
            // Atualizar o selectedWorkout no UI state
            _uiState.update {
                if (it.selectedWorkout?.id == updatedWorkout.id) it.copy(selectedWorkout = updatedWorkout) else it
            }
        }
    }

    fun addSetToExercise(exerciseId: Int) {
        if (!hasCurrentExerciseModels()) return
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
        if (!hasCurrentExerciseModels()) return
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
                    if (_uiState.value.activeSession?.id == session.id && observingSelection) {
                        observedExerciseModels?.let { reconcileSetRemovals(session.id, it) }
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
        if (!hasCurrentExerciseModels()) return
        if (hasPendingSetWrite(exerciseId) || hasPendingSetRemoval(exerciseId) ||
            exerciseId in pendingSetAdds || exerciseId in pendingExerciseEdits || _uiState.value.isFinalizing) return
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
        if (!hasCurrentExerciseModels()) return
        if (name.isBlank() || exerciseId in pendingExerciseEdits) return
        val exModel = _uiState.value.exercises.find { it.exercise.id == exerciseId } ?: return
        val draft = ExerciseEditState(exerciseId, exModel.exercise.workoutId, name, equipment, muscleGroups,
            isOpen = _uiState.value.exerciseEdits[exerciseId]?.isOpen == true, isSaving = true)
        val request = PendingExerciseEdit(draft)
        pendingExerciseEdits[exerciseId] = request
        _uiState.update { it.copy(exerciseEdits = it.exerciseEdits + (exerciseId to draft)) }
        viewModelScope.launch {
            try {
                repository.updateExercise(exModel.exercise, name, equipment, muscleGroups)
                request.saved = true
                if (_uiState.value.selectedWorkout?.id != draft.workoutId ||
                    _uiState.value.selectionLoadFailed || !observingSelection) {
                    finishExerciseEdit(exerciseId)
                } else {
                    observedExerciseModels?.let(::reconcileExerciseEdits)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { state ->
                    val current = state.exerciseEdits[exerciseId] ?: draft
                    state.copy(exerciseEdits = state.exerciseEdits + (exerciseId to current.copy(saveFailed = true)))
                }
            } finally {
                if (!request.saved) {
                    pendingExerciseEdits.remove(exerciseId)
                    _uiState.update { state ->
                        val current = state.exerciseEdits[exerciseId] ?: return@update state
                        state.copy(exerciseEdits = state.exerciseEdits + (exerciseId to current.copy(isSaving = false)))
                    }
                }
            }
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
        if (!hasCurrentExerciseModels()) return
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
                if (_uiState.value.activeSession?.id == session.id && observingSelection) {
                    observedExerciseModels?.let { reconcileSetWrites(session.id, it) }
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
    fun moveExercise(
        from: Int,
        to: Int,
        expectedWorkoutId: Int? = _uiState.value.selectedWorkout?.id,
        expectedExerciseId: Int? = null
    ) {
        val state = _uiState.value
        if (!hasCurrentExerciseModels()) return
        val workoutId = state.selectedWorkout?.id ?: return
        val currentExercises = state.exercises.toMutableList()
        if (workoutId != expectedWorkoutId || from !in currentExercises.indices ||
            to !in currentExercises.indices || from == to ||
            (expectedExerciseId != null && currentExercises[from].exercise.id != expectedExerciseId)) return
        val item = currentExercises.removeAt(from)
        currentExercises.add(to, item)
        pendingReorders[workoutId] = PendingReorder(currentExercises.mapIndexed { index, model ->
            model.exercise.copy(orderIndex = index)
        })
        _uiState.update {
            it.copy(exercises = currentExercises, reorderFailedWorkoutIds = it.reorderFailedWorkoutIds - workoutId)
        }
        startReorderWorker(workoutId)
    }

    fun dismissReorderError(workoutId: Int) {
        _uiState.update { it.copy(reorderFailedWorkoutIds = it.reorderFailedWorkoutIds - workoutId) }
    }

    fun finalizeWorkout(expectedSessionId: Int? = _uiState.value.activeSession?.id) {
        if (!hasCurrentExerciseModels()) return
        val state = _uiState.value
        val session = state.activeSession ?: return
        // Reject duplicate taps, including callbacks from the previous session's UI.
        if (state.isFinalizing || state.isSelectionLoading || state.selectionLoadFailed ||
            session.id != expectedSessionId ||
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
                    loadWorkout(_uiState.value.selectedWorkout, force = true)
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
