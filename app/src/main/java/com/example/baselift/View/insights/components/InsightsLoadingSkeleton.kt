package com.example.baselift.View.insights.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.baselift.View.components.SkeletonBlock
import com.example.baselift.View.theme.DarkSurface

@Composable
internal fun InsightsWeightChartSkeleton(opacity: State<Float>) {
    Column(Modifier.fillMaxWidth().testTag("insights_weight_chart_loading")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(Modifier.width(110.dp).height(20.dp), opacity)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { SkeletonBlock(Modifier.width(30.dp).height(24.dp), opacity) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(250.dp).background(DarkSurface, RoundedCornerShape(12.dp))) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(4) { SkeletonBlock(Modifier.fillMaxWidth().height(2.dp), opacity) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(Modifier.size(28.dp), opacity)
            Spacer(Modifier.width(12.dp))
            SkeletonBlock(Modifier.width(90.dp).height(28.dp), opacity)
        }
    }
}

@Composable
internal fun InsightsWeightLedgerSkeleton(opacity: State<Float>) {
    Column(Modifier.fillMaxWidth().testTag("insights_weight_history_loading")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SkeletonBlock(Modifier.width(145.dp).height(13.dp), opacity)
            SkeletonBlock(Modifier.width(95.dp).height(17.dp), opacity)
        }
        Spacer(Modifier.height(16.dp))
        repeat(2) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    SkeletonBlock(Modifier.width(112.dp).height(14.dp), opacity)
                    Spacer(Modifier.height(5.dp))
                    SkeletonBlock(Modifier.width(72.dp).height(11.dp), opacity)
                }
                SkeletonBlock(Modifier.width(78.dp).height(18.dp), opacity)
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
internal fun InsightsPhotoGallerySkeleton(opacity: State<Float>) {
    Row(
        Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp))
            .testTag("insights_photo_gallery_loading"),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(2) {
            SkeletonBlock(Modifier.width(200.dp).fillMaxHeight(), opacity, RoundedCornerShape(12.dp))
        }
    }
}
