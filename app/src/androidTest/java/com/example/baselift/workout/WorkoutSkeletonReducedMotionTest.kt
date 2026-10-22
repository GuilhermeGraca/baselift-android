package com.example.baselift.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.example.baselift.View.workout.components.WorkoutLoadingContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class WorkoutSkeletonReducedMotionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    private fun samePixels(first: ImageBitmap, second: ImageBitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        for (y in 0 until first.height) for (x in 0 until first.width) {
            if (a[x, y] != b[x, y]) return false
        }
        return true
    }

    @Test fun placeholdersRemainStableAndVisibleWithAnimationsDisabled() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { WorkoutLoadingContent(emptyList()) } }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(compose.onAllNodesWithTag("workout-skeleton-set-row").fetchSemanticsNodes().isNotEmpty())
        val skeleton = compose.onNodeWithTag("workout-loading-skeleton")
        val first = skeleton.captureToImage()
        compose.mainClock.advanceTimeBy(250)
        assertTrue("Reduced motion should leave the skeleton stable",
            samePixels(first, skeleton.captureToImage()))
    }
}
