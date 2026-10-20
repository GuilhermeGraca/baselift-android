package com.example.baselift.View.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baselift.View.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CalendarMode {
    SELECT,
    VIEW
}

data class DayMarkerState(
    val hasWorkout: Boolean = false,
    val hasNutrition: Boolean = false,
    val workoutNames: List<String> = emptyList(),
    val nutritionCalories: Int? = null,
    val nutritionProtein: Int? = null,
    val nutritionCarbs: Int? = null,
    val nutritionFats: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCalendar(
    mode: CalendarMode,
    selectedDate: Long? = null,
    minDate: Long? = null,
    maxDate: Long? = null,
    markedDays: Map<String, DayMarkerState> = emptyMap(),
    onDateSelected: (Long) -> Unit = {},
    onDayClickInViewMode: (Long, DayMarkerState) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onVisibleMonthChanged: (Long) -> Unit = {}
) {
    val initialCal = Calendar.getInstance()
    if (selectedDate != null) initialCal.timeInMillis = selectedDate
    initialCal.set(Calendar.DAY_OF_MONTH, 1)
    initialCal.set(Calendar.HOUR_OF_DAY, 0)
    initialCal.set(Calendar.MINUTE, 0)
    initialCal.set(Calendar.SECOND, 0)
    initialCal.set(Calendar.MILLISECOND, 0)

    var currentMonth by remember { mutableStateOf(initialCal) }
    val onMonthChanged by rememberUpdatedState(onVisibleMonthChanged)
    LaunchedEffect(currentMonth.timeInMillis) { onMonthChanged(currentMonth.timeInMillis) }
    var showYearSelector by remember { mutableStateOf(false) }

    val monthFormat = remember { SimpleDateFormat("MMMM", Locale.US) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    
    val todayStr = remember { dayFormat.format(Date()) }

    Column(modifier = modifier.fillMaxWidth()) {
        // cabeçalho do calendário
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val newCal = currentMonth.clone() as Calendar
                newCal.add(Calendar.MONTH, -1)
                currentMonth = newCal
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = CrystalWhite)
            }

            // seletor de ano e mês
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showYearSelector = !showYearSelector }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${monthFormat.format(currentMonth.time).uppercase(Locale.US)} ${currentMonth.get(Calendar.YEAR)}",
                        color = CrystalWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Year", tint = CrystalWhite)
                }

                DropdownMenu(
                    expanded = showYearSelector,
                    onDismissRequest = { showYearSelector = false },
                    modifier = Modifier.background(DarkSurface).heightIn(max = 300.dp)
                ) {
                    val currentY = Calendar.getInstance().get(Calendar.YEAR)
                    for (year in currentY downTo (currentY - 10)) {
                        DropdownMenuItem(
                            text = { Text(year.toString(), color = CrystalWhite) },
                            onClick = {
                                val newCal = currentMonth.clone() as Calendar
                                newCal.set(Calendar.YEAR, year)
                                currentMonth = newCal
                                showYearSelector = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = {
                val newCal = currentMonth.clone() as Calendar
                newCal.add(Calendar.MONTH, 1)
                currentMonth = newCal
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = CrystalWhite)
            }
        }

        // dias da semana
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    color = MediumGrey,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // grelha de dias
        val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = currentMonth.get(Calendar.DAY_OF_WEEK) - 1 // começa no domingo

        val totalCells = daysInMonth + firstDayOfWeek
        val rows = Math.ceil(totalCells / 7.0).toInt()

        Column(modifier = Modifier.fillMaxWidth()) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - firstDayOfWeek + 1

                        if (dayNumber in 1..daysInMonth) {
                            val dayCal = currentMonth.clone() as Calendar
                            dayCal.set(Calendar.DAY_OF_MONTH, dayNumber)
                            val dayTimestamp = dayCal.timeInMillis
                            val dayStr = dayFormat.format(dayCal.time)

                            val isToday = dayStr == todayStr
                            val isSelected = mode == CalendarMode.SELECT && selectedDate != null && dayFormat.format(Date(selectedDate)) == dayStr
                            
                            val isFuture = maxDate != null && dayTimestamp > maxDate
                            val isTooEarly = minDate != null && dayTimestamp < minDate
                            val isDisabled = isFuture || isTooEarly

                            val marker = markedDays[dayStr]

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // fundo e interação
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(y = if (mode == CalendarMode.VIEW) (-6).dp else 0.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> NeonGreen.copy(alpha = 0.2f)
                                                isToday -> Color.White.copy(alpha = 0.05f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) NeonGreen else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .then(
                                            if (!isDisabled) {
                                                Modifier.clickable {
                                                    if (mode == CalendarMode.SELECT) {
                                                        onDateSelected(dayTimestamp)
                                                    } else {
                                                        onDayClickInViewMode(dayTimestamp, marker ?: DayMarkerState())
                                                    }
                                                }
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        color = if (isDisabled) MediumGrey.copy(alpha = 0.3f) else if (isSelected) NeonGreen else CrystalWhite,
                                        fontSize = 14.sp,
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                // marcadores de eventos
                                if (marker != null && (marker.hasWorkout || marker.hasNutrition)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 2.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        if (marker.hasWorkout && marker.hasNutrition) {
                                            // ponto com contorno
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(NeonGreen, CircleShape)
                                                    .border(2.dp, ElectricBlue, CircleShape)
                                            )
                                        } else if (marker.hasWorkout) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(NeonGreen, CircleShape)
                                            )
                                        } else if (marker.hasNutrition) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(Color.Transparent, CircleShape)
                                                    .border(2.dp, ElectricBlue, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // espaço vazio
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp))
                        }
                    }
                }
            }
        }
    }
}
