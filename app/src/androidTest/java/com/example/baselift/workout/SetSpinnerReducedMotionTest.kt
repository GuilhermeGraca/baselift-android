package com.example.baselift.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.baselift.View.workout.components.SetRow
import com.example.baselift.ViewModel.workout.SetUiModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SetSpinnerReducedMotionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    private fun samePixels(first: ImageBitmap, second: ImageBitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val before = first.toPixelMap()
        val after = second.toPixelMap()
        for (y in 0 until first.height) for (x in 0 until first.width) {
            if (before[x, y] != after[x, y]) return false
        }
        return true
    }

    @Test fun pendingSetRemainsReadableWhenAnimationsAreDisabled() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                SetRow(1, SetUiModel(1, null, 70f, 8, canBeChecked = true),
                    "", "", { _, _, _ -> }, { _, _, _ -> },
                    onLogSet = { _, _, _, _, _ -> }, isSaving = true)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val spinner = compose.onNodeWithTag("set-pending-spinner", useUnmergedTree = true)
        val still = spinner.captureToImage()
        compose.mainClock.advanceTimeBy(180)
        assertTrue("Reduced motion should keep a stable, visible pending arc",
            samePixels(still, spinner.captureToImage()))
        compose.mainClock.advanceTimeBy(220)
        compose.onNodeWithText("Saving set…").assertExists()
        compose.onNodeWithText("70kg x 8").assertExists()
        compose.onNodeWithContentDescription("Set 1 completion")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Saving set…"))
    }
}
