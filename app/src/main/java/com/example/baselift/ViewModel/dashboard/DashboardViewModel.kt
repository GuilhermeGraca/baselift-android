package com.example.baselift.ViewModel.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.IWorkoutRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import com.example.baselift.View.components.ChartDataPoint
import com.example.baselift.View.components.DayMarkerState
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
    val historicalCalendarData: Map<String, com.example.baselift.View.components.DayMarkerState> = emptyMap(),
    val restDays: Int = 4,
    val nutritionRestDays: Int = 0,
    val nutritionLoaded: Boolean = false,
    val sessionsLoaded: Boolean = false,
    val volumeLoaded: Boolean = false,
    val routinesLoaded: Boolean = false,
    val preparedRoutineTrends: Set<Int> = emptySet(),
    val requestedRoutineTrends: Set<Int> = emptySet(),
    val calendarDataMonth: Long? = null,
    val calendarLoading: Boolean = false,
    val isLoading: Boolean = true
)

// modelo de vista do dashboard
class DashboardViewModel(
    private val workoutRepository: IWorkoutRepository,
    private val nutritionRepository: INutritionRepository,
    private val analyticsDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val restDaysFlow = MutableStateFlow(4) // por defeito 4 dias de descanso
    private val nutritionRestDaysFlow = MutableStateFlow(0) // por defeito 0 dias de descanso na nutrição
    private val expandedRoutines = MutableStateFlow<Set<Int>>(emptySet())
    private val calendarMonth = MutableStateFlow<Long?>(null)

    fun setRoutineExpanded(workoutId: Int, expanded: Boolean) {
        expandedRoutines.update { if (expanded) it + workoutId else it - workoutId }
    }

    fun setCalendarMonth(timestamp: Long?) {
        calendarMonth.value = timestamp
    }

    fun setRestDays(days: Int) {
        restDaysFlow.value = days
    }
    
    fun setNutritionRestDays(days: Int) {
        nutritionRestDaysFlow.value = days
    }

    private val analytics = DashboardAnalytics(clockMillis)

    // A cold flow: query creation and preparation begin only when a detail panel collects.
    fun exerciseCharts(exerciseId: Int): Flow<ExerciseCharts> = flow {
        emitAll(workoutRepository.getCompletedSetLogsForExercise(exerciseId)
            .distinctUntilChanged().map(analytics::exerciseCharts))
    }.onStart { emit(ExerciseCharts()) }
        .catch {
            if (it is CancellationException) throw it
            emit(ExerciseCharts(isLoading = false, loadFailed = true))
        }
        .flowOn(analyticsDispatcher)

    private data class Metadata(
        val workouts: List<WorkoutEntity>,
        val exercises: Map<Int, List<ExerciseEntity>>,
        val workoutNames: Map<Int, String>,
        val workoutByExercise: Map<Int, Int>
    )

    private data class CalendarSelection(val month: Long?, val dates: Set<String>)
    private data class DashboardDemands(val month: Long?, val expandedRoutines: Set<Int>)
    private data class NutritionAnalytics(
        val history: NutritionHistory, val streak: Int, val restDays: Int, val calendarMonth: Long?
    )
    private data class SessionCalendar(
        val history: SessionHistory,
        val weeklyDays: Map<Int, List<String>>,
        val calendar: Map<String, DayMarkerState>,
        val calendarMonth: Long?
    )
    private data class SessionAnalytics(
        val streak: Int,
        val restDays: Int,
        val weeklyDays: Map<Int, List<String>>,
        val weeklySessionCount: Int,
        val calendar: Map<String, DayMarkerState>,
        val calendarMonth: Long?
    )
    private data class ChartAnalytics(
        val history: SetHistory,
        val workoutTrends: Map<Int, List<ChartDataPoint>>,
        val preparedRoutineIds: Set<Int>
    )
    private data class ChartHistory(val history: SetHistory, val dailyVolumesPrepared: Boolean)

    val uiState: StateFlow<DashboardUiState> = flow {
        // Keep validated sections visible when the tab is collected again after its grace period.
        val retained = uiState.value
        // Sharing is confined to this collection. Stopping uiState cancels every branch,
        // including metadata's shared collectors, with no second offscreen grace period.
        coroutineScope {
            val day = flow {
                while (currentCoroutineContext().isActive) {
                    val (start, end) = analytics.currentDayBounds()
                    emit(start)
                    delay((end - clockMillis()).coerceAtLeast(1L))
                }
            }.distinctUntilChanged().shareIn(this, SharingStarted.Lazily, replay = 1)
            val week = day.map { analytics.currentWeekBounds().first }.distinctUntilChanged()
            val visibleDates = calendarMonth.map { CalendarSelection(it, analytics.calendarMonthDates(it)) }
                .distinctUntilChanged()
                .shareIn(this, SharingStarted.Lazily, replay = 1)

            val metadata = combine(
                workoutRepository.allWorkouts.distinctUntilChanged(),
                workoutRepository.allExercises.distinctUntilChanged()
            ) { workouts, exercises ->
                Metadata(
                    workouts, exercises.groupBy { it.workoutId },
                    workouts.associate { it.id to it.name },
                    exercises.associate { it.id to it.workoutId }
                )
            }.shareIn(this, SharingStarted.Lazily, replay = 1)

            val nutrition = combine(
                combine(nutritionRepository.getAllNutritionLogs().distinctUntilChanged(), week, visibleDates) { logs, _, selection ->
                    selection to analytics.nutritionHistory(logs, selection.dates)
                },
                nutritionRestDaysFlow,
                day
            ) { (selection, history), restDays, _ ->
                NutritionAnalytics(history, analytics.streak(history.activeDates, restDays), restDays, selection.month)
            }

            val sessionCalendar = combine(
                combine(workoutRepository.getAllCompletedSessions().distinctUntilChanged(), week) { history, _ ->
                    analytics.sessionHistory(history)
                },
                metadata.map { it.workoutNames }.distinctUntilChanged(),
                visibleDates
            ) { history, names, selection ->
                SessionCalendar(
                    history = history,
                    weeklyDays = history.weeklyWorkoutIds.mapValues { (_, ids) -> ids.map { names[it] ?: "Unknown" } },
                    calendar = analytics.workoutCalendar(history, names, selection.dates),
                    calendarMonth = selection.month
                )
            }
            val sessions = combine(sessionCalendar, restDaysFlow, day) { calendar, restDays, _ ->
                SessionAnalytics(
                    streak = analytics.streak(calendar.history.calendarWorkoutIds.keys, restDays),
                    restDays = restDays,
                    weeklyDays = calendar.weeklyDays,
                    weeklySessionCount = calendar.history.weeklySessionCount,
                    calendar = calendar.calendar,
                    calendarMonth = calendar.calendarMonth
                )
            }

            val charts = combine(
                combine(workoutRepository.getAllCompletedSetLogs().distinctUntilChanged().map { logs ->
                    // Opening/closing a detail must not regroup the full weekly history.
                    logs to analytics.setHistory(logs, includeDailyVolumes = false).weeklyVolumes
                }, expandedRoutines.map { it.isNotEmpty() }.distinctUntilChanged()) { (logs, weekly), needsDailyVolumes ->
                    ChartHistory(SetHistory(weekly,
                        if (needsDailyVolumes) analytics.dailyVolumes(logs) else emptyList()),
                        dailyVolumesPrepared = needsDailyVolumes)
                },
                // Renames and set-count/order edits do not change routine membership.
                metadata.map { it.workoutByExercise }.distinctUntilChanged(),
                expandedRoutines
            ) { prepared, workoutByExercise, expanded ->
                val trendReady = expanded.isEmpty() || prepared.dailyVolumesPrepared
                ChartAnalytics(prepared.history, if (expanded.isEmpty() || !trendReady) emptyMap() else
                    analytics.workoutVolumeTrends(prepared.history,
                        workoutByExercise.filterValues { it in expanded }),
                    if (trendReady) expanded else emptySet())
            }

            val demands = combine(calendarMonth, expandedRoutines, ::DashboardDemands)
            emitAll(combine(
                nutrition.map<NutritionAnalytics, NutritionAnalytics?> { it }.onStart { emit(null) },
                sessions.map<SessionAnalytics, SessionAnalytics?> { it }.onStart { emit(null) },
                charts.map<ChartAnalytics, ChartAnalytics?> { it }.onStart { emit(null) },
                metadata.map<Metadata, Metadata?> { it }.onStart { emit(null) },
                demands
            ) { nutritionData, sessionData, chartData, meta, requested ->
                val nutritionLoaded = nutritionData != null || retained.nutritionLoaded
                val sessionsLoaded = sessionData != null || retained.sessionsLoaded
                val volumeLoaded = chartData != null || retained.volumeLoaded
                val routinesLoaded = meta != null || retained.routinesLoaded
                val calendarIsCurrent = nutritionData?.calendarMonth == requested.month &&
                    sessionData?.calendarMonth == requested.month && nutritionData != null && sessionData != null
                val retainedCalendarIsCurrent = retained.calendarDataMonth == requested.month &&
                    retained.nutritionLoaded && retained.sessionsLoaded
                val calendarData = when {
                    calendarIsCurrent -> analytics.mergeCalendar(nutritionData!!.history.calendar, sessionData!!.calendar)
                    retainedCalendarIsCurrent -> retained.historicalCalendarData
                    else -> emptyMap()
                }
                DashboardUiState(
                    nutritionStreak = nutritionData?.streak ?: retained.nutritionStreak,
                    workoutStreak = sessionData?.streak ?: retained.workoutStreak,
                    nutritionDaysThisWeek = nutritionData?.history?.weeklyDays ?: retained.nutritionDaysThisWeek,
                    workoutDaysThisWeek = sessionData?.weeklyDays ?: retained.workoutDaysThisWeek,
                    workoutSessionsThisWeek = sessionData?.weeklySessionCount ?: retained.workoutSessionsThisWeek,
                    weeklyVolumes = chartData?.history?.weeklyVolumes ?: retained.weeklyVolumes,
                    workouts = meta?.workouts ?: retained.workouts,
                    exercises = meta?.exercises ?: retained.exercises,
                    workoutVolumeTrends = chartData?.workoutTrends ?: retained.workoutVolumeTrends,
                    preparedRoutineTrends = chartData?.preparedRoutineIds ?: retained.preparedRoutineTrends,
                    requestedRoutineTrends = requested.expandedRoutines,
                    historicalCalendarData = calendarData,
                    calendarDataMonth = if (calendarIsCurrent || retainedCalendarIsCurrent) requested.month else null,
                    calendarLoading = requested.month != null && !calendarIsCurrent && !retainedCalendarIsCurrent,
                    restDays = sessionData?.restDays ?: retained.restDays,
                    nutritionRestDays = nutritionData?.restDays ?: retained.nutritionRestDays,
                    nutritionLoaded = nutritionLoaded,
                    sessionsLoaded = sessionsLoaded,
                    volumeLoaded = volumeLoaded,
                    routinesLoaded = routinesLoaded,
                    isLoading = !(nutritionLoaded && sessionsLoaded && volumeLoaded && routinesLoaded)
                )
            })
        }
    }
        .flowOn(analyticsDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_000),
            initialValue = DashboardUiState()
        )
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
