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
import com.example.baselift.View.theme.PureBlack
import com.example.baselift.ViewModel.dashboard.DashboardUiState
import com.example.baselift.ViewModel.dashboard.ExerciseCharts
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// ecrã principal do dashboard
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onSetRestDays: (Int) -> Unit = {},
    onSetNutritionRestDays: (Int) -> Unit = {},
    exerciseCharts: (Int) -> Flow<ExerciseCharts>
) {
    var isContentReady by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        isContentReady = true
    }

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
                onSetRestDays = onSetRestDays,
                onSetNutritionRestDays = onSetNutritionRestDays
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            // secção de volume total de treino
            TotalTrainingVolumeSection(weeklyVolumes = uiState.weeklyVolumes)
        }

        // secção de rotinas (carregada lazy)
        if (isContentReady) {
            RoutinesSection(
                workouts = uiState.workouts,
                exercisesMap = uiState.exercises,
                workoutVolumeTrends = uiState.workoutVolumeTrends,
                exerciseCharts = exerciseCharts
            )
        }
    }
}
