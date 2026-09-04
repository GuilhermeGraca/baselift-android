package com.example.baselift.View.nutrition.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.View.theme.CrystalWhite
import com.example.baselift.View.theme.ElectricBlue
import com.example.baselift.View.theme.MediumGrey
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.PureBlack
import com.example.baselift.View.theme.SoftCoral
import com.example.baselift.View.theme.SunYellow
import com.example.baselift.View.theme.VibrantPurple

@Composable
fun TodaysLogsSection(
    logs: List<NutritionLogEntity>,
    onDeleteLog: (NutritionLogEntity) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (logs.isEmpty()) return

    var showAllLogs by remember { mutableStateOf(false) }
    var logToDelete by remember { mutableStateOf<NutritionLogEntity?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(com.example.baselift.R.string.nutrition_todays_logs),
                color = SunYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SoftCoral.copy(alpha = 0.2f))
                    .clickable { showResetDialog = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(com.example.baselift.R.string.nutrition_reset_all), color = SoftCoral, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureBlack)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val sortedLogs = logs.sortedByDescending { it.timestamp }
                val displayLogs = if (showAllLogs) sortedLogs else sortedLogs.take(4)

                displayLogs.forEachIndexed { index, log ->
                    val alphaValue = if (!showAllLogs && index == 3 && sortedLogs.size > 4) 0.4f else 1f

                    var isPressed by remember { mutableStateOf(false) }
                    val animatedBgAlpha by animateFloatAsState(targetValue = if (isPressed) 0.3f else 0f, animationSpec = tween(600))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(alphaValue)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        SoftCoral.copy(alpha = animatedBgAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isPressed = true
                                        val success = tryAwaitRelease()
                                        isPressed = false
                                    },
                                    onLongPress = {
                                        isPressed = false
                                        logToDelete = log
                                    }
                                )
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = log.name ?: stringResource(com.example.baselift.R.string.nutrition_quick_add),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "+${log.calories}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(com.example.baselift.R.string.nutrition_kcal),
                                    color = MediumGrey,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }

                        // Mostrar os macros se existirem
                        if (log.protein > 0 || log.carbs > 0 || log.fats > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (log.protein > 0) {
                                    MacroDotText(value = log.protein, unit = "P", color = SunYellow)
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                if (log.carbs > 0) {
                                    MacroDotText(value = log.carbs, unit = "C", color = ElectricBlue)
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                if (log.fats > 0) {
                                    MacroDotText(value = log.fats, unit = "F", color = VibrantPurple)
                                }
                            }
                        }
                    }
                    
                    if (index < displayLogs.size - 1) {
                        HorizontalDivider(color = Color(0xFF2A2A2A))
                    }
                }
                
                if (sortedLogs.size > 4) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (showAllLogs) stringResource(com.example.baselift.R.string.nutrition_show_less) else stringResource(com.example.baselift.R.string.nutrition_show_more),
                        color = MediumGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAllLogs = !showAllLogs }
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (logToDelete != null) {
        Dialog(onDismissRequest = { logToDelete = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureBlack)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text(stringResource(com.example.baselift.R.string.nutrition_delete_entry), color = SoftCoral, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(com.example.baselift.R.string.nutrition_delete_entry_msg, logToDelete?.name ?: stringResource(com.example.baselift.R.string.nutrition_this_entry)), color = CrystalWhite, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { logToDelete = null }) {
                        Text(stringResource(com.example.baselift.R.string.workout_cancel), color = MediumGrey)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            logToDelete?.let { onDeleteLog(it) }
                            logToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SoftCoral),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(com.example.baselift.R.string.workout_delete), color = PureBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        Dialog(onDismissRequest = { showResetDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureBlack)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text(stringResource(com.example.baselift.R.string.nutrition_reset_all_title), color = SoftCoral, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(com.example.baselift.R.string.nutrition_reset_all_msg), color = CrystalWhite, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text(stringResource(com.example.baselift.R.string.workout_cancel), color = MediumGrey)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onResetAll()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SoftCoral),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(com.example.baselift.R.string.nutrition_reset), color = PureBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MacroDotText(value: Int, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${value}g $unit",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
