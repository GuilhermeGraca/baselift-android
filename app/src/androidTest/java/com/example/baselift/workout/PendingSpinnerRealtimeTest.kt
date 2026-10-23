package com.example.baselift.workout

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.View.components.PendingSpinner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android's normal frame clock; Compose's manually advanced test clock can hide stalls. */
@RunWith(AndroidJUnit4::class)
class PendingSpinnerRealtimeTest {
    @Test
    fun spinnerKeepsDrawingWithoutOtherUiUpdates() {
        val frames = CountDownLatch(12)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PendingSpinner(Color.Green, Modifier.size(24.dp).drawWithContent {
                            drawContent()
                            frames.countDown()
                        })
                    }
                }
            }
            assertTrue("The Android frame clock must keep redrawing the pending spinner",
                frames.await(3, TimeUnit.SECONDS))
        }
    }
}
