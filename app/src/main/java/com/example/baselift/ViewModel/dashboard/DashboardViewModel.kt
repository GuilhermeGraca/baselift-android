package com.example.baselift.ViewModel.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.IWorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import java.util.Calendar
import java.util.Locale
import androidx.compose.runtime.Stable

// volume agregado por semana
@Stable
data class WeeklyVolume(
    val weekStartTimestamp: Long,
    val totalVolume: Float
)

@Stable
data class NutritionSummary(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int
)

// estado da vista do dashboard
@Stable
data class DashboardUiState(
    val nutritionStreak: Int = 0,
    val workoutStreak: Int = 0,
    val nutritionDaysThisWeek: Map<Int, NutritionSummary> = emptyMap(),
    val workoutDaysThisWeek: Map<Int, List<String>> = emptyMap(),
    val workoutSessionsThisWeek: Int = 0,
    val weeklyVolumes: List<WeeklyVolume> = emptyList(),
    val workouts: List<WorkoutEntity> = emptyList(),
    val exercises: Map<Int, List<com.example.baselift.Model.local.entity.ExerciseEntity>> = emptyMap(),
    val workoutVolumeTrends: Map<Int, List<com.example.baselift.View.components.ChartDataPoint>> = emptyMap(),
    val exerciseVolumeTrends: Map<Int, List<com.example.baselift.View.components.ChartDataPoint>> = emptyMap(),
    val exerciseMaxWeightTrends: Map<Int, List<com.example.baselift.View.components.ChartDataPoint>> = emptyMap(),
    val historicalCalendarData: Map<String, com.example.baselift.View.components.DayMarkerState> = emptyMap(),
    val restDays: Int = 4,
    val nutritionRestDays: Int = 0,
    val isLoading: Boolean = true
)

// modelo de vista do dashboard
class DashboardViewModel(
    private val workoutRepository: IWorkoutRepository,
    private val nutritionRepository: INutritionRepository
) : ViewModel() {

    private val restDaysFlow = MutableStateFlow(4) // por defeito 4 dias de descanso
    private val nutritionRestDaysFlow = MutableStateFlow(0) // por defeito 0 dias de descanso na nutrição

    fun setRestDays(days: Int) {
        restDaysFlow.value = days
    }
    
    fun setNutritionRestDays(days: Int) {
        nutritionRestDaysFlow.value = days
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        nutritionRepository.getAllNutritionLogs(),
        workoutRepository.getAllCompletedSessions(),
        workoutRepository.getAllCompletedSetLogs(),
        workoutRepository.allWorkouts,
        workoutRepository.allExercises,
        restDaysFlow,
        nutritionRestDaysFlow
    ) { dataArray ->
        @Suppress("UNCHECKED_CAST")
        val nutritionLogs = dataArray[0] as List<NutritionLogEntity>
        @Suppress("UNCHECKED_CAST")
        val completedSessions = dataArray[1] as List<WorkoutSessionEntity>
        @Suppress("UNCHECKED_CAST")
        val completedSetLogs = dataArray[2] as List<com.example.baselift.Model.local.entity.SetLogEntity>
        @Suppress("UNCHECKED_CAST")
        val workouts = dataArray[3] as List<WorkoutEntity>
        @Suppress("UNCHECKED_CAST")
        val allExercises = dataArray[4] as List<com.example.baselift.Model.local.entity.ExerciseEntity>
        val restDays = dataArray[5] as Int
        val nutritionRestDays = dataArray[6] as Int

        // calcular streak de nutrição (semanas consecutivas com >= requiredNutritionDays)
        val requiredNutritionDays = if (nutritionRestDays >= 7) 0 else 7 - nutritionRestDays
        val nutritionStreak = calculateNutritionStreak(nutritionLogs, requiredNutritionDays)

        // calcular streak de workout (semanas consecutivas com >= requiredDays)
        val requiredDays = if (restDays >= 7) 0 else 7 - restDays
        val workoutStreak = calculateWorkoutStreak(completedSessions, requiredDays)

        // calcular calendário semanal
        val (nutDays, wrkDays, wrkCount) = calculateWeeklyCalendar(nutritionLogs, completedSessions, workouts)

        // calcular volumes semanais globais
        val weeklyVolumes = calculateWeeklyVolumes(completedSetLogs)
        
        // agrupar exercícios por workout
        val exercisesMap = allExercises.groupBy { it.workoutId }
        val exerciseIdToWorkoutId = allExercises.associate { it.id to it.workoutId }

        // --- CALCULAR GRÁFICOS POR DATA ---
        val workoutVolTrends = mutableMapOf<Int, MutableList<com.example.baselift.View.components.ChartDataPoint>>()
        val exerciseVolTrends = mutableMapOf<Int, MutableList<com.example.baselift.View.components.ChartDataPoint>>()
        val exerciseMaxWeightTrends = mutableMapOf<Int, MutableList<com.example.baselift.View.components.ChartDataPoint>>()

        val setLogsByDate = completedSetLogs.groupBy { timestampToDateKey(it.timestamp) }
        
        val shortDateFormatter = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        
        setLogsByDate.forEach { (dateKey, logsForDate) ->
            val parts = dateKey.split("-")
            cal.set(java.util.Calendar.YEAR, parts[0].toInt())
            cal.set(java.util.Calendar.DAY_OF_YEAR, parts[1].toInt())
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val midnightTs = cal.timeInMillis
            val dateStr = shortDateFormatter.format(java.util.Date(midnightTs))

            // agrupar volume por treino
            val logsByWorkout = logsForDate.groupBy { log -> exerciseIdToWorkoutId[log.exerciseId] ?: -1 }
            logsByWorkout.forEach { (wId, wLogs) ->
                if (wId != -1) {
                    val wVol = wLogs.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
                    workoutVolTrends.getOrPut(wId) { mutableListOf() }.add(
                        com.example.baselift.View.components.ChartDataPoint(
                            xValue = midnightTs,
                            yValue = wVol,
                            tooltipLabel = "${String.format(java.util.Locale.US, "%.0f", wVol)} kg\n$dateStr"
                        )
                    )
                }
            }

            // calcular volume do exercício e peso máximo
            val logsByExercise = logsForDate.groupBy { it.exerciseId }
            logsByExercise.forEach { (exId, exLogs) ->
                val totalVolume = exLogs.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
                
                val maxWeightLog = exLogs.maxWithOrNull(compareBy({ it.weight }, { it.reps }))
                val maxWeight = maxWeightLog?.weight ?: 0f
                val est1RM = if (maxWeightLog != null) maxWeight * (1 + maxWeightLog.reps / 30f) else 0f
                
                exerciseVolTrends.getOrPut(exId) { mutableListOf() }.add(
                    com.example.baselift.View.components.ChartDataPoint(
                        xValue = midnightTs,
                        yValue = totalVolume,
                        tooltipLabel = "${String.format(java.util.Locale.US, "%.0f", totalVolume)} kg\n$dateStr"
                    )
                )
                
                exerciseMaxWeightTrends.getOrPut(exId) { mutableListOf() }.add(
                    com.example.baselift.View.components.ChartDataPoint(
                        xValue = midnightTs,
                        yValue = maxWeight,
                        tooltipLabel = "${String.format(java.util.Locale.US, "%.1f", maxWeight)} kg\n$dateStr",
                        extraValue = est1RM
                    )
                )
            }
        }
        
        // ordenar as listas cronologicamente
        workoutVolTrends.values.forEach { list -> list.sortBy { it.xValue } }
        exerciseVolTrends.values.forEach { list -> list.sortBy { it.xValue } }
        exerciseMaxWeightTrends.values.forEach { list -> list.sortBy { it.xValue } }

        // --- CALCULAR HISTORICO GLOBAL DE CALENDARIO ---
        val calendarData = mutableMapOf<String, com.example.baselift.View.components.DayMarkerState>()
        
        val ymdFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fun toYMD(timestamp: Long): String {
            return ymdFormatter.format(java.util.Date(timestamp))
        }

        val nutritionByYMD = nutritionLogs.groupBy { toYMD(it.timestamp) }
        nutritionByYMD.forEach { (ymd, logs) ->
            calendarData[ymd] = com.example.baselift.View.components.DayMarkerState(
                hasNutrition = true,
                nutritionCalories = logs.sumOf { it.calories },
                nutritionProtein = logs.sumOf { it.protein },
                nutritionCarbs = logs.sumOf { it.carbs },
                nutritionFats = logs.sumOf { it.fats }
            )
        }

        val workoutsByYMD = completedSessions.groupBy { toYMD(it.timestamp) }
        workoutsByYMD.forEach { (ymd, sessions) ->
            val workoutNames = sessions.mapNotNull { sess -> workouts.find { it.id == sess.workoutId }?.name }.distinct()
            val existing = calendarData[ymd]
            if (existing != null) {
                calendarData[ymd] = existing.copy(hasWorkout = true, workoutNames = workoutNames)
            } else {
                calendarData[ymd] = com.example.baselift.View.components.DayMarkerState(hasWorkout = true, workoutNames = workoutNames)
            }
        }

        DashboardUiState(
            nutritionStreak = nutritionStreak,
            workoutStreak = workoutStreak,
            nutritionDaysThisWeek = nutDays,
            workoutDaysThisWeek = wrkDays,
            workoutSessionsThisWeek = wrkCount,
            weeklyVolumes = weeklyVolumes,
            workouts = workouts,
            exercises = exercisesMap,
            workoutVolumeTrends = workoutVolTrends,
            exerciseVolumeTrends = exerciseVolTrends,
            exerciseMaxWeightTrends = exerciseMaxWeightTrends,
            historicalCalendarData = calendarData,
            restDays = restDays,
            nutritionRestDays = nutritionRestDays,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = DashboardUiState()
    )

    // conta o número total de DIAS de nutrição registados desde o início da streak atual
    private fun calculateNutritionStreak(logs: List<NutritionLogEntity>, requiredDays: Int): Int {
        if (logs.isEmpty()) return 0
        if (requiredDays == 0) {
            return logs.map { timestampToDateKey(it.timestamp) }.toSet().size
        }

        // agrupar logs por semana ISO (ano + semana)
        val logsByWeek = logs.groupBy { getIsoWeekKey(it.timestamp) }

        var totalStreakCount = 0

        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4

        // Função auxiliar para contar dias únicos com registos numa semana
        fun countUniqueDays(weekLogs: List<NutritionLogEntity>?): Int {
            if (weekLogs == null) return 0
            return weekLogs.map { timestampToDateKey(it.timestamp) }.toSet().size
        }

        // 1. A semana atual (mesmo incompleta) não quebra a streak.
        val currentWeekKey = getIsoWeekKeyFromCalendar(cal)
        val currentCount = countUniqueDays(logsByWeek[currentWeekKey])
        totalStreakCount += currentCount

        // 2. Andar para trás semana a semana
        cal.add(Calendar.WEEK_OF_YEAR, -1)

        while (true) {
            val weekKey = getIsoWeekKeyFromCalendar(cal)
            val count = countUniqueDays(logsByWeek[weekKey])
            
            if (count >= requiredDays) {
                totalStreakCount += count
                cal.add(Calendar.WEEK_OF_YEAR, -1)
            } else {
                break
            }
        }

        return totalStreakCount
    }

    // conta o número total de DIAS de treino desde o início da streak atual
    private fun calculateWorkoutStreak(sessions: List<WorkoutSessionEntity>, requiredDays: Int): Int {
        if (sessions.isEmpty()) return 0
        if (requiredDays == 0) {
            return sessions.map { timestampToDateKey(it.timestamp) }.toSet().size
        }

        // agrupar sessões por semana ISO (ano + semana)
        val sessionsByWeek = sessions.groupBy { getIsoWeekKey(it.timestamp) }

        var totalStreakCount = 0

        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4

        // Função auxiliar para contar os dias únicos de treino numa lista de sessões
        fun countUniqueDays(weekSessions: List<WorkoutSessionEntity>?): Int {
            if (weekSessions == null) return 0
            return weekSessions.map { timestampToDateKey(it.timestamp) }.toSet().size
        }

        // 1. A semana atual (mesmo que incompleta) não quebra a streak.
        val currentWeekKey = getIsoWeekKeyFromCalendar(cal)
        val currentCount = countUniqueDays(sessionsByWeek[currentWeekKey])
        totalStreakCount += currentCount

        // 2. Agora andamos para trás, semana a semana
        cal.add(Calendar.WEEK_OF_YEAR, -1)

        while (true) {
            val weekKey = getIsoWeekKeyFromCalendar(cal)
            val count = countUniqueDays(sessionsByWeek[weekKey])
            
            // se a semana anterior teve treinos >= requiredDays, a streak é válida
            if (count >= requiredDays) {
                totalStreakCount += count
                cal.add(Calendar.WEEK_OF_YEAR, -1)
            } else {
                break
            }
        }

        return totalStreakCount
    }

    // calcular que dias da semana atual têm dados
    private fun calculateWeeklyCalendar(
        nutritionLogs: List<NutritionLogEntity>,
        sessions: List<WorkoutSessionEntity>,
        workouts: List<WorkoutEntity>
    ): Triple<Map<Int, NutritionSummary>, Map<Int, List<String>>, Int> {
        val cal = Calendar.getInstance()
        // recuar até à segunda-feira desta semana
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val weekStart = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 7)
        val weekEnd = cal.timeInMillis

        val nutDays = mutableMapOf<Int, NutritionSummary>()
        nutritionLogs.filter { it.timestamp in weekStart until weekEnd }.forEach { log ->
            val day = timestampToDayOfWeek(log.timestamp)
            val current = nutDays[day] ?: NutritionSummary(0, 0, 0, 0)
            nutDays[day] = NutritionSummary(
                calories = current.calories + log.calories,
                protein = current.protein + log.protein,
                carbs = current.carbs + log.carbs,
                fats = current.fats + log.fats
            )
        }

        val wrkDays = mutableMapOf<Int, MutableList<String>>()
        val weekSessions = sessions.filter { it.timestamp in weekStart until weekEnd }
        weekSessions.forEach { session ->
            val day = timestampToDayOfWeek(session.timestamp)
            val workoutName = workouts.find { it.id == session.workoutId }?.name ?: "Unknown"
            wrkDays.getOrPut(day) { mutableListOf() }.add(workoutName)
        }

        return Triple(nutDays, wrkDays, weekSessions.size)
    }

    // agregar volume total por semana ISO
    private fun calculateWeeklyVolumes(
        setLogs: List<com.example.baselift.Model.local.entity.SetLogEntity>
    ): List<WeeklyVolume> {
        if (setLogs.isEmpty()) return emptyList()

        val volumeByWeek = mutableMapOf<String, Pair<Long, Float>>()

        setLogs.forEach { log ->
            val weekKey = getIsoWeekKey(log.timestamp)
            val volume = log.weight * log.reps
            val existing = volumeByWeek[weekKey]
            if (existing != null) {
                volumeByWeek[weekKey] = existing.copy(second = existing.second + volume)
            } else {
                // usar a segunda-feira dessa semana como timestamp
                val mondayTs = getMondayTimestamp(log.timestamp)
                volumeByWeek[weekKey] = Pair(mondayTs, volume)
            }
        }

        return volumeByWeek.entries
            .sortedBy { it.value.first }
            .map { WeeklyVolume(it.value.first, it.value.second) }
    }

    // --- funções auxiliares ---

    // converte timestamp para chave de data (ano + dia do ano)
    private fun timestampToDateKey(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun calendarToDateKey(cal: Calendar): String {
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    // converte timestamp para dia da semana (1=Seg, 7=Dom)
    private fun timestampToDayOfWeek(timestamp: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.firstDayOfWeek = Calendar.MONDAY
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        // converter de domingo=1..sábado=7 para segunda=1..domingo=7
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    // chave ISO da semana (ano + semana)
    private fun getIsoWeekKey(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        return "${cal.get(Calendar.YEAR)}-W${cal.get(Calendar.WEEK_OF_YEAR)}"
    }

    private fun getIsoWeekKeyFromCalendar(cal: Calendar): String {
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        return "${cal.get(Calendar.YEAR)}-W${cal.get(Calendar.WEEK_OF_YEAR)}"
    }

    // obter timestamp da segunda-feira de uma semana
    private fun getMondayTimestamp(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

// fábrica para instanciar o modelo de vista
class DashboardViewModelFactory(
    private val workoutRepository: IWorkoutRepository,
    private val nutritionRepository: INutritionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(workoutRepository, nutritionRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
