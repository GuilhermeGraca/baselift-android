package com.example.baselift.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.R
import com.example.baselift.View.nutrition.components.QuickAddMealsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAddMealsSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val meal = MealTemplateEntity(
        id = 1, name = "Test meal", iconName = "restaurant",
        calories = 600, protein = 40, carbs = 70, fats = 10
    )

    private fun confirmDeletion() {
        compose.onNodeWithText(meal.name).performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Delete", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(350)
        val deleteLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.workout_delete)
        compose.onNodeWithText(deleteLabel).performClick()
    }

    @Test
    fun confirmationStartsDeletionBeforeAnimationAndRetainsLastRowForExit() {
        val templates = mutableStateOf(listOf(meal))
        val deletedIds = mutableListOf<Int>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                QuickAddMealsSection(
                    templates = templates.value,
                    onLogMeal = {},
                    onDeleteTemplate = {
                        deletedIds += it.id
                        // Simulate a fast Room invalidation before the exit finishes.
                        templates.value = emptyList()
                    },
                    onConfigureClick = {}
                )
            }
        }
        confirmDeletion()
        compose.runOnIdle {
            assertEquals(listOf(meal.id), deletedIds)
            assertTrue(templates.value.isEmpty())
        }
        compose.onNodeWithText(meal.name).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitUntil(2_000) {
            compose.onAllNodesWithText(meal.name).fetchSemanticsNodes().isEmpty()
        }
        val emptyLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.nutrition_no_meals)
        compose.onNodeWithText(emptyLabel).assertIsDisplayed()
    }

    @Test
    fun slowDeletionKeepsRowHiddenAndFailureAllowsItToReturn() {
        val pendingIds = mutableStateOf(emptySet<Int>())
        compose.setContent {
            MaterialTheme {
                QuickAddMealsSection(
                    templates = listOf(meal),
                    onLogMeal = {},
                    onDeleteTemplate = { pendingIds.value = setOf(it.id) },
                    onConfigureClick = {},
                    deletingTemplateIds = pendingIds.value
                )
            }
        }
        confirmDeletion()
        // The presentation snapshot expires while persistence is still pending.
        compose.waitUntil(2_000) {
            compose.onAllNodesWithText(meal.name).fetchSemanticsNodes().isEmpty()
        }
        compose.runOnIdle { assertEquals(setOf(meal.id), pendingIds.value) }
        // A failed write leaves the repository row intact and releases its pending ID.
        compose.runOnIdle { pendingIds.value = emptySet() }
        compose.onNodeWithText(meal.name).assertIsDisplayed()
    }
}
