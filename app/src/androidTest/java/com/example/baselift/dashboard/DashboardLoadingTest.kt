package com.example.baselift.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.R
import com.example.baselift.View.dashboard.DashboardScreen
import com.example.baselift.View.dashboard.components.ExerciseCollapsibleCard
import com.example.baselift.View.dashboard.components.WorkoutCollapsibleCard
import com.example.baselift.ViewModel.dashboard.DashboardUiState
import com.example.baselift.ViewModel.dashboard.ExerciseCharts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun partialResultsShowOnlyUnknownSectionsAsSkeletons() {
        val state = mutableStateOf(DashboardUiState())
        compose.setContent { MaterialTheme {
            DashboardScreen(state.value, exerciseCharts = { flowOf(ExerciseCharts(isLoading = false)) })
        } }
        compose.onAllNodesWithTag("dashboard-streak-skeleton").assertCountEquals(2)
        compose.onNodeWithTag("dashboard-volume-skeleton").assertIsDisplayed()

        compose.runOnIdle {
            state.value = DashboardUiState(nutritionLoaded = false, sessionsLoaded = true,
                volumeLoaded = true, routinesLoaded = true, isLoading = true)
        }
        compose.onAllNodesWithTag("dashboard-streak-skeleton").assertCountEquals(1)
        compose.onNodeWithTag("dashboard-volume-skeleton").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(nutritionLoaded = true, isLoading = false) }
        compose.onNodeWithTag("dashboard-streak-skeleton").assertDoesNotExist()
    }

    @Test fun calendarMarkersShowPendingStateUntilTheRequestedMonthIsReady() {
        val state = mutableStateOf(DashboardUiState(nutritionLoaded = true,
            sessionsLoaded = true, volumeLoaded = true, routinesLoaded = true,
            calendarLoading = true, isLoading = false))
        compose.setContent { MaterialTheme {
            DashboardScreen(state.value, exerciseCharts = { flowOf(ExerciseCharts(isLoading = false)) })
        } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.dash_view_calendar)).performClick()
        compose.onAllNodesWithTag("calendar-markers-skeleton").assertCountEquals(7)
        compose.runOnIdle { state.value = state.value.copy(calendarLoading = false) }
        compose.onNodeWithTag("calendar-markers-skeleton").assertDoesNotExist()
    }

    @Test fun exerciseDetailReplacesItsLocalSkeletonAsSoonAsTheResultArrives() {
        val charts = MutableStateFlow(ExerciseCharts())
        compose.setContent { MaterialTheme {
            ExerciseCollapsibleCard(ExerciseEntity(id = 1, workoutId = 1, name = "Bench"),
                exerciseCharts = { charts })
        } }
        compose.onNodeWithText("Bench", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("dashboard-exercise-charts-skeleton").assertExists()
        compose.runOnIdle { charts.value = ExerciseCharts(isLoading = false) }
        compose.onNodeWithTag("dashboard-exercise-charts-skeleton").assertDoesNotExist()
    }

    @Test fun routineChartShowsEmptyOnlyAfterItsHistoryIsPrepared() {
        val ready = mutableStateOf(false)
        compose.setContent { MaterialTheme {
            WorkoutCollapsibleCard(WorkoutEntity(id = 1, name = "Push"),
                exercises = emptyList(), workoutVolumeTrend = emptyList(),
                trendLoading = !ready.value,
                exerciseCharts = { flowOf(ExerciseCharts(isLoading = false)) })
        } }
        compose.onNodeWithText("PUSH").performClick()
        compose.onNodeWithTag("dashboard-chart-skeleton").assertExists()
        compose.runOnIdle { ready.value = true }
        compose.onNodeWithTag("dashboard-chart-skeleton").assertDoesNotExist()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.dash_no_data)).assertIsDisplayed()
    }
}
