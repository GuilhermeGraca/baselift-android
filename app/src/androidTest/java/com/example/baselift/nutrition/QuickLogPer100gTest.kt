package com.example.baselift.nutrition

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.Model.nutrition.NutritionValues
import com.example.baselift.View.nutrition.components.QuickLogSection
import com.example.baselift.ViewModel.nutrition.QuickLogSubmissionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickLogPer100gTest {
    @get:Rule val compose = createComposeRule()

    private data class Submission(
        val values: NutritionValues,
        val caloriesOnly: Boolean,
        val per100g: Boolean,
        val grams: Int?
    )

    @Test fun caloriesOnlyScalesAndClearsOnlyAfterConfirmedSave() {
        val state = mutableStateOf(QuickLogSubmissionState())
        val submitted = mutableListOf<Submission>()
        compose.setContent {
            MaterialTheme {
                QuickLogSection(state.value, { values, caloriesOnly, per100g, grams ->
                    submitted += Submission(values, caloriesOnly, per100g, grams)
                    state.value = QuickLogSubmissionState(pendingRequestId = 1)
                    1
                })
            }
        }

        compose.onNodeWithTag("nutrition_per_100g_toggle").performClick()
        compose.onNodeWithTag("nutrition_per_100g_toggle").assertIsSelected()
        compose.onNodeWithTag("nutrition_calories_input").performTextInput("200")
        compose.onNodeWithTag("nutrition_amount_grams").performTextInput("150")
        compose.onNodeWithTag("nutrition_portion_preview").assertTextEquals("TO BE LOGGED: 300 kcal")
        compose.onNodeWithText("+ ADD ENTRY").performClick()
        compose.runOnIdle {
            assertEquals(listOf(Submission(NutritionValues(200, 0, 0, 0), true, true, 150)), submitted)
        }
        compose.onNodeWithTag("nutrition_calories_input").assertTextEquals("200")
        compose.onNodeWithText("SAVING…").assertIsNotEnabled()

        compose.runOnIdle { state.value = QuickLogSubmissionState(completedRequestId = 1) }
        compose.waitForIdle()
        compose.onNodeWithTag("nutrition_calories_input").assertTextEquals("")
        compose.onNodeWithTag("nutrition_amount_grams").assertTextEquals("")
        compose.onNodeWithTag("nutrition_per_100g_toggle").assertExists()
    }

    @Test fun detailedPreviewRejectsDecimalPasteAndZeroAmount() {
        val submitted = mutableListOf<Submission>()
        compose.setContent {
            MaterialTheme {
                QuickLogSection(QuickLogSubmissionState(), { values, caloriesOnly, per100g, grams ->
                    submitted += Submission(values, caloriesOnly, per100g, grams)
                    1
                })
            }
        }
        compose.onNodeWithText("DETAILED").performClick()
        compose.onNodeWithTag("nutrition_per_100g_toggle").performClick()
        compose.onNodeWithTag("nutrition_calories_input").performTextInput("200")
        compose.onNodeWithTag("nutrition_protein_input").performTextInput("10")
        compose.onNodeWithTag("nutrition_carbs_input").performTextInput("20")
        compose.onNodeWithTag("nutrition_fats_input").performTextInput("5")

        compose.onNodeWithText("+ ADD ENTRY").assertIsNotEnabled()
        compose.onNodeWithTag("nutrition_amount_grams").performTextInput("0")
        compose.onNodeWithText("+ ADD ENTRY").assertIsNotEnabled()
        compose.onNodeWithTag("nutrition_amount_grams").performTextClearance()
        compose.onNodeWithTag("nutrition_amount_grams").performTextInput("12,5")
        compose.onNodeWithTag("nutrition_amount_grams").assertTextEquals("12,5")
        compose.onNodeWithText("Enter whole numbers only.").assertExists()
        compose.onNodeWithText("+ ADD ENTRY").assertIsNotEnabled()

        compose.onNodeWithTag("nutrition_amount_grams").performTextClearance()
        compose.onNodeWithTag("nutrition_amount_grams").performTextInput("150")
        compose.onNodeWithTag("nutrition_portion_preview")
            .assertTextEquals("TO BE LOGGED: 300 kcal · P 15 g · C 30 g · F 8 g")
        compose.onNodeWithText("+ ADD ENTRY").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(Submission(NutritionValues(200, 10, 20, 5), false, true, 150)), submitted)
        }
    }

    @Test fun failedSavePreservesDraftAndAllowsRetry() {
        val state = mutableStateOf(QuickLogSubmissionState())
        var requests = 0L
        compose.setContent {
            MaterialTheme {
                QuickLogSection(state.value, { _, _, _, _ ->
                    val id = ++requests
                    state.value = QuickLogSubmissionState(pendingRequestId = id)
                    id
                })
            }
        }
        compose.onNodeWithTag("nutrition_per_100g_toggle").performClick()
        compose.onNodeWithTag("nutrition_calories_input").performTextInput("200")
        compose.onNodeWithTag("nutrition_amount_grams").performTextInput("150")
        compose.onNodeWithText("+ ADD ENTRY").performClick()
        compose.runOnIdle { state.value = QuickLogSubmissionState(failedRequestId = 1) }
        compose.onNodeWithTag("nutrition_quick_log_error").assertExists()
        compose.onNodeWithTag("nutrition_calories_input").assertTextEquals("200")
        compose.onNodeWithTag("nutrition_amount_grams").assertTextEquals("150")
        compose.onNodeWithText("+ ADD ENTRY").performClick()
        compose.runOnIdle { assertEquals(2, requests) }
        compose.runOnIdle { state.value = QuickLogSubmissionState(completedRequestId = 2) }
        compose.onNodeWithTag("nutrition_calories_input").assertTextEquals("")
    }

    @Test fun rotationRestoresPer100gDraftWithoutSubmitting() {
        val restoration = StateRestorationTester(compose)
        var submissions = 0
        restoration.setContent {
            MaterialTheme {
                QuickLogSection(QuickLogSubmissionState(), { _, _, _, _ ->
                    submissions++
                    1
                })
            }
        }
        compose.onNodeWithTag("nutrition_per_100g_toggle").performClick()
        compose.onNodeWithTag("nutrition_calories_input").performTextInput("200")
        compose.onNodeWithTag("nutrition_amount_grams").performTextInput("150")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("nutrition_calories_input").assertTextEquals("200")
        compose.onNodeWithTag("nutrition_amount_grams").assertTextEquals("150")
        compose.onNodeWithTag("nutrition_portion_preview").assertTextEquals("TO BE LOGGED: 300 kcal")
        compose.runOnIdle { assertEquals(0, submissions) }
    }
}
