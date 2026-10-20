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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.baselift.ViewModel.dashboard.ExerciseCharts
import com.example.baselift.View.components.rememberChartRange
import kotlinx.coroutines.flow.Flow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import com.example.baselift.View.theme.*

fun LazyListScope.RoutinesSection(
    workouts: List<WorkoutEntity>,
    exercisesMap: Map<Int, List<ExerciseEntity>>,
    workoutVolumeTrends: Map<Int, List<com.example.baselift.View.components.ChartDataPoint>> = emptyMap(),
    exerciseCharts: (Int) -> Flow<ExerciseCharts>,
    onRoutineExpansionChanged: (Int, Boolean) -> Unit = { _, _ -> }
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
                exerciseCharts = exerciseCharts,
                onRoutineExpansionChanged = onRoutineExpansionChanged,
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
    exerciseCharts: (Int) -> Flow<ExerciseCharts>,
    routineColor: Color = NeonGreen,
    onRoutineExpansionChanged: (Int, Boolean) -> Unit = { _, _ -> }
) {
    var isExpanded by rememberSaveable(workout.id) { mutableStateOf(false) }
    var selectedFilter by rememberSaveable(workout.id) { mutableStateOf("ALL") }
    val onExpansionChanged by rememberUpdatedState(onRoutineExpansionChanged)
    val dateFormatter = remember { java.text.SimpleDateFormat("MMM dd", java.util.Locale.US) }
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
            DisposableEffect(workout.id) {
                onExpansionChanged(workout.id, true)
                onDispose { onExpansionChanged(workout.id, false) }
            }
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
                
                val rangeDays = when(selectedFilter) {
                    "1M" -> 30
                    "3M" -> 90
                    "1Y" -> 365
                    else -> null
                }
                val filteredWorkoutTrend = rememberChartRange(workoutVolumeTrend, rangeDays)

                if (filteredWorkoutTrend.isNotEmpty()) {
                    com.example.baselift.View.components.InteractiveChartWithControls(
                        title = "",
                        dataPoints = filteredWorkoutTrend,
                        lineColor = routineColor,
                        yUnit = "kg",
                        formatYLabel = { String.format(java.util.Locale.US, "%.0f", it) },
                        formatXLabel = { dateFormatter.format(java.util.Date(it)) },
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
                    key(exercise.id) {
                        ExerciseCollapsibleCard(
                            exercise = exercise,
                            exerciseCharts = exerciseCharts
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseCollapsibleCard(
    exercise: ExerciseEntity,
    exerciseCharts: (Int) -> Flow<ExerciseCharts>
) {
    var isExpanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var selectedFilter by rememberSaveable(exercise.id) { mutableStateOf("ALL") }
    val dateFormatter = remember { java.text.SimpleDateFormat("MMM dd", java.util.Locale.US) }
    var retryVersion by remember(exercise.id) { mutableIntStateOf(0) }
    val filters = listOf("1M", "3M", "1Y", "ALL")

    val context = androidx.compose.ui.platform.LocalContext.current
    var targetWeight by remember(exercise.id) { mutableStateOf<Float?>(null) }
    
    LaunchedEffect(exercise.id, isExpanded) {
        if (isExpanded) {
            val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val sharedPrefs = context.getSharedPreferences("baselift_targets", android.content.Context.MODE_PRIVATE)
                sharedPrefs.getFloat("target_weight_${exercise.id}", -1f)
            }
            targetWeight = saved.takeIf { it != -1f }
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
            val chartFlow = remember(exercise.id, exerciseCharts, retryVersion) { exerciseCharts(exercise.id) }
            val charts by chartFlow.collectAsStateWithLifecycle(initialValue = ExerciseCharts())
            if (charts.isLoading) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeonGreen)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(com.example.baselift.R.string.dash_exercise_loading), color = MediumGrey)
                }
            } else if (charts.loadFailed) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(com.example.baselift.R.string.dash_exercise_load_error), color = MediumGrey)
                    TextButton(onClick = { retryVersion++ }) {
                        Text(stringResource(com.example.baselift.R.string.dash_retry), color = NeonGreen)
                    }
                }
            } else {
                val volumeTrend = charts.volumeTrend
                val maxWeightTrend = charts.maxWeightTrend
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {

                    // Max Weight & Volume stats
                    val rangeDays = when(selectedFilter) {
                        "1M" -> 30
                        "3M" -> 90
                        "1Y" -> 365
                        else -> null
                    }
                    val filteredMaxWeight = rememberChartRange(maxWeightTrend, rangeDays)
                    val filteredVolume = rememberChartRange(volumeTrend, rangeDays)

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
                            formatXLabel = { dateFormatter.format(java.util.Date(it)) },
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
                            formatXLabel = { dateFormatter.format(java.util.Date(it)) },
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
}
