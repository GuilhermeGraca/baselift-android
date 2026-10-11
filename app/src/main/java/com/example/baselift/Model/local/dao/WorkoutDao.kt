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

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

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

    @Update
    suspend fun updateExercises(exercises: List<ExerciseEntity>)

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
        ORDER BY ws.timestamp DESC, ws.id DESC, sl.id DESC LIMIT 1
    """)
    suspend fun getPreviousSet(exerciseId: Int, setNumber: Int): SetLogEntity?

    // Latest completed history per exercise/set number, including fallback to older sessions.
    // Resolve equal timestamps by session ID, then duplicate logs by log ID.
    @Query("""
        WITH latest_timestamps AS (
            SELECT history.exerciseId, history.setNumber, MAX(session.timestamp) AS timestamp
            FROM exercises exercise
            INNER JOIN set_logs history ON history.exerciseId = exercise.id
            INNER JOIN workout_sessions session ON history.sessionId = session.id
            WHERE exercise.workoutId = :workoutId
              AND history.isCompleted = 1 AND session.isCompleted = 1
            GROUP BY history.exerciseId, history.setNumber
        ), latest_sessions AS (
            SELECT latest.exerciseId, latest.setNumber, MAX(session.id) AS sessionId
            FROM latest_timestamps latest
            INNER JOIN set_logs history ON history.exerciseId = latest.exerciseId AND history.setNumber = latest.setNumber
            INNER JOIN workout_sessions session ON history.sessionId = session.id AND session.timestamp = latest.timestamp
            WHERE history.isCompleted = 1 AND session.isCompleted = 1
            GROUP BY latest.exerciseId, latest.setNumber
        )
        SELECT sl.* FROM set_logs sl
        WHERE sl.id IN (
            SELECT MAX(history.id)
            FROM latest_sessions latest
            INNER JOIN set_logs history ON history.sessionId = latest.sessionId
                AND history.exerciseId = latest.exerciseId AND history.setNumber = latest.setNumber
            WHERE history.isCompleted = 1
            GROUP BY latest.exerciseId, latest.setNumber
        )
        ORDER BY sl.exerciseId ASC, sl.setNumber ASC
    """)
    suspend fun getPreviousSetsForWorkout(workoutId: Int): List<SetLogEntity>

    // Both independent PR maxima in one aggregate read of completed history.
    @Query("""
        SELECT MAX(sl.weight) AS maxWeight,
               MAX(sl.weight * (1.0 + (sl.reps / 30.0))) AS max1RM
        FROM set_logs sl
        INNER JOIN workout_sessions ws ON sl.sessionId = ws.id
        WHERE sl.exerciseId = :exerciseId AND ws.isCompleted = 1 AND sl.isCompleted = 1
    """)
    suspend fun getExercisePrMaxima(exerciseId: Int): ExercisePrMaxima

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
