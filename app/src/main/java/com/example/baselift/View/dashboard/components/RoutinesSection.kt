package com.example.baselift.View.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListScope
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.View.theme.*

fun LazyListScope.RoutinesSection(
    workouts: List<WorkoutEntity>,
    exercisesMap: Map<Int, List<ExerciseEntity>>,
    workoutVolumeTrends: Map<Int, List<com.example.baselift.View.components.ChartDataPoint>> = emptyMap(),
    exerciseVolumeTrends: Map<Int, List<com.example.baselift.View.components.ChartDataPoint>> = emptyMap(),
    exerciseMaxWeightTrends: Map<Int, List<com.example.baselift.View.components.ChartDataPoint>> = emptyMap()
) {
    item {
        Text(
            stringResource(com.example.baselift.R.string.dash_routines),
            color = CrystalWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (workouts.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(com.example.baselift.R.string.dash_no_routines), color = MediumGrey)
            }
        }
    } else {
        val routineColors = listOf(NeonGreen, SunYellow, ElectricBlue, VibrantPurple, SoftCoral)
        items(workouts.size, key = { index -> workouts[index].id }) { index ->
            val workout = workouts[index]
            val rColor = routineColors[index % routineColors.size]
            val exercises = exercisesMap[workout.id] ?: emptyList()
            WorkoutCollapsibleCard(
                workout = workout,
                exercises = exercises,
                workoutVolumeTrend = workoutVolumeTrends[workout.id] ?: emptyList(),
                exerciseVolumeTrends = exerciseVolumeTrends,
                exerciseMaxWeightTrends = exerciseMaxWeightTrends,
                routineColor = rColor
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun WorkoutCollapsibleCard(
    workout: WorkoutEntity, 
    exercises: List<ExerciseEntity>,
    workoutVolumeTrend: List<com.example.baselift.View.components.ChartDataPoint>,
    exerciseVolumeTrends: Map<Int, List<com.example.baselift.View.components.ChartDataPoint>>,
    exerciseMaxWeightTrends: Map<Int, List<com.example.baselift.View.components.ChartDataPoint>>,
    routineColor: Color = NeonGreen
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    val filters = listOf("1M", "3M", "1Y", "ALL")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181818))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val initial = workout.name.firstOrNull()?.uppercase() ?: "W"
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(routineColor.copy(alpha = 0.05f))
                        .border(1.dp, routineColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initial, color = routineColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = workout.name.uppercase(),
                    color = CrystalWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = routineColor
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(com.example.baselift.R.string.dash_volume_trend),
                        color = CrystalWhite.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.End) {
                        filters.forEach { filter ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selectedFilter == filter) routineColor else DarkSurface)
                                    .clickable { selectedFilter = filter }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    filter,
                                    color = if (selectedFilter == filter) PureBlack else CrystalWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                val now = System.currentTimeMillis()
                val filteredWorkoutTrend = when(selectedFilter) {
                    "1M" -> workoutVolumeTrend.filter { now - it.xValue <= 30L * 24 * 60 * 60 * 1000 }
                    "3M" -> workoutVolumeTrend.filter { now - it.xValue <= 90L * 24 * 60 * 60 * 1000 }
                    "1Y" -> workoutVolumeTrend.filter { now - it.xValue <= 365L * 24 * 60 * 60 * 1000 }
                    else -> workoutVolumeTrend
                }

                if (filteredWorkoutTrend.isNotEmpty()) {
                    com.example.baselift.View.components.InteractiveChartWithControls(
                        title = "",
                        dataPoints = filteredWorkoutTrend,
                        lineColor = routineColor,
                        yUnit = "kg",
                        formatYLabel = { String.format(java.util.Locale.US, "%.0f", it) },
                        formatXLabel = { java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(java.util.Date(it)) },
                        targetValue = null,
                        onSetTargetValue = {},
                        showTimeFilters = false,
                        showTargetControls = false,
                        chartHeight = 250.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222222))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(com.example.baselift.R.string.dash_no_data), color = MediumGrey, fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                exercises.forEach { exercise ->
                    ExerciseCollapsibleCard(
                        exercise = exercise,
                        volumeTrend = exerciseVolumeTrends[exercise.id] ?: emptyList(),
                        maxWeightTrend = exerciseMaxWeightTrends[exercise.id] ?: emptyList()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ExerciseCollapsibleCard(
    exercise: ExerciseEntity,
    volumeTrend: List<com.example.baselift.View.components.ChartDataPoint>,
    maxWeightTrend: List<com.example.baselift.View.components.ChartDataPoint>
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    val filters = listOf("1M", "3M", "1Y", "ALL")

    val context = androidx.compose.ui.platform.LocalContext.current
    var targetWeight by remember { mutableStateOf<Float?>(null) }
    
    LaunchedEffect(exercise.id) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("baselift_targets", android.content.Context.MODE_PRIVATE)
            val saved = sharedPrefs.getFloat("target_weight_${exercise.id}", -1f)
            if (saved != -1f) {
                targetWeight = saved
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = exercise.name,
                color = CrystalWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            
            if (isExpanded) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.padding(end = 8.dp)) {
                    filters.forEach { filter ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selectedFilter == filter) NeonGreen else DarkSurface)
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                filter,
                                color = if (selectedFilter == filter) PureBlack else CrystalWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = NeonGreen
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {

                // Max Weight & Volume stats
                val now = System.currentTimeMillis()
                val filteredMaxWeight = when(selectedFilter) {
                    "1M" -> maxWeightTrend.filter { now - it.xValue <= 30L * 24 * 60 * 60 * 1000 }
                    "3M" -> maxWeightTrend.filter { now - it.xValue <= 90L * 24 * 60 * 60 * 1000 }
                    "1Y" -> maxWeightTrend.filter { now - it.xValue <= 365L * 24 * 60 * 60 * 1000 }
                    else -> maxWeightTrend
                }
                val filteredVolume = when(selectedFilter) {
                    "1M" -> volumeTrend.filter { now - it.xValue <= 30L * 24 * 60 * 60 * 1000 }
                    "3M" -> volumeTrend.filter { now - it.xValue <= 90L * 24 * 60 * 60 * 1000 }
                    "1Y" -> volumeTrend.filter { now - it.xValue <= 365L * 24 * 60 * 60 * 1000 }
                    else -> volumeTrend
                }

                val bestMaxWeightPoint = filteredMaxWeight.maxByOrNull { it.yValue }
                val overallEst1RM = bestMaxWeightPoint?.extraValue ?: 0f
                val maxW = filteredMaxWeight.maxOfOrNull { it.yValue } ?: 0f
                val maxV = filteredVolume.maxOfOrNull { it.yValue } ?: 0f
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        NeonGreen.copy(alpha = 0.15f),
                                        NeonGreen.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(com.example.baselift.R.string.dash_max_weight), color = CrystalWhite.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${String.format(java.util.Locale.US, "%.1f", maxW)} kg", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, ElectricBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        ElectricBlue.copy(alpha = 0.15f),
                                        ElectricBlue.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(com.example.baselift.R.string.dash_max_volume), color = CrystalWhite.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${String.format(java.util.Locale.US, "%.0f", maxV)} kg", color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(com.example.baselift.R.string.dash_est_1rm, String.format(java.util.Locale.US, "%.1f", overallEst1RM)), color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    stringResource(com.example.baselift.R.string.dash_volume),
                    color = CrystalWhite.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (filteredVolume.isNotEmpty()) {
                    com.example.baselift.View.components.BarChartWithControls(
                        title = "",
                        dataPoints = filteredVolume,
                        barColor = ElectricBlue,
                        yUnit = "kg",
                        formatYLabel = { String.format(java.util.Locale.US, "%.0f", it) },
                        formatXLabel = { java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(java.util.Date(it)) },
                        summaryValue = null,
                        summarySubtitle = null,
                        summarySubtitleColor = CrystalWhite,
                        showTimeFilters = false,
                        chartHeight = 150.dp
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(Color(0xFF242424), RoundedCornerShape(4.dp)))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    stringResource(com.example.baselift.R.string.dash_absolute_weight),
                    color = CrystalWhite.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (filteredMaxWeight.isNotEmpty()) {
                    com.example.baselift.View.components.InteractiveChartWithControls(
                        title = "",
                        dataPoints = filteredMaxWeight,
                        lineColor = NeonGreen,
                        yUnit = "kg",
                        formatYLabel = { String.format(java.util.Locale.US, "%.1f", it) },
                        formatXLabel = { java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(java.util.Date(it)) },
                        targetValue = targetWeight,
                        onSetTargetValue = { newTarget: Float? ->
                            targetWeight = newTarget
                            val ctx = context
                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val sharedPrefs = ctx.getSharedPreferences("baselift_targets", android.content.Context.MODE_PRIVATE)
                                val editor = sharedPrefs.edit()
                                if (newTarget == null) editor.remove("target_weight_${exercise.id}")
                                else editor.putFloat("target_weight_${exercise.id}", newTarget)
                                editor.apply()
                            }
                        },
                        showTimeFilters = false,
                        showTargetControls = true,
                        chartHeight = 220.dp
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(Color(0xFF242424), RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
