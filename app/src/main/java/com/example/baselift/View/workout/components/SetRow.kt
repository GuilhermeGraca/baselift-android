package com.example.baselift.View.workout.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import com.example.baselift.ViewModel.workout.SetUiModel
import com.example.baselift.View.theme.*

@Composable
fun SetRow(
    exerciseId: Int,
    setModel: SetUiModel,
    draftWeights: Map<String, String>,
    draftReps: Map<String, String>,
    onUpdateDraftWeight: (exerciseId: Int, setNumber: Int, value: String) -> Unit,
    onUpdateDraftReps: (exerciseId: Int, setNumber: Int, value: String) -> Unit,
    onRemove: () -> Unit = {},
    onLogSet: (Int, Float, Int, Boolean, Int) -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        ConfirmDeleteDialog(
            title = stringResource(com.example.baselift.R.string.workout_remove_set),
            message = stringResource(com.example.baselift.R.string.workout_remove_set_msg, setModel.setNumber),
            onConfirm = { onRemove(); showRemoveDialog = false },
            onDismiss = { showRemoveDialog = false }
        )
    }
    
    val log = setModel.currentLog
    val existingId = log?.id ?: 0

    // dados vêm do mapa de estado do modelo de vista
    val draftKey = "${exerciseId}_${setModel.setNumber}"
    val weightInput = draftWeights[draftKey] ?: ""
    val repsInput = draftReps[draftKey] ?: ""

    // sincronização inicial
    LaunchedEffect(exerciseId, setModel.setNumber, log?.id) {
        if (weightInput.isEmpty() && log != null && log.weight > 0f) {
            val df = java.text.DecimalFormat("#.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
            onUpdateDraftWeight(exerciseId, setModel.setNumber, df.format(log.weight))
        }
        if (repsInput.isEmpty() && log != null && log.reps > 0) {
            onUpdateDraftReps(exerciseId, setModel.setNumber, log.reps.toString())
        }
    }

    var isWeightFocused by remember { mutableStateOf(false) }
    var isRepsFocused by remember { mutableStateOf(false) }
    val isFocused = isWeightFocused || isRepsFocused

    val prType = log?.prType ?: "NONE"

    val rowBgColor = if (setModel.isLastCompleted) Color(0xFF1E2413) else Color.Transparent
    val inputBgColor = Color(0xFF353535)

    val setNumberColor = when {
        setModel.isLastCompleted -> NeonGreen
        setModel.isCompleted -> CrystalWhite
        weightInput.isNotEmpty() || repsInput.isNotEmpty() -> CrystalWhite
        else -> Color(0xFF7E7E7E)
    }

    val inputTextColor = when {
        setModel.isLastCompleted -> NeonGreen
        setModel.isCompleted -> CrystalWhite
        else -> CrystalWhite
    }

    var isPressed by remember { mutableStateOf(false) }
    val animatedBgAlpha by animateFloatAsState(targetValue = if (isPressed) 0.3f else 0f, animationSpec = tween(600))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBgColor)
            .then(
                if (setModel.canBeRemoved) {
                    Modifier
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFFF6B6B).copy(alpha = animatedBgAlpha), // cor suave coral
                                    Color.Transparent
                                )
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    tryAwaitRelease()
                                    isPressed = false
                                },
                                onLongPress = {
                                    isPressed = false
                                    showRemoveDialog = true
                                }
                            )
                        }
                } else Modifier
            )
            .then(
                if (setModel.isLastCompleted) Modifier.drawBehind {
                    drawLine(
                        color = NeonGreen,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 4.dp.toPx()
                    )
                } else Modifier
            )
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // número da série
        Text(
            setModel.setNumber.toString(),
            color = setNumberColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(0.5f).padding(start = 8.dp)
        )

        // anterior
        val prevText = if (setModel.prevWeight != null && setModel.prevReps != null) {
            val df = java.text.DecimalFormat("#.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
            "${df.format(setModel.prevWeight)}kg x ${setModel.prevReps}"
        } else "-"
        Text(prevText, color = Color(0xFFC4C9AC), fontSize = 14.sp, modifier = Modifier.weight(1f))

        // entrada de peso
        BasicTextField(
            value = weightInput,
            onValueChange = { newValue ->
                var text = newValue.replace(',', '.').filter { it.isDigit() || it == '.' }
                if (text.count { it == '.' } > 1) {
                    text = text.substringBeforeLast('.')
                }
                val parts = text.split('.')
                if (parts.size == 2 && parts[1].length > 2) {
                    text = parts[0] + "." + parts[1].take(2)
                }
                if (text.length > 6) text = text.take(6)
                onUpdateDraftWeight(exerciseId, setModel.setNumber, text)
            },
            enabled = !setModel.isCompleted,
            modifier = Modifier
                .weight(0.8f)
                .background(inputBgColor, RoundedCornerShape(6.dp))
                .onFocusChanged { isWeightFocused = it.isFocused },
            textStyle = Typography().bodyLarge.copy(
                color = inputTextColor,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(CrystalWhite),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp)) {
                    if (weightInput.isEmpty()) {
                        val df = java.text.DecimalFormat("#.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
                        val hint = setModel.prevWeight?.let { df.format(it) } ?: "-"
                        Text(
                            hint,
                            color = if (isFocused) MediumGrey else MediumGrey.copy(alpha = 0.5f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else if (setModel.isCompleted) {
                        Text(
                            weightInput,
                            color = inputTextColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        innerTextField()
                    }
                }
            }
        )
        Spacer(modifier = Modifier.width(4.dp))

        // entrada de repetições
        BasicTextField(
            value = repsInput,
            onValueChange = { newValue ->
                val text = newValue.filter { it.isDigit() }.take(4)
                onUpdateDraftReps(exerciseId, setModel.setNumber, text)
            },
            enabled = !setModel.isCompleted,
            modifier = Modifier
                .weight(0.8f)
                .background(inputBgColor, RoundedCornerShape(6.dp))
                .onFocusChanged { isRepsFocused = it.isFocused },
            textStyle = Typography().bodyLarge.copy(
                color = inputTextColor,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(CrystalWhite),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp)) {
                    if (repsInput.isEmpty()) {
                        Text(
                            setModel.prevReps?.toString() ?: "-",
                            color = if (isFocused) MediumGrey else MediumGrey.copy(alpha = 0.5f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else if (setModel.isCompleted) {
                        Text(
                            repsInput,
                            color = inputTextColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        innerTextField()
                    }
                }
            }
        )
        Spacer(modifier = Modifier.width(6.dp))

        // troféu
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (prType) {
                "WEIGHT" -> Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "Weight PR",
                    tint = NeonGreen,
                    modifier = Modifier.size(18.dp)
                )
                "VOLUME" -> Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "Volume PR",
                    tint = ElectricBlue,
                    modifier = Modifier.size(18.dp)
                )
                else -> Spacer(modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(6.dp))

        // caixa de seleção
        val checkboxBg = if (setModel.isCompleted) NeonGreen else Color(0xFF353535)
        val checkTint = if (setModel.isCompleted) PureBlack else MediumGrey.copy(alpha = 0.3f)
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(checkboxBg)
                .clickable(enabled = setModel.canBeChecked) {
                    val w = weightInput.toFloatOrNull() ?: setModel.prevWeight ?: 0f
                    val r = repsInput.toIntOrNull() ?: setModel.prevReps ?: 0
                    if (!setModel.isCompleted) {
                        val df = java.text.DecimalFormat("#.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
                        onUpdateDraftWeight(exerciseId, setModel.setNumber, df.format(w))
                        onUpdateDraftReps(exerciseId, setModel.setNumber, r.toString())
                    }
                    onLogSet(setModel.setNumber, w, r, !setModel.isCompleted, existingId)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Check",
                tint = checkTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
