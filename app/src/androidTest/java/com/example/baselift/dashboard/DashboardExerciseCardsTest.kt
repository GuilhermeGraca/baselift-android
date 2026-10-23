package com.example.baselift.dashboard

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.R
import com.example.baselift.View.components.ChartDataPoint
import com.example.baselift.View.dashboard.components.ExerciseCollapsibleCard
import com.example.baselift.View.dashboard.components.RoutinesSection
import com.example.baselift.ViewModel.dashboard.ExerciseCharts
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DashboardExerciseCardsTest {
    @get:Rule val compose = createComposeRule()
    private val bench = ExerciseEntity(id = 1, workoutId = 1, name = "Bench")
    private val squat = ExerciseEntity(id = 2, workoutId = 1, name = "Squat")

    @Test
    fun collapsedPanelsDoNotQueryAndCollapseAndReopenManageSubscriptions() {
        val queries = AtomicInteger()
        val active = AtomicInteger()
        val routineActive = AtomicInteger()
        var weight = 100f
        val charts: (Int) -> Flow<ExerciseCharts> = { id ->
            queries.incrementAndGet()
            flow {
                active.incrementAndGet()
                try {
                    assertEquals(bench.id, id)
                    emit(ExerciseCharts(maxWeightTrend = listOf(
                        ChartDataPoint(System.currentTimeMillis(), weight, "weight")
                    ), isLoading = false))
                    awaitCancellation()
                } finally { active.decrementAndGet() }
            }
        }
        compose.setContent {
            MaterialTheme {
                LazyColumn {
                    RoutinesSection(
                        workouts = listOf(WorkoutEntity(id = 1, name = "Push")),
                        exercisesMap = mapOf(1 to listOf(bench, squat)), exerciseCharts = charts,
                        onRoutineExpansionChanged = { id, expanded ->
                            assertEquals(1, id)
                            routineActive.addAndGet(if (expanded) 1 else -1)
                        }
                    )
                }
            }
        }
        compose.runOnIdle { assertEquals(0, queries.get()); assertEquals(0, routineActive.get()) }
        compose.onNodeWithText("PUSH").performClick()
        compose.onNodeWithText("Bench").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, routineActive.get()) }
        compose.runOnIdle { assertEquals(0, queries.get()) }
        // Tap the name: the merged header's center can hit a nested time filter.
        compose.onNodeWithText("Bench", useUnmergedTree = true).performClick()
        compose.waitUntil(2_000) { active.get() == 1 }
        compose.onNodeWithText("100.0 kg").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, queries.get()) }
        compose.onNodeWithText("Bench", useUnmergedTree = true).performClick()
        compose.waitUntil(2_000) { active.get() == 0 }
        compose.runOnIdle { weight = 120f }
        compose.onNodeWithText("Bench", useUnmergedTree = true).performClick()
        compose.waitUntil(2_000) { active.get() == 1 }
        compose.onNodeWithText("120.0 kg").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, queries.get()) }
        // Closing the parent routine also removes the exercise's query collector.
        compose.onNodeWithText("PUSH").performClick()
        compose.waitUntil(2_000) { active.get() == 0 }
        compose.waitUntil(2_000) { routineActive.get() == 0 }
    }

    @Test
    fun failedPanelCanRetryWithoutClosingAndReopening() {
        val queries = AtomicInteger()
        val charts: (Int) -> Flow<ExerciseCharts> = {
            val attempt = queries.incrementAndGet()
            flow {
                emit(if (attempt == 1) ExerciseCharts(isLoading = false, loadFailed = true)
                    else ExerciseCharts(maxWeightTrend = listOf(
                        ChartDataPoint(System.currentTimeMillis(), 110f, "weight")
                    ), isLoading = false))
                awaitCancellation()
            }
        }
        compose.setContent { MaterialTheme { ExerciseCollapsibleCard(bench, charts) } }
        compose.onNodeWithText("Bench", useUnmergedTree = true).performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.dash_exercise_load_error)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.dash_retry)).performClick()
        compose.onNodeWithText("110.0 kg").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, queries.get()) }
    }
}
