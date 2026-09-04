package com.example.baselift.Model.repository

import com.example.baselift.Model.local.dao.WorkoutDao
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow
import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.ExistingWorkPolicy
import java.util.concurrent.TimeUnit
import com.example.baselift.Model.worker.WorkoutReminderWorker

/**
 * Interface do repositório de treino.
 * Permite criar implementações fake para testes.
 */
interface IWorkoutRepository {
    val allWorkouts: Flow<List<WorkoutEntity>>
    val allExercises: Flow<List<ExerciseEntity>>
    suspend fun createWorkout(name: String, orderIndex: Int = 0): Int
    suspend fun deleteWorkout(workout: WorkoutEntity)
    fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>>
    suspend fun createExercise(workoutId: Int, name: String, equipment: String, muscleGroups: String, orderIndex: Int = 0)
    suspend fun updateExercise(exercise: ExerciseEntity, name: String, equipment: String, muscleGroups: String)
    suspend fun deleteExercise(exercise: ExerciseEntity)
    suspend fun removeLastSet(exercise: ExerciseEntity, sessionId: Int)
    suspend fun addSet(exercise: ExerciseEntity)
    fun getActiveSessionFlow(workoutId: Int): Flow<WorkoutSessionEntity?>
    suspend fun startOrGetSession(workoutId: Int): WorkoutSessionEntity
    suspend fun finalizeSession(session: WorkoutSessionEntity)
    fun getSetsForSession(sessionId: Int): Flow<List<SetLogEntity>>
    suspend fun getPreviousSet(exerciseId: Int, setNumber: Int): SetLogEntity?
    suspend fun checkPR(exerciseId: Int, weight: Float, reps: Int): String
    suspend fun logSet(sessionId: Int, exerciseId: Int, setNumber: Int, weight: Float, reps: Int, isCompleted: Boolean, existingSetId: Int = 0)
    fun getAllCompletedSessions(): Flow<List<WorkoutSessionEntity>>
    fun getAllCompletedSetLogs(): Flow<List<SetLogEntity>>
    fun getCompletedSetLogsForExercise(exerciseId: Int): Flow<List<SetLogEntity>>
    fun getCompletedSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSessionEntity>>
    suspend fun getAnyActiveSession(): WorkoutSessionEntity?
    suspend fun clearAllWorkoutData()
}

/**
 * Implementação real que delega para o WorkoutDao (Room).
 */
class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val context: Context
) : IWorkoutRepository {

    // --- WORKOUT TEMPLATES ---
    override val allWorkouts: Flow<List<WorkoutEntity>> = workoutDao.getAllWorkouts()

    override suspend fun createWorkout(name: String, orderIndex: Int): Int {
        val workout = WorkoutEntity(name = name, orderIndex = orderIndex)
        return workoutDao.insertWorkout(workout).toInt()
    }

    override suspend fun deleteWorkout(workout: WorkoutEntity) {
        workoutDao.deleteWorkout(workout)
    }

    // --- EXERCISES ---
    override val allExercises: Flow<List<ExerciseEntity>> = workoutDao.getAllExercises()

    override fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>> {
        return workoutDao.getExercisesForWorkout(workoutId)
    }

    override suspend fun createExercise(workoutId: Int, name: String, equipment: String, muscleGroups: String, orderIndex: Int) {
        val exercise = ExerciseEntity(
            workoutId = workoutId,
            name = name,
            equipment = equipment,
            muscleGroups = muscleGroups,
            orderIndex = orderIndex,
            setCount = 1 // 1 série por defeito ao criar
        )
        workoutDao.insertExercise(exercise)
    }

    override suspend fun updateExercise(exercise: ExerciseEntity, name: String, equipment: String, muscleGroups: String) {
        workoutDao.updateExercise(exercise.copy(name = name, equipment = equipment, muscleGroups = muscleGroups))
    }

    override suspend fun deleteExercise(exercise: ExerciseEntity) {
        workoutDao.deleteExercise(exercise)
    }

    /** decrementa o número de séries e apaga o registo na sessão atual */
    override suspend fun removeLastSet(exercise: ExerciseEntity, sessionId: Int) {
        val newCount = maxOf(1, exercise.setCount - 1)
        val setNumberToRemove = exercise.setCount
        workoutDao.deleteSetByNumber(exercise.id, sessionId, setNumberToRemove)
        workoutDao.updateExercise(exercise.copy(setCount = newCount))
    }

    /** incrementa o número de séries */
    override suspend fun addSet(exercise: ExerciseEntity) {
        workoutDao.updateExercise(exercise.copy(setCount = exercise.setCount + 1))
    }

    // --- SESSIONS ---
    override fun getActiveSessionFlow(workoutId: Int): Flow<WorkoutSessionEntity?> {
        return workoutDao.getActiveSessionFlow(workoutId)
    }

    override suspend fun getAnyActiveSession(): WorkoutSessionEntity? {
        return workoutDao.getAnyActiveSession()
    }

    override suspend fun startOrGetSession(workoutId: Int): WorkoutSessionEntity {
        var session = workoutDao.getActiveSession(workoutId)
        if (session == null) {
            val newSession = WorkoutSessionEntity(
                workoutId = workoutId,
                timestamp = System.currentTimeMillis()
            )
            val id = workoutDao.insertSession(newSession)
            session = newSession.copy(id = id.toInt())
        }
        
        // Agendar notificação para daqui a 5 horas
        val data = workDataOf("SESSION_ID" to session.id)
        val workRequest = OneTimeWorkRequestBuilder<WorkoutReminderWorker>()
            .setInitialDelay(5, TimeUnit.HOURS)
            .setInputData(data)
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "REMINDER_${session.id}", 
            ExistingWorkPolicy.REPLACE, 
            workRequest
        )
        
        return session
    }

    override suspend fun finalizeSession(session: WorkoutSessionEntity) {
        val completedSession = session.copy(
            isCompleted = true,
            endTime = System.currentTimeMillis()
        )
        workoutDao.updateSession(completedSession)
        
        // Cancelar notificação agendada
        WorkManager.getInstance(context).cancelUniqueWork("REMINDER_${session.id}")
    }

    // --- SETS & PR LOGIC ---
    override fun getSetsForSession(sessionId: Int): Flow<List<SetLogEntity>> {
        return workoutDao.getSetsForSession(sessionId)
    }

    override suspend fun getPreviousSet(exerciseId: Int, setNumber: Int): SetLogEntity? {
        return workoutDao.getPreviousSet(exerciseId, setNumber)
    }

    /**
     * verifica se uma série é PR e retorna o tipo de recorde
     * usa um pequeno valor para evitar falsos positivos
     * o troféu só é dado quando o novo valor é maior que o anterior
     */
    override suspend fun checkPR(exerciseId: Int, weight: Float, reps: Int): String {
        val maxWeight = workoutDao.getMaxWeightForExercise(exerciseId) ?: 0f
        val max1RM = workoutDao.getMax1RMForExercise(exerciseId) ?: 0f

        val current1RM = weight * (1.0f + (reps / 30.0f))
        val epsilon = 0.01f

        return when {
            weight > maxWeight + epsilon -> "WEIGHT"
            current1RM > max1RM + epsilon -> "VOLUME"
            else -> "NONE"
        }
    }

    override suspend fun logSet(sessionId: Int, exerciseId: Int, setNumber: Int, weight: Float, reps: Int, isCompleted: Boolean, existingSetId: Int) {
        val setLog = SetLogEntity(
            id = existingSetId,
            sessionId = sessionId,
            exerciseId = exerciseId,
            setNumber = setNumber,
            weight = weight,
            reps = reps,
            isCompleted = isCompleted,
            prType = "NONE"
        )
        
        if (!isCompleted) {
            if (existingSetId != 0) {
                workoutDao.deleteSet(setLog)
            }
            return
        }

        // avaliar recorde apenas se a série estiver completa com valores maiores que 0
        var prType = "NONE"
        if (weight > 0 && reps > 0) {
            prType = checkPR(exerciseId, weight, reps)
        }

        val finalSetLog = setLog.copy(prType = prType)
        
        if (existingSetId == 0) {
            workoutDao.insertSet(finalSetLog)
        } else {
            workoutDao.updateSet(finalSetLog)
        }
    }

    // --- DASHBOARD ---

    // todas as sessões completas
    override fun getAllCompletedSessions() = workoutDao.getAllCompletedSessions()

    // todos os set logs completos
    override fun getAllCompletedSetLogs() = workoutDao.getAllCompletedSetLogs()

    // set logs completos de um exercício
    override fun getCompletedSetLogsForExercise(exerciseId: Int) = workoutDao.getCompletedSetLogsForExercise(exerciseId)

    // sessões completas de um workout
    override fun getCompletedSessionsForWorkout(workoutId: Int) = workoutDao.getCompletedSessionsForWorkout(workoutId)

    override suspend fun clearAllWorkoutData() {
        workoutDao.clearSetLogsTable()
        workoutDao.clearWorkoutSessionsTable()
        workoutDao.clearExercisesTable()
        workoutDao.clearWorkoutsTable()
    }
}
