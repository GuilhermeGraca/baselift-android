package com.example.baselift.View.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baselift.View.theme.*
import java.util.Calendar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable

// secção do consistency journal no dashboard
@Composable
fun ConsistencyJournalSection(
    nutritionStreak: Int,
    workoutStreak: Int,
    nutritionDaysThisWeek: Map<Int, com.example.baselift.ViewModel.dashboard.NutritionSummary>,
    workoutDaysThisWeek: Map<Int, List<String>>,
    workoutSessionsThisWeek: Int,
    historicalCalendarData: Map<String, com.example.baselift.View.components.DayMarkerState>,
    restDays: Int,
    nutritionRestDays: Int,
    onSetRestDays: (Int) -> Unit = {},
    onSetNutritionRestDays: (Int) -> Unit = {}
) {
    Text(
        stringResource(com.example.baselift.R.string.dash_consistency_journal),
        color = CrystalWhite,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(16.dp))

    var showRestDaysDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var restDaysInput by androidx.compose.runtime.remember(restDays) { androidx.compose.runtime.mutableStateOf(restDays) }

    var showNutritionRestDaysDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var nutritionRestDaysInput by androidx.compose.runtime.remember(nutritionRestDays) { androidx.compose.runtime.mutableStateOf(nutritionRestDays) }

    var showCalendarDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var clickedMarker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.example.baselift.View.components.DayMarkerState?>(null) }

    // linha de streaks (fora do cartão principal)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StreakCard(
            icon = Icons.Default.Restaurant,
            value = nutritionStreak,
            label = stringResource(com.example.baselift.R.string.dash_nutrition_streak),
            accentColor = ElectricBlue,
            modifier = Modifier.weight(1f).clickable { showNutritionRestDaysDialog = true }
        )
        StreakCard(
            icon = Icons.Default.FitnessCenter,
            value = workoutStreak,
            label = stringResource(com.example.baselift.R.string.dash_workout_streak),
            accentColor = NeonGreen,
            modifier = Modifier.weight(1f).clickable { showRestDaysDialog = true }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // cartão principal apenas para os dias da semana
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131313))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        // secção da nutrição
        WeekCalendarRow(
            title = stringResource(com.example.baselift.R.string.dash_nutrition_tab),
            rightLabel = stringResource(com.example.baselift.R.string.dash_this_week),
            isActiveDay = { it in nutritionDaysThisWeek.keys },
            icon = Icons.Default.Restaurant,
            activeColor = ElectricBlue,
            tooltipContent = { day ->
                val nut = nutritionDaysThisWeek[day]
                if (nut != null) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222222))
                            .border(1.dp, ElectricBlue.copy(alpha=0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text("${nut.calories} kcal", color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(VibrantPurple))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${nut.protein}g", color = CrystalWhite, fontSize = 10.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(SunYellow))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${nut.carbs}g", color = CrystalWhite, fontSize = 10.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ElectricBlue))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${nut.fats}g", color = CrystalWhite, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // secção do workout
        WeekCalendarRow(
            title = stringResource(com.example.baselift.R.string.dash_workout_tab),
            rightLabel = stringResource(com.example.baselift.R.string.dash_workouts_count, workoutSessionsThisWeek),
            isActiveDay = { it in workoutDaysThisWeek.keys },
            icon = Icons.Default.FitnessCenter,
            activeColor = NeonGreen,
            tooltipContent = { day ->
                val workouts = workoutDaysThisWeek[day] ?: emptyList()
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222222))
                        .border(1.dp, NeonGreen.copy(alpha=0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    workouts.forEach { wName ->
                        Text(wName, color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
        
        androidx.compose.material3.TextButton(
            onClick = { showCalendarDialog = true },
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 4.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(stringResource(com.example.baselift.R.string.dash_view_calendar), color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showCalendarDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCalendarDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureBlack)
                    .border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                com.example.baselift.View.components.CustomCalendar(
                    mode = com.example.baselift.View.components.CalendarMode.VIEW,
                    maxDate = System.currentTimeMillis(),
                    markedDays = historicalCalendarData,
                    onDayClickInViewMode = { _, marker ->
                        if (marker.hasWorkout || marker.hasNutrition) {
                            clickedMarker = marker
                        }
                    }
                )
            }
        }
    }

    if (clickedMarker != null) {
        val marker = clickedMarker!!
        androidx.compose.ui.window.Dialog(onDismissRequest = { clickedMarker = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222222))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                if (marker.hasWorkout) {
                    Text(stringResource(com.example.baselift.R.string.dash_workouts_label), color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    marker.workoutNames.forEach { wName ->
                        Text("• $wName", color = CrystalWhite, fontSize = 14.sp)
                    }
                }
                
                if (marker.hasWorkout && marker.hasNutrition) {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (marker.hasNutrition) {
                    Text(stringResource(com.example.baselift.R.string.dash_nutrition_tab), color = ElectricBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${marker.nutritionCalories ?: 0} kcal", color = CrystalWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(com.example.baselift.View.theme.VibrantPurple))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${marker.nutritionProtein ?: 0}g", color = CrystalWhite, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(com.example.baselift.View.theme.SunYellow))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${marker.nutritionCarbs ?: 0}g", color = CrystalWhite, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ElectricBlue))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${marker.nutritionFats ?: 0}g", color = CrystalWhite, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showRestDaysDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showRestDaysDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureBlack)
                    .border(1.dp, NeonGreen.copy(alpha=0.2f), RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text(stringResource(com.example.baselift.R.string.dash_workout_streak), color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(com.example.baselift.R.string.dash_rest_days_desc), color = MediumGrey, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(com.example.baselift.R.string.dash_rest_days_note), color = MediumGrey, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = { if (restDaysInput > 0) restDaysInput-- },
                        modifier = Modifier.background(Color.White.copy(alpha=0.1f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Text("-", color = CrystalWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Text("$restDaysInput", color = NeonGreen, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(24.dp))
                    androidx.compose.material3.IconButton(
                        onClick = { if (restDaysInput < 7) restDaysInput++ },
                        modifier = Modifier.background(Color.White.copy(alpha=0.1f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Text("+", color = CrystalWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                androidx.compose.material3.Button(
                    onClick = {
                        if (restDaysInput in 0..7) {
                            onSetRestDays(restDaysInput)
                            showRestDaysDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text(stringResource(com.example.baselift.R.string.dash_save), color = PureBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (showNutritionRestDaysDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showNutritionRestDaysDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureBlack)
                    .border(1.dp, ElectricBlue.copy(alpha=0.2f), RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text(stringResource(com.example.baselift.R.string.dash_nutrition_streak), color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(com.example.baselift.R.string.dash_nut_rest_days_desc), color = MediumGrey, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(com.example.baselift.R.string.dash_nut_rest_days_note), color = MediumGrey, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = { if (nutritionRestDaysInput > 0) nutritionRestDaysInput-- },
                        modifier = Modifier.background(Color.White.copy(alpha=0.1f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Text("-", color = CrystalWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Text("$nutritionRestDaysInput", color = ElectricBlue, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(24.dp))
                    androidx.compose.material3.IconButton(
                        onClick = { if (nutritionRestDaysInput < 7) nutritionRestDaysInput++ },
                        modifier = Modifier.background(Color.White.copy(alpha=0.1f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Text("+", color = CrystalWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                androidx.compose.material3.Button(
                    onClick = {
                        if (nutritionRestDaysInput in 0..7) {
                            onSetNutritionRestDays(nutritionRestDaysInput)
                            showNutritionRestDaysDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text(stringResource(com.example.baselift.R.string.dash_save), color = PureBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// cartão individual de streak
@Composable
private fun StreakCard(
    icon: ImageVector,
    value: Int,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val isGlowing = value >= 7
    val glowShadow = if (isGlowing) androidx.compose.ui.graphics.Shadow(color = accentColor, blurRadius = 25f) else null

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp, 
                if (isGlowing) accentColor.copy(alpha = 0.6f) else accentColor.copy(alpha = 0.3f), 
                RoundedCornerShape(12.dp)
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isGlowing) 0.25f else 0.15f),
                        accentColor.copy(alpha = if (isGlowing) 0.1f else 0.05f)
                    )
                )
            )
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isGlowing) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                    )
                }
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "$value",
                color = accentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(shadow = glowShadow)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            color = CrystalWhite.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

// linha do calendário semanal
@Composable
private fun WeekCalendarRow(
    title: String,
    rightLabel: String,
    icon: ImageVector,
    activeColor: Color,
    isActiveDay: (Int) -> Boolean,
    tooltipContent: @Composable (Int) -> Unit
) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    // determinar o dia atual da semana (1=Seg..7=Dom)
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = Calendar.MONDAY
    val rawDow = cal.get(Calendar.DAY_OF_WEEK)
    val todayIndex = if (rawDow == Calendar.SUNDAY) 7 else rawDow - 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = activeColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            rightLabel,
            color = MediumGrey,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dayLabels.forEachIndexed { index, label ->
            val dayNumber = index + 1 // 1=Seg..7=Dom
            val isActive = isActiveDay(dayNumber)
            val isFuture = dayNumber > todayIndex
            var showTooltip by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (isActive) {
                                Modifier
                                    .background(activeColor.copy(alpha = 0.15f))
                                    .border(1.dp, activeColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .clickable { showTooltip = !showTooltip }
                            } else {
                                Modifier
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = when {
                            isActive -> activeColor
                            isFuture -> MediumGrey.copy(alpha = 0.3f)
                            else -> MediumGrey.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    label,
                    color = when {
                        isActive -> CrystalWhite
                        isFuture -> MediumGrey.copy(alpha = 0.3f)
                        else -> MediumGrey
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                
                if (showTooltip && isActive) {
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.TopCenter,
                        offset = androidx.compose.ui.unit.IntOffset(0, -150),
                        onDismissRequest = { showTooltip = false }
                    ) {
                        tooltipContent(dayNumber)
                    }
                }
            }
        }
    }
}
