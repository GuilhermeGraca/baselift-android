package com.example.baselift.View.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.R
import com.example.baselift.View.components.SkeletonBlock
import com.example.baselift.View.components.rememberSkeletonOpacity
import com.example.baselift.View.theme.CrystalWhite
import com.example.baselift.View.theme.MediumGrey

@Composable
fun WorkoutRoutineTabsPlaceholder() {
    val opacity = rememberSkeletonOpacity()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("workout-tabs-skeleton"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(76.dp, 92.dp, 68.dp).forEach { width ->
            SkeletonBlock(Modifier.width(width).height(30.dp), opacity, RoundedCornerShape(50))
        }
    }
}

/** Match ExerciseCard's layout while leaving unknown values and all actions absent. */
@Composable
fun WorkoutLoadingContent(headers: List<ExerciseEntity>, modifier: Modifier = Modifier) {
    val opacity = rememberSkeletonOpacity()
    val loadingLabel = stringResource(R.string.workout_loading)
    val count = if (headers.isEmpty()) 2 else headers.size
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("workout-loading-skeleton")
            .semantics { contentDescription = loadingLabel },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
            top = if (headers.isEmpty()) 20.dp else 0.dp, bottom = 120.dp),
        userScrollEnabled = false
    ) {
        items(count, key = { index -> headers.getOrNull(index)?.id ?: "placeholder-$index" }) { index ->
            WorkoutLoadingCard(headers.getOrNull(index), opacity, index)
        }
    }
}

@Composable
private fun WorkoutLoadingCard(header: ExerciseEntity?, opacity: State<Float>, index: Int) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1B1B))
            .testTag("workout-skeleton-card")
    ) {
        Column(Modifier.padding(top = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    if (header == null) {
                        SkeletonBlock(Modifier.width(if (index == 0) 178.dp else 142.dp).height(28.dp), opacity)
                        Spacer(Modifier.height(4.dp))
                        SkeletonBlock(Modifier.width(56.dp).height(12.dp), opacity)
                    } else {
                        Text(header.name, color = CrystalWhite, fontSize = 24.sp,
                            fontWeight = FontWeight.Bold)
                        if (header.equipment.isNotBlank()) {
                            Text(header.equipment, color = MediumGrey, fontSize = 12.sp)
                        }
                    }
                }
                SkeletonBlock(Modifier.width(4.dp).height(18.dp), opacity)
            }
            Spacer(Modifier.height(10.dp))
            if (header == null) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.width(46.dp).height(26.dp), opacity, RoundedCornerShape(4.dp))
                    SkeletonBlock(Modifier.width(58.dp).height(26.dp), opacity, RoundedCornerShape(4.dp))
                }
            } else if (header.muscleGroups.isNotBlank()) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    header.muscleGroups.split(",").forEach { tag ->
                        Box(Modifier.background(Color(0xFF333333), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(tag.trim(), color = Color(0xFFC4C9AC), fontSize = 10.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val labelColor = Color(0xFFC4C9AC)
                Text(stringResource(R.string.workout_set_col), color = labelColor, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f).padding(start = 8.dp))
                Text(stringResource(R.string.workout_prev_col), color = labelColor, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.workout_kg_col), color = labelColor, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.workout_reps_col), color = labelColor, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
                Spacer(Modifier.width(64.dp))
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFF232323), thickness = 1.dp)
            Spacer(Modifier.height(2.dp))
        }
        repeat(3) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("workout-skeleton-set-row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(0.5f).padding(start = 8.dp)) {
                    SkeletonBlock(Modifier.width(13.dp).height(22.dp), opacity)
                }
                Box(Modifier.weight(1f)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.86f).height(12.dp), opacity)
                }
                SkeletonBlock(Modifier.weight(0.8f).height(56.dp), opacity)
                Spacer(Modifier.width(4.dp))
                SkeletonBlock(Modifier.weight(0.8f).height(56.dp), opacity)
                Spacer(Modifier.width(6.dp))
                Spacer(Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                SkeletonBlock(Modifier.size(32.dp), opacity)
            }
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier.fillMaxWidth().height(48.dp).background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            SkeletonBlock(Modifier.width(72.dp).height(12.dp), opacity)
        }
    }
}
