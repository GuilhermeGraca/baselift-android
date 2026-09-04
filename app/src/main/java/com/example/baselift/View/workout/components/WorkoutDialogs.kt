package com.example.baselift.View.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import com.example.baselift.View.theme.*

@Composable
fun RestTimerWidget(modifier: Modifier = Modifier) {
    var totalSeconds by remember { mutableStateOf(105) } // padrão é 01:45
    var defaultSeconds by remember { mutableStateOf(105) }
    var isRunning by remember { mutableStateOf(false) }

    // lógica do temporizador
    LaunchedEffect(isRunning) {
        while (isRunning && totalSeconds > 0) {
            delay(1000)
            totalSeconds--
            if (totalSeconds == 0) isRunning = false
        }
    }

    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)

    var showEditDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .background(PureBlack)
            .padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(com.example.baselift.R.string.workout_rest_timer), color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Timer, contentDescription = "Timer", tint = NeonGreen, modifier = Modifier.size(16.dp))
            }
            
            Text(
                text = timeString,
                color = CrystalWhite,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.clickable { showEditDialog = true }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        isRunning = false
                        totalSeconds = defaultSeconds
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(stringResource(com.example.baselift.R.string.workout_reset), color = PureBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .clickable { isRunning = !isRunning },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = CrystalWhite
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        var minInput by remember { mutableStateOf((defaultSeconds / 60).toString()) }
        var secInput by remember { mutableStateOf((defaultSeconds % 60).toString()) }

        Dialog(onDismissRequest = { showEditDialog = false }) {
            GenericInputDialog(
                title = stringResource(com.example.baselift.R.string.workout_set_rest_timer),
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = minInput,
                            onValueChange = { minInput = it },
                            modifier = Modifier.weight(1f).background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(16.dp),
                            textStyle = Typography().bodyLarge.copy(color = CrystalWhite, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Text(":", color = CrystalWhite, fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterVertically))
                        BasicTextField(
                            value = secInput,
                            onValueChange = { secInput = it },
                            modifier = Modifier.weight(1f).background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(16.dp),
                            textStyle = Typography().bodyLarge.copy(color = CrystalWhite, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                onConfirm = {
                    val m = minInput.toIntOrNull() ?: 0
                    val s = secInput.toIntOrNull() ?: 0
                    defaultSeconds = (m * 60) + s
                    totalSeconds = defaultSeconds
                    isRunning = false
                    showEditDialog = false
                },
                onDismiss = { showEditDialog = false }
            )
        }
    }
}

@Composable
fun GenericInputDialog(
    title: String,
    content: @Composable () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PureBlack)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(24.dp)
    ) {
        Column {
            Text(title, color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            content()
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(com.example.baselift.R.string.workout_cancel), color = MediumGrey)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)) {
                    Text(stringResource(com.example.baselift.R.string.workout_save), color = PureBlack)
                }
            }
        }
    }
}

@Composable
fun EditExerciseDialog(
    currentName: String,
    currentEquipment: String,
    currentMuscleGroups: String,
    onConfirm: (name: String, equipment: String, muscleGroups: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var equipment by remember { mutableStateOf(currentEquipment) }
    var muscleGroups by remember { mutableStateOf(currentMuscleGroups) }

    Dialog(onDismissRequest = onDismiss) {
        GenericInputDialog(
            title = stringResource(com.example.baselift.R.string.workout_edit_ex_title),
            content = {
                // nome
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(16.dp),
                    textStyle = Typography().bodyLarge.copy(color = CrystalWhite),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text(stringResource(com.example.baselift.R.string.workout_edit_ex_name_hint), color = MediumGrey)
                        inner()
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                // equipamento
                BasicTextField(
                    value = equipment,
                    onValueChange = { equipment = it },
                    modifier = Modifier.fillMaxWidth().background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(16.dp),
                    textStyle = Typography().bodyLarge.copy(color = CrystalWhite),
                    decorationBox = { inner ->
                        if (equipment.isEmpty()) Text(stringResource(com.example.baselift.R.string.workout_edit_ex_eq_hint), color = MediumGrey)
                        inner()
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                // grupos musculares
                BasicTextField(
                    value = muscleGroups,
                    onValueChange = { muscleGroups = it },
                    modifier = Modifier.fillMaxWidth().background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(16.dp),
                    textStyle = Typography().bodyLarge.copy(color = CrystalWhite),
                    decorationBox = { inner ->
                        if (muscleGroups.isEmpty()) Text(stringResource(com.example.baselift.R.string.workout_edit_ex_musc_hint), color = MediumGrey)
                        inner()
                    }
                )
            },
            onConfirm = {
                if (name.isNotBlank()) onConfirm(name, equipment, muscleGroups)
            },
            onDismiss = onDismiss
        )
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PureBlack)
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(title, color = Color(0xFFFF4444), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(message, color = CrystalWhite, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(com.example.baselift.R.string.workout_cancel), color = MediumGrey)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))) {
                        Text(stringResource(com.example.baselift.R.string.workout_delete), color = CrystalWhite)
                    }
                }
            }
        }
    }
}
