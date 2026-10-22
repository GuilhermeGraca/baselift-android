package com.example.baselift.View.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.baselift.View.dashboard.components.ConsistencyJournalSection
import com.example.baselift.View.dashboard.components.TotalTrainingVolumeSection
import com.example.baselift.View.dashboard.components.RoutinesSection
import com.example.baselift.View.dashboard.components.DashboardVolumeSkeleton
import com.example.baselift.View.theme.PureBlack
import com.example.baselift.ViewModel.dashboard.DashboardUiState
import com.example.baselift.ViewModel.dashboard.ExerciseCharts
import kotlinx.coroutines.flow.Flow

// ecrã principal do dashboard
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onSetRestDays: (Int) -> Unit = {},
    onSetNutritionRestDays: (Int) -> Unit = {},
    exerciseCharts: (Int) -> Flow<ExerciseCharts>,
    onRoutineExpansionChanged: (Int, Boolean) -> Unit = { _, _ -> },
    onCalendarMonthChanged: (Long?) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
    ) {
        item {
            // secção de consistency journal
            ConsistencyJournalSection(
                nutritionStreak = uiState.nutritionStreak,
                workoutStreak = uiState.workoutStreak,
                nutritionDaysThisWeek = uiState.nutritionDaysThisWeek,
                workoutDaysThisWeek = uiState.workoutDaysThisWeek,
                workoutSessionsThisWeek = uiState.workoutSessionsThisWeek,
                historicalCalendarData = uiState.historicalCalendarData,
                restDays = uiState.restDays,
                nutritionRestDays = uiState.nutritionRestDays,
                nutritionLoading = !uiState.nutritionLoaded,
                workoutLoading = !uiState.sessionsLoaded,
                calendarLoading = uiState.calendarLoading,
                onSetRestDays = onSetRestDays,
                onSetNutritionRestDays = onSetNutritionRestDays,
                onCalendarMonthChanged = onCalendarMonthChanged
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            // secção de volume total de treino
            if (uiState.volumeLoaded) {
                TotalTrainingVolumeSection(weeklyVolumes = uiState.weeklyVolumes)
            } else {
                DashboardVolumeSkeleton()
            }
        }

        // secção de rotinas (carregada lazy)
        RoutinesSection(
            workouts = uiState.workouts,
            isLoading = !uiState.routinesLoaded,
            exercisesMap = uiState.exercises,
            workoutVolumeTrends = uiState.workoutVolumeTrends,
            preparedRoutineTrends = uiState.preparedRoutineTrends,
            requestedRoutineTrends = uiState.requestedRoutineTrends,
            exerciseCharts = exerciseCharts,
            onRoutineExpansionChanged = onRoutineExpansionChanged
        )
    }
}
