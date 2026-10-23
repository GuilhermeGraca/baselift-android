package com.example.baselift.View.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baselift.R
import com.example.baselift.View.components.SkeletonBlock
import com.example.baselift.View.components.rememberSkeletonOpacity
import com.example.baselift.View.theme.CrystalWhite
import com.example.baselift.View.theme.DarkSurface

@Composable
fun DashboardVolumeSkeleton() {
    val opacity = rememberSkeletonOpacity()
    Column(Modifier.fillMaxWidth().testTag("dashboard-volume-skeleton")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dash_total_volume), color = CrystalWhite,
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { SkeletonBlock(Modifier.width(27.dp).height(22.dp), opacity) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().height(250.dp)
            .background(DarkSurface, RoundedCornerShape(12.dp)).padding(16.dp)) {
            SkeletonBlock(Modifier.width(112.dp).height(32.dp), opacity)
            Spacer(Modifier.height(6.dp))
            SkeletonBlock(Modifier.width(86.dp).height(12.dp), opacity)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(105.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom) {
                listOf(40.dp, 76.dp, 54.dp, 90.dp, 62.dp).forEach { height ->
                    SkeletonBlock(Modifier.width(28.dp).height(height), opacity,
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun DashboardRoutinesSkeleton() {
    val opacity = rememberSkeletonOpacity()
    Column(Modifier.fillMaxWidth().testTag("dashboard-routines-skeleton")) {
        repeat(2) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)
                .background(Color(0xFF181818), RoundedCornerShape(12.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(40.dp), opacity, RoundedCornerShape(8.dp))
                Spacer(Modifier.width(16.dp))
                SkeletonBlock(Modifier.width(if (it == 0) 136.dp else 104.dp).height(18.dp), opacity)
                Spacer(Modifier.weight(1f))
                SkeletonBlock(Modifier.width(20.dp).height(20.dp), opacity)
            }
        }
    }
}

@Composable
fun DashboardChartSkeleton(height: androidx.compose.ui.unit.Dp = 250.dp, opacity: State<Float>? = null) {
    val animatedOpacity = opacity ?: rememberSkeletonOpacity()
    Box(Modifier.fillMaxWidth().height(height)
        .background(DarkSurface, RoundedCornerShape(8.dp)).testTag("dashboard-chart-skeleton"),
        contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(height - 40.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(36.dp, 74.dp, 53.dp, 92.dp).forEach { bar ->
                SkeletonBlock(Modifier.width(28.dp).height(minOf(bar, height - 52.dp)), animatedOpacity)
            }
        }
    }
}

@Composable
fun DashboardExerciseChartsSkeleton() {
    val opacity = rememberSkeletonOpacity()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        .testTag("dashboard-exercise-charts-skeleton")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                Column(Modifier.weight(1f).background(DarkSurface, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    SkeletonBlock(Modifier.width(86.dp).height(12.dp), opacity)
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.width(64.dp).height(18.dp), opacity)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        SkeletonBlock(Modifier.width(80.dp).height(12.dp), opacity)
        Spacer(Modifier.height(8.dp))
        DashboardChartSkeleton(150.dp, opacity)
        Spacer(Modifier.height(20.dp))
        SkeletonBlock(Modifier.width(116.dp).height(12.dp), opacity)
        Spacer(Modifier.height(8.dp))
        DashboardChartSkeleton(220.dp, opacity)
    }
}
