package com.example.baselift.ViewModel.dashboard

import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.View.components.ChartDataPoint
import com.example.baselift.View.components.DayMarkerState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class NutritionHistory(
    val weeklyDays: Map<Int, NutritionSummary>,
    val calendar: Map<String, DayMarkerState>
)

internal data class SessionHistory(
    val weeklyWorkoutIds: Map<Int, List<Int>>,
    val weeklySessionCount: Int,
    val calendarWorkoutIds: Map<String, List<Int>>
)

internal data class DailyExerciseVolume(
    val exerciseId: Int,
    val timestamp: Long,
    val volume: Double,
    val dateLabel: String
)

internal data class SetHistory(
    val weeklyVolumes: List<WeeklyVolume>,
    val dailyVolumes: List<DailyExerciseVolume>
)

data class ExerciseCharts(
    val volumeTrend: List<ChartDataPoint> = emptyList(),
    val maxWeightTrend: List<ChartDataPoint> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false
)

// Pure history preparation. No shared Calendar/formatter state crosses coroutine branches.
internal class DashboardAnalytics(private val clockMillis: () -> Long) {
    fun nutritionHistory(logs: List<NutritionLogEntity>): NutritionHistory {
        val (weekStart, weekEnd) = currentWeekBounds()
        val cal = Calendar.getInstance()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val weeklyDays = mutableMapOf<Int, NutritionSummary>()
        val calendar = mutableMapOf<String, DayMarkerState>()

        logs.forEach { log ->
            cal.timeInMillis = log.timestamp
            val ymd = formatter.format(Date(log.timestamp))
            val previous = calendar[ymd] ?: DayMarkerState()
            calendar[ymd] = previous.copy(
                hasNutrition = true,
                nutritionCalories = (previous.nutritionCalories ?: 0) + log.calories,
                nutritionProtein = (previous.nutritionProtein ?: 0) + log.protein,
                nutritionCarbs = (previous.nutritionCarbs ?: 0) + log.carbs,
                nutritionFats = (previous.nutritionFats ?: 0) + log.fats
            )
            if (log.timestamp in weekStart until weekEnd) {
                val day = dayOfWeek(cal)
                val current = weeklyDays[day] ?: NutritionSummary(0, 0, 0, 0)
                weeklyDays[day] = NutritionSummary(
                    current.calories + log.calories, current.protein + log.protein,
                    current.carbs + log.carbs, current.fats + log.fats
                )
            }
        }
        return NutritionHistory(weeklyDays, calendar)
    }

    fun sessionHistory(sessions: List<WorkoutSessionEntity>): SessionHistory {
        val (weekStart, weekEnd) = currentWeekBounds()
        val cal = Calendar.getInstance()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val weeklyIds = mutableMapOf<Int, MutableList<Int>>()
        val calendarIds = mutableMapOf<String, MutableList<Int>>()
        var count = 0
        sessions.forEach { session ->
            cal.timeInMillis = session.timestamp
            val ymd = formatter.format(Date(session.timestamp))
            calendarIds.getOrPut(ymd) { mutableListOf() }.add(session.workoutId)
            if (session.timestamp in weekStart until weekEnd) {
                weeklyIds.getOrPut(dayOfWeek(cal)) { mutableListOf() }.add(session.workoutId)
                count++
            }
        }
        return SessionHistory(weeklyIds, count, calendarIds)
    }

    // One rule across 0–6: increasing a week's rest allowance can only extend a streak.
    // Prepared unique dates avoid rereading logs or counting multiple sessions twice.
    fun streak(activeDates: Set<String>, restDays: Int): Int {
        if (activeDates.isEmpty()) return 0
        val cal = Calendar.getInstance().apply { timeInMillis = clockMillis() }
        atMidnight(cal)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = formatter.format(cal.time)
        val allowance = restDays.coerceIn(0, 7)
        if (allowance == 7) return activeDates.count { it <= today }

        // Today has not ended: an unlogged today may continue yesterday's streak.
        if (today !in activeDates) cal.add(Calendar.DAY_OF_YEAR, -1)
        var count = 0
        var missedDays = 0
        while (true) {
            if (formatter.format(cal.time) in activeDates) {
                count++
            } else {
                missedDays++
                if (missedDays > allowance) break
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
            // Walking backward from Monday enters the preceding Monday–Sunday week.
            // Use the actual day boundary, so Dec/Jan cannot split one week's budget.
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) missedDays = 0
        }
        return count
    }

    fun setHistory(setLogs: List<SetLogEntity>): SetHistory {
        val weeklyVolumes = weeklyVolumes(setLogs)
        val cal = Calendar.getInstance()
        val logsByDate = setLogs.groupBy {
            cal.timeInMillis = it.timestamp
            dateKey(cal)
        }
        val shortDateFormatter = SimpleDateFormat("MMM dd", Locale.US)
        val dailyVolumes = mutableListOf<DailyExerciseVolume>()
        logsByDate.forEach { (_, logs) ->
            cal.timeInMillis = logs.first().timestamp
            atMidnight(cal)
            val midnightTs = cal.timeInMillis
            val dateLabel = shortDateFormatter.format(Date(midnightTs))
            logs.groupBy { it.exerciseId }.forEach { (exerciseId, exerciseLogs) ->
                // Keep Double sums until the final point, including routine totals.
                val totalVolume = exerciseLogs.sumOf { (it.weight * it.reps).toDouble() }
                dailyVolumes += DailyExerciseVolume(exerciseId, midnightTs, totalVolume, dateLabel)
            }
        }
        return SetHistory(weeklyVolumes, dailyVolumes)
    }

    fun exerciseCharts(setLogs: List<SetLogEntity>): ExerciseCharts {
        val cal = Calendar.getInstance()
        val logsByDate = setLogs.groupBy {
            cal.timeInMillis = it.timestamp
            dateKey(cal)
        }
        val formatter = SimpleDateFormat("MMM dd", Locale.US)
        val volumes = mutableListOf<ChartDataPoint>()
        val weights = mutableListOf<ChartDataPoint>()
        logsByDate.values.forEach { logs ->
            cal.timeInMillis = logs.first().timestamp
            atMidnight(cal)
            val timestamp = cal.timeInMillis
            val label = formatter.format(cal.time)
            val volume = logs.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
            val maxLog = logs.maxWithOrNull(compareBy({ it.weight }, { it.reps }))
            val weight = maxLog?.weight ?: 0f
            val est1RM = if (maxLog != null) weight * (1 + maxLog.reps / 30f) else 0f
            volumes += ChartDataPoint(timestamp, volume, volumeLabel(volume, label))
            weights += ChartDataPoint(timestamp, weight,
                "${String.format(Locale.US, "%.1f", weight)} kg\n$label", extraValue = est1RM)
        }
        volumes.sortBy { it.xValue }
        weights.sortBy { it.xValue }
        return ExerciseCharts(volumes, weights, isLoading = false)
    }

    fun workoutVolumeTrends(history: SetHistory, workoutByExercise: Map<Int, Int>): Map<Int, List<ChartDataPoint>> {
        val volumes = mutableMapOf<Pair<Int, Long>, Double>()
        val labels = mutableMapOf<Long, String>()
        history.dailyVolumes.forEach { daily ->
            val workoutId = workoutByExercise[daily.exerciseId] ?: return@forEach
            if (workoutId == -1) return@forEach
            val key = workoutId to daily.timestamp
            volumes[key] = (volumes[key] ?: 0.0) + daily.volume
            labels[daily.timestamp] = daily.dateLabel
        }
        val trends = mutableMapOf<Int, MutableList<ChartDataPoint>>()
        volumes.forEach { (key, volume) ->
            val (workoutId, timestamp) = key
            trends.getOrPut(workoutId) { mutableListOf() }.add(
                ChartDataPoint(timestamp, volume.toFloat(), volumeLabel(volume.toFloat(), labels.getValue(timestamp)))
            )
        }
        trends.values.forEach { it.sortBy { point -> point.xValue } }
        return trends
    }

    fun workoutCalendar(history: SessionHistory, names: Map<Int, String>): Map<String, DayMarkerState> =
        history.calendarWorkoutIds.mapValues { (_, ids) ->
            DayMarkerState(hasWorkout = true, workoutNames = ids.mapNotNull { names[it] }.distinct())
        }

    fun mergeCalendar(nutrition: Map<String, DayMarkerState>, workouts: Map<String, DayMarkerState>): Map<String, DayMarkerState> {
        val combined = nutrition.toMutableMap()
        workouts.forEach { (date, workout) ->
            combined[date] = combined[date]?.copy(hasWorkout = true, workoutNames = workout.workoutNames) ?: workout
        }
        return combined
    }

    private fun weeklyVolumes(setLogs: List<SetLogEntity>): List<WeeklyVolume> {
        val cal = isoCalendar()
        val volumeByWeek = mutableMapOf<String, Pair<Long, Float>>()
        setLogs.forEach { log ->
            cal.timeInMillis = log.timestamp
            val key = weekKey(cal)
            val volume = log.weight * log.reps
            val existing = volumeByWeek[key]
            volumeByWeek[key] = if (existing != null) {
                existing.copy(second = existing.second + volume)
            } else {
                mondayTimestamp(log.timestamp) to volume
            }
        }
        return volumeByWeek.values.sortedBy { it.first }.map { WeeklyVolume(it.first, it.second) }
    }

    fun currentWeekBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = clockMillis() }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) cal.add(Calendar.DAY_OF_YEAR, -1)
        atMidnight(cal)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        return start to cal.timeInMillis
    }

    fun currentDayBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = clockMillis() }
        atMidnight(cal)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }

    private fun mondayTimestamp(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) cal.add(Calendar.DAY_OF_YEAR, -1)
        atMidnight(cal)
        return cal.timeInMillis
    }

    private fun atMidnight(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    private fun isoCalendar() = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }

    // Preserve the existing calendar-year/week-number semantics in this performance change.
    private fun weekKey(cal: Calendar) = "${cal.get(Calendar.YEAR)}-W${cal.get(Calendar.WEEK_OF_YEAR)}"
    private fun dateKey(cal: Calendar) = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    private fun dayOfWeek(cal: Calendar): Int =
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else cal.get(Calendar.DAY_OF_WEEK) - 1
    private fun volumeLabel(volume: Float, date: String) = "${String.format(Locale.US, "%.0f", volume)} kg\n$date"
}
