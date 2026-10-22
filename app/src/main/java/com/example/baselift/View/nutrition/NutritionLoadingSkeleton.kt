package com.example.baselift.View.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.baselift.View.components.SkeletonBlock

private val cardColor = Color(0xFF1B1B1B)

@Composable
internal fun NutritionProgressSkeleton(opacity: State<Float>) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("nutrition_progress_loading"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Match the 236 dp calorie ring and its inset instead of replacing it with a solid disk.
        Box(Modifier.size(236.dp).padding(16.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value }) {
                drawCircle(
                    color = Color(0xFF414141),
                    radius = (size.minDimension - 14.dp.toPx()) / 2f,
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SkeletonBlock(Modifier.width(110.dp).height(42.dp), opacity)
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(Modifier.width(140.dp).height(12.dp), opacity)
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NutritionTotalSkeleton(opacity, Alignment.Start)
                NutritionTotalSkeleton(opacity, Alignment.End)
            }
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(8.dp), opacity)
            // The real calorie bar reserves 16 dp for its target marker.
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().background(cardColor, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            repeat(3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SkeletonBlock(Modifier.width(82.dp).height(15.dp), opacity)
                    SkeletonBlock(Modifier.width(66.dp).height(18.dp), opacity)
                }
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(Modifier.fillMaxWidth().height(8.dp), opacity)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun NutritionTotalSkeleton(opacity: State<Float>, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        SkeletonBlock(Modifier.width(62.dp).height(11.dp), opacity)
        Spacer(Modifier.height(4.dp))
        SkeletonBlock(Modifier.width(80.dp).height(21.dp), opacity)
    }
}

@Composable
internal fun NutritionTargetSkeleton(opacity: State<Float>) {
    SkeletonBlock(
        Modifier.fillMaxWidth().height(48.dp).testTag("nutrition_target_loading"),
        opacity,
        RoundedCornerShape(12.dp)
    )
}

@Composable
internal fun NutritionMealsSkeleton(opacity: State<Float>) {
    Column(Modifier.fillMaxWidth().testTag("nutrition_meals_loading")) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBlock(Modifier.width(158.dp).height(20.dp), opacity)
            SkeletonBlock(Modifier.width(78.dp).height(16.dp), opacity)
        }
        // A template row uses a 48 dp icon, two text lines and a calorie total.
        repeat(2) {
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(Modifier.size(48.dp), opacity, RoundedCornerShape(8.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(Modifier.width(110.dp).height(18.dp), opacity)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(Modifier.width(86.dp).height(11.dp), opacity)
                }
                SkeletonBlock(Modifier.width(58.dp).height(21.dp), opacity)
            }
        }
    }
}

@Composable
internal fun NutritionLogsSkeleton(opacity: State<Float>) {
    Column(Modifier.fillMaxWidth().testTag("nutrition_logs_loading")) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SkeletonBlock(Modifier.width(122.dp).height(14.dp), opacity)
            SkeletonBlock(Modifier.width(62.dp).height(20.dp), opacity)
        }
        repeat(2) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(Modifier.width(130.dp).height(17.dp), opacity)
                SkeletonBlock(Modifier.width(64.dp).height(17.dp), opacity)
            }
        }
    }
}
