package com.example.baselift.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.View.nutrition.components.ConfigureMealDialog
import com.example.baselift.View.nutrition.components.MealQuantityDialog
import com.example.baselift.View.nutrition.components.QuickAddMealsSection
import com.example.baselift.ViewModel.nutrition.MealWriteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealPer100gFlowTest {
    @get:Rule val compose = createComposeRule()

    private val normal = MealTemplateEntity(id = 1, name = "Rice", iconName = "restaurant",
        calories = 300, protein = 6.0, carbs = 60.0, fats = 2.0)
    private val per100g = MealTemplateEntity(id = 2, name = "Yogurt", iconName = "restaurant",
        calories = 200, protein = 10.0, carbs = 20.0, fats = 5.0, isPer100g = true)

    @Test fun mealListRequestsAmountOnlyForPer100gTemplate() {
        val logged = mutableListOf<Int>()
        val requested = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                QuickAddMealsSection(
                    templates = listOf(normal, per100g),
                    onLogMeal = { logged += it.id },
                    onDeleteTemplate = {},
                    onConfigureClick = {},
                    onRequestAmount = { requested += it.id }
                )
            }
        }
        compose.onNodeWithText("/ 100g").assertExists()
        compose.onNodeWithText("TAP TO ENTER AMOUNT").assertExists()
        compose.onNodeWithText("Yogurt").performClick()
        compose.runOnIdle {
            assertEquals(listOf(2), requested)
            assertTrue(logged.isEmpty())
        }
        compose.onNodeWithText("Rice").performClick()
        compose.runOnIdle { assertEquals(listOf(1), logged) }
    }

    @Test fun quantityDialogScalesOnlyOnConfirmationAndClosesAfterSuccess() {
        val state = mutableStateOf(MealWriteState())
        val visible = mutableStateOf(true)
        val requests = mutableListOf<Double>()
        compose.setContent {
            MaterialTheme {
                if (visible.value) MealQuantityDialog(per100g, state.value,
                    onDismiss = { visible.value = false },
                    onLog = { _, grams ->
                        requests += grams
                        state.value = MealWriteState(pendingRequestId = 1)
                        1
                    })
            }
        }
        compose.onNodeWithTag("meal_amount_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("meal_amount_grams").performTextInput("0")
        compose.onNodeWithTag("meal_amount_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("meal_amount_grams").performTextClearance()
        compose.onNodeWithTag("meal_amount_grams").performTextInput("12,5")
        compose.onNodeWithTag("meal_amount_grams").assertTextEquals("12,5")
        compose.onNodeWithTag("meal_amount_confirm").assertIsEnabled()
        compose.runOnIdle { assertTrue(requests.isEmpty()) }

        compose.onNodeWithTag("meal_amount_grams").performTextClearance()
        compose.onNodeWithTag("meal_amount_grams").performTextInput("150.5")
        compose.onNodeWithTag("meal_amount_preview_calories").assertTextEquals("301 kcal")
        compose.onNodeWithTag("meal_amount_preview_macros")
            .assertTextEquals("P 15.05 g · C 30.1 g · F 7.53 g")
        compose.onNodeWithTag("meal_amount_confirm").performClick()
        compose.runOnIdle { assertEquals(listOf(150.5), requests) }
        compose.onNodeWithTag("meal_amount_confirm").assertIsNotEnabled()
        compose.runOnIdle { state.value = MealWriteState(completedRequestId = 1) }
        compose.onNodeWithTag("meal_amount_grams").assertDoesNotExist()
        compose.runOnIdle { assertFalse(visible.value) }
    }

    @Test fun quantityDialogCancelDoesNotLogAndFailureKeepsAmount() {
        val state = mutableStateOf(MealWriteState())
        val visible = mutableStateOf(true)
        var requests = 0
        compose.setContent {
            MaterialTheme {
                if (visible.value) MealQuantityDialog(per100g, state.value,
                    onDismiss = { visible.value = false },
                    onLog = { _, _ ->
                        val id = ++requests
                        state.value = MealWriteState(pendingRequestId = id.toLong())
                        id.toLong()
                    })
            }
        }
        compose.onNodeWithTag("meal_amount_grams").performTextInput("150.5")
        compose.onNodeWithTag("meal_amount_confirm").performClick()
        compose.runOnIdle { state.value = MealWriteState(failedRequestId = 1) }
        compose.onNodeWithTag("meal_amount_error").assertExists()
        compose.onNodeWithTag("meal_amount_grams").assertTextEquals("150.5")
        compose.onNodeWithText("CANCEL").performClick()
        compose.runOnIdle {
            assertEquals(1, requests)
            assertFalse(visible.value)
        }
    }

    @Test fun templateConfigurationPersistsPer100gChoiceOnlyAfterSave() {
        val state = mutableStateOf(MealWriteState())
        val visible = mutableStateOf(true)
        val saved = mutableListOf<MealTemplateEntity>()
        compose.setContent {
            MaterialTheme {
                if (visible.value) ConfigureMealDialog(
                    onDismiss = { visible.value = false },
                    onSave = { template ->
                        saved += template
                        state.value = MealWriteState(pendingRequestId = 1)
                        1
                    },
                    saveState = state.value
                )
            }
        }
        compose.onNodeWithTag("meal_template_name").performTextInput("Yogurt")
        compose.onNodeWithTag("meal_template_per_100g_toggle").performClick()
        compose.onNodeWithTag("meal_template_calories").performTextInput("200")
        compose.onNodeWithTag("meal_template_protein").performScrollTo().performTextInput("10,5")
        compose.onNodeWithText("CONFIRM TEMPLATE").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, saved.size)
            assertTrue(saved.single().isPer100g)
            assertEquals(200, saved.single().calories)
            assertEquals(10.5, saved.single().protein, 0.000001)
            assertTrue(visible.value)
        }
        compose.runOnIdle { state.value = MealWriteState(completedRequestId = 1) }
        compose.runOnIdle { assertFalse(visible.value) }
    }
}
