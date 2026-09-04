package com.example.baselift.Model.local.dao

import androidx.room.*
import com.example.baselift.Model.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // --- WORKOUTS ---
    @Query("SELECT * FROM workouts ORDER BY orderIndex ASC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)

    // --- EXERCISES ---
    @Query("SELECT * FROM exercises ORDER BY workoutId ASC, orderIndex ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE workoutId = :workoutId ORDER BY orderIndex ASC")
    fun getExercisesForWorkout(workoutId: Int): Flow<List<ExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    @Query("DELETE FROM set_logs WHERE exerciseId = :exerciseId AND sessionId = :sessionId AND setNumber = :setNumber")
    suspend fun deleteSetByNumber(exerciseId: Int, sessionId: Int, setNumber: Int)

    // --- SESSIONS ---
    @Query("SELECT * FROM workout_sessions WHERE workoutId = :workoutId AND isCompleted = 0 LIMIT 1")
    suspend fun getActiveSession(workoutId: Int): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getAnyActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE workoutId = :workoutId AND isCompleted = 0 LIMIT 1")
    fun getActiveSessionFlow(workoutId: Int): Flow<WorkoutSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    // --- SETS ---
    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY exerciseId ASC, setNumber ASC")
    fun getSetsForSession(sessionId: Int): Flow<List<SetLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(setLog: SetLogEntity): Long

    @Update
    suspend fun updateSet(setLog: SetLogEntity)

    @Delete
    suspend fun deleteSet(setLog: SetLogEntity)

    // --- HISTORICAL PREV & PRs ---

    // obter a série anterior terminada de um exercício na última sessão
    @Query("""
        SELECT sl.* FROM set_logs sl
        INNER JOIN workout_sessions ws ON sl.sessionId = ws.id
        WHERE sl.exerciseId = :exerciseId AND sl.setNumber = :setNumber
          AND ws.isCompleted = 1 AND sl.isCompleted = 1
        ORDER BY ws.timestamp DESC LIMIT 1
    """)
    suspend fun getPreviousSet(exerciseId: Int, setNumber: Int): SetLogEntity?

    // obter o peso máximo levantado neste exercício
    @Query("""
        SELECT MAX(sl.weight) FROM set_logs sl
        INNER JOIN workout_sessions ws ON sl.sessionId = ws.id
        WHERE sl.exerciseId = :exerciseId AND ws.isCompleted = 1 AND sl.isCompleted = 1
    """)
    suspend fun getMaxWeightForExercise(exerciseId: Int): Float?

    // obter o 1RM máximo deste exercício
    @Query("""
        SELECT MAX(sl.weight * (1.0 + (sl.reps / 30.0))) FROM set_logs sl
        INNER JOIN workout_sessions ws ON sl.sessionId = ws.id
        WHERE sl.exerciseId = :exerciseId AND ws.isCompleted = 1 AND sl.isCompleted = 1
    """)
    suspend fun getMax1RMForExercise(exerciseId: Int): Float?

    // --- DASHBOARD ---

    // todas as sessões completas (para streak e calendário semanal)
    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 1 ORDER BY timestamp DESC")
    fun getAllCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    // todos os set logs completos (para agregação de volume total)
    @Query("""
        SELECT sl.* FROM set_logs sl
        INNER JOIN workout_sessions ws ON sl.sessionId = ws.id
        WHERE ws.isCompleted = 1 AND sl.isCompleted = 1
        ORDER BY ws.timestamp ASC
    """)
    fun getAllCompletedSetLogs(): Flow<List<SetLogEntity>>

    // set logs completos de um exercício específico (para gráficos por exercício)
    @Query("""
        SELECT sl.* FROM set_logs sl
        INNER JOIN workout_sessions ws ON sl.sessionId = ws.id
        WHERE sl.exerciseId = :exerciseId AND ws.isCompleted = 1 AND sl.isCompleted = 1
        ORDER BY ws.timestamp ASC
    """)
    fun getCompletedSetLogsForExercise(exerciseId: Int): Flow<List<SetLogEntity>>

    // sessões completas de um workout específico (para gráfico por rotina)
    @Query("""
        SELECT ws.* FROM workout_sessions ws
        WHERE ws.workoutId = :workoutId AND ws.isCompleted = 1
        ORDER BY ws.timestamp ASC
    """)
    fun getCompletedSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSessionEntity>>

    @Query("DELETE FROM workouts")
    suspend fun clearWorkoutsTable()

    @Query("DELETE FROM exercises")
    suspend fun clearExercisesTable()

    @Query("DELETE FROM workout_sessions")
    suspend fun clearWorkoutSessionsTable()

    @Query("DELETE FROM set_logs")
    suspend fun clearSetLogsTable()
}
