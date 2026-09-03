package com.example.baselift.fakes

import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.IWorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWorkoutRepository : IWorkoutRepository {
    private val workoutsFlow = MutableStateFlow<List<WorkoutEntity>>(emptyList())
    private val exercisesFlow = MutableStateFlow<List<ExerciseEntity>>(emptyList())
    private val sessionsFlow = MutableStateFlow<List<WorkoutSessionEntity>>(emptyList())
    private val setLogsFlow = MutableStateFlow<List<SetLogEntity>>(emptyList())

    private var nextWorkoutId = 1
    private var nextExerciseId = 1
    private var nextSessionId = 1
    private var nextSetLogId = 1

    override val allWorkouts: Flow<List<WorkoutEntity>> = workoutsFlow.map { list ->
        list.sortedBy { it.orderIndex }
    }

    override val allExercises: Flow<List<ExerciseEntity>> = exercisesFlow.map { list ->
        list.sortedBy { it.orderIndex }
    }

    override suspend fun createWorkout(name: String, orderIndex: Int): Int {
        val id = nextWorkoutId++
        val workout = WorkoutEntity(id = id, name = name, orderIndex = orderIndex)
        workoutsFlow.value = workoutsFlow.value + workout
        return id
    }

    override suspend fun deleteWorkout(workout: WorkoutEntity) {
        workoutsFlow.value = workoutsFlow.value.filter { it.id != workout.id }
        exercisesFlow.value = exercisesFlow.value.filter { it.workoutId != workout.id }
        // Em Room há onDelete = CASCADE para exercícios, sessões e logs.
        val sessionsToDelete = sessionsFlow.value.filter { it.workoutId == workout.id }.map { it.id }
        sessionsFlow.value = sessionsFlow.value.filterNot { it.workoutId == workout.id }
        setLogsFlow.value = setLogsFlow.value.filterNot { it.sessionId in sessionsToDelete }
    }

    override fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>> {
        return exercisesFlow.map { list ->
            list.filter { it.workoutId == workoutId }.sortedBy { it.orderIndex }
        }
    }

    override suspend fun createExercise(workoutId: Int, name: String, equipment: String, muscleGroups: String, orderIndex: Int) {
        val exercise = ExerciseEntity(
            id = nextExerciseId++,
            workoutId = workoutId,
            name = name,
            equipment = equipment,
            muscleGroups = muscleGroups,
            orderIndex = orderIndex,
            setCount = 1
        )
        exercisesFlow.value = exercisesFlow.value + exercise
    }

    override suspend fun updateExercise(exercise: ExerciseEntity, name: String, equipment: String, muscleGroups: String) {
        val updated = exercise.copy(name = name, equipment = equipment, muscleGroups = muscleGroups)
        val index = exercisesFlow.value.indexOfFirst { it.id == exercise.id }
        if (index != -1) {
            val newList = exercisesFlow.value.toMutableList()
            newList[index] = updated
            exercisesFlow.value = newList
        }
    }

    override suspend fun deleteExercise(exercise: ExerciseEntity) {
        exercisesFlow.value = exercisesFlow.value.filter { it.id != exercise.id }
        setLogsFlow.value = setLogsFlow.value.filter { it.exerciseId != exercise.id }
    }

    override suspend fun removeLastSet(exercise: ExerciseEntity, sessionId: Int) {
        val newCount = maxOf(1, exercise.setCount - 1)
        val setNumberToRemove = exercise.setCount
        
        // Remove from set logs
        setLogsFlow.value = setLogsFlow.value.filterNot { 
            it.exerciseId == exercise.id && it.sessionId == sessionId && it.setNumber == setNumberToRemove 
        }

        // Update exercise set count
        val index = exercisesFlow.value.indexOfFirst { it.id == exercise.id }
        if (index != -1) {
            val newList = exercisesFlow.value.toMutableList()
            newList[index] = exercise.copy(setCount = newCount)
            exercisesFlow.value = newList
        }
    }

    override suspend fun addSet(exercise: ExerciseEntity) {
        val index = exercisesFlow.value.indexOfFirst { it.id == exercise.id }
        if (index != -1) {
            val newList = exercisesFlow.value.toMutableList()
            newList[index] = exercise.copy(setCount = exercise.setCount + 1)
            exercisesFlow.value = newList
        }
    }

    override fun getActiveSessionFlow(workoutId: Int): Flow<WorkoutSessionEntity?> {
        return sessionsFlow.map { list ->
            list.find { it.workoutId == workoutId && !it.isCompleted }
        }
    }

    override suspend fun startOrGetSession(workoutId: Int): WorkoutSessionEntity {
        var session = sessionsFlow.value.find { it.workoutId == workoutId && !it.isCompleted }
        if (session == null) {
            val id = nextSessionId++
            session = WorkoutSessionEntity(id = id, workoutId = workoutId, timestamp = System.currentTimeMillis())
            sessionsFlow.value = sessionsFlow.value + session
        }
        return session
    }

    override suspend fun finalizeSession(session: WorkoutSessionEntity) {
        val index = sessionsFlow.value.indexOfFirst { it.id == session.id }
        if (index != -1) {
            val newList = sessionsFlow.value.toMutableList()
            newList[index] = session.copy(isCompleted = true, endTime = System.currentTimeMillis())
            sessionsFlow.value = newList
        }
    }

    override fun getSetsForSession(sessionId: Int): Flow<List<SetLogEntity>> {
        return setLogsFlow.map { list ->
            list.filter { it.sessionId == sessionId }
        }
    }

    override suspend fun getPreviousSet(exerciseId: Int, setNumber: Int): SetLogEntity? {
        // Encontrar a última sessão completa que tenha este exercício
        val previousSessionId = setLogsFlow.value
            .filter { it.exerciseId == exerciseId && it.isCompleted }
            .maxByOrNull { it.id }?.sessionId

        if (previousSessionId != null) {
            return setLogsFlow.value.find { it.sessionId == previousSessionId && it.exerciseId == exerciseId && it.setNumber == setNumber }
        }
        return null
    }

    override suspend fun checkPR(exerciseId: Int, weight: Float, reps: Int): String {
        val logsForExercise = setLogsFlow.value.filter { it.exerciseId == exerciseId && it.isCompleted }
        val maxWeight = logsForExercise.maxOfOrNull { it.weight } ?: 0f
        
        // estimate 1RM = weight * (1 + reps/30)
        val max1RM = logsForExercise.maxOfOrNull { it.weight * (1.0f + (it.reps / 30.0f)) } ?: 0f

        val current1RM = weight * (1.0f + (reps / 30.0f))
        val epsilon = 0.01f

        return when {
            weight > maxWeight + epsilon -> "WEIGHT"
            current1RM > max1RM + epsilon -> "VOLUME"
            else -> "NONE"
        }
    }

    override suspend fun logSet(sessionId: Int, exerciseId: Int, setNumber: Int, weight: Float, reps: Int, isCompleted: Boolean, existingSetId: Int) {
        if (!isCompleted) {
            if (existingSetId != 0) {
                setLogsFlow.value = setLogsFlow.value.filter { it.id != existingSetId }
            }
            return
        }

        var prType = "NONE"
        if (weight > 0 && reps > 0) {
            prType = checkPR(exerciseId, weight, reps)
        }

        val finalSetLog = SetLogEntity(
            id = if (existingSetId > 0) existingSetId else nextSetLogId++,
            sessionId = sessionId,
            exerciseId = exerciseId,
            setNumber = setNumber,
            weight = weight,
            reps = reps,
            isCompleted = true,
            prType = prType
        )

        val currentList = setLogsFlow.value.toMutableList()
        if (existingSetId > 0) {
            val index = currentList.indexOfFirst { it.id == existingSetId }
            if (index != -1) currentList[index] = finalSetLog
            else currentList.add(finalSetLog)
        } else {
            currentList.add(finalSetLog)
        }
        setLogsFlow.value = currentList
    }

    override fun getAllCompletedSessions(): Flow<List<WorkoutSessionEntity>> {
        return sessionsFlow.map { list -> list.filter { it.isCompleted } }
    }

    override fun getAllCompletedSetLogs(): Flow<List<SetLogEntity>> {
        return setLogsFlow.map { list -> list.filter { it.isCompleted } }
    }

    override fun getCompletedSetLogsForExercise(exerciseId: Int): Flow<List<SetLogEntity>> {
        return setLogsFlow.map { list -> list.filter { it.exerciseId == exerciseId && it.isCompleted } }
    }

    override fun getCompletedSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSessionEntity>> {
        return sessionsFlow.map { list -> list.filter { it.workoutId == workoutId && it.isCompleted } }
    }

    override suspend fun clearAllWorkoutData() {
        workoutsFlow.value = emptyList()
        exercisesFlow.value = emptyList()
        sessionsFlow.value = emptyList()
        setLogsFlow.value = emptyList()
        nextWorkoutId = 1
        nextExerciseId = 1
        nextSessionId = 1
        nextSetLogId = 1
    }
}
