package com.example.baselift.nutrition

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.View.nutrition.components.MacroBar
import com.example.baselift.View.nutrition.components.MacroDotText
import com.example.baselift.View.nutrition.components.MealMacroAdjustBox
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionDecimalDisplayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun macroButtonsKeepFractionalPartAndClampAtZero() {
        val value = mutableStateOf("12,5")
        compose.setContent {
            MaterialTheme {
                MealMacroAdjustBox("PROTEIN", Color.Yellow, value.value,
                    onValueChange = { value.value = it }, inputTestTag = "macro")
            }
        }
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithTag("macro").assertTextEquals("13.5")
        compose.onNodeWithText("-").performClick()
        compose.onNodeWithTag("macro").assertTextEquals("12.5")
        compose.onNodeWithTag("macro").performTextClearance()
        compose.onNodeWithTag("macro").performTextInput("0,5")
        compose.onNodeWithText("-").performClick()
        compose.onNodeWithTag("macro").assertTextEquals("0")
    }

    @Test fun summedFractionsAtGoalRenderWithoutZeroWeightOrFloatingPointArtefacts() {
        val consumed = List(10_000) { 0.01 }.sum()
        compose.setContent {
            MaterialTheme {
                Column {
                    MacroBar("PROTEIN", consumed, 100, Color.Yellow, Color.Red)
                    MacroDotText(0.1 + 0.2, "P", Color.Yellow)
                }
            }
        }
        compose.onNodeWithText("100").assertExists()
        compose.onNodeWithText("0.3g P").assertExists()
    }
}
