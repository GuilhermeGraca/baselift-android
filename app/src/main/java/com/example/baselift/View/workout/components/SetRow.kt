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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.baselift.ViewModel.workout.SetUiModel
import com.example.baselift.View.components.DelayedPendingSpinner
import com.example.baselift.View.theme.*
import kotlinx.coroutines.delay

@Composable
fun SetRow(
    exerciseId: Int,
    setModel: SetUiModel,
    weightInput: String,
    repsInput: String,
    onUpdateDraftWeight: (exerciseId: Int, setNumber: Int, value: String) -> Unit,
    onUpdateDraftReps: (exerciseId: Int, setNumber: Int, value: String) -> Unit,
    onRemove: (Int) -> Unit = {},
    onLogSet: (Int, Float, Int, Boolean, Int) -> Unit,
    isSaving: Boolean = false,
    writesBlocked: Boolean = false,
    isRemoving: Boolean = false,
    removalBlocked: Boolean = false,
    otherWritesPending: Boolean = false
) {
    var showRemoveDialog by remember { mutableStateOf(false) }
    val isBusy = isSaving || isRemoving
    var showSlowWriteLabel by remember(isBusy) { mutableStateOf(false) }
    LaunchedEffect(isBusy) {
        if (isBusy) {
            delay(350)
            showSlowWriteLabel = true
        }
    }

    if (showRemoveDialog) {
        ConfirmDeleteDialog(
            title = stringResource(com.example.baselift.R.string.workout_remove_set),
            message = stringResource(com.example.baselift.R.string.workout_remove_set_msg, setModel.setNumber),
            onConfirm = {
                onRemove(setModel.setNumber)
                showRemoveDialog = false
            },
            onDismiss = { showRemoveDialog = false },
            confirmEnabled = !isBusy && !writesBlocked && !removalBlocked && !otherWritesPending
        )
    }
    
    val log = setModel.currentLog
    val existingId = log?.id ?: 0

    val setNumber = setModel.setNumber
    val previousWeightText = remember(setModel.prevWeight) {
        val df = java.text.DecimalFormat("#.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
        setModel.prevWeight?.let(df::format) ?: "-"
    }

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
                if (setModel.canBeRemoved && !writesBlocked && !isBusy && !removalBlocked && !otherWritesPending) {
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
            "${previousWeightText}kg x ${setModel.prevReps}"
        } else "-"
        val pendingText = when {
            isRemoving -> stringResource(com.example.baselift.R.string.workout_set_removing)
            isSaving -> stringResource(com.example.baselift.R.string.workout_set_saving)
            else -> null
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(prevText, color = Color(0xFFC4C9AC), fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showSlowWriteLabel && pendingText != null) {
                Text(pendingText, color = NeonGreen, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        val hintColor = if (isFocused) MediumGrey else MediumGrey.copy(alpha = 0.5f)
        SetValueField(
            value = weightInput,
            hint = previousWeightText,
            completed = setModel.isCompleted,
            enabled = !isBusy && !writesBlocked,
            color = inputTextColor,
            hintColor = hintColor,
            modifier = Modifier.weight(0.8f).onFocusChanged { isWeightFocused = it.isFocused },
            onValueChange = { newValue ->
                var text = newValue.replace(',', '.').filter { it.isDigit() || it == '.' }
                if (text.count { it == '.' } > 1) text = text.substringBeforeLast('.')
                val parts = text.split('.')
                if (parts.size == 2 && parts[1].length > 2) text = parts[0] + "." + parts[1].take(2)
                onUpdateDraftWeight(exerciseId, setNumber, text.take(6))
            }
        )
        Spacer(modifier = Modifier.width(4.dp))
        SetValueField(
            value = repsInput,
            hint = setModel.prevReps?.toString() ?: "-",
            completed = setModel.isCompleted,
            enabled = !isBusy && !writesBlocked,
            color = inputTextColor,
            hintColor = hintColor,
            modifier = Modifier.weight(0.8f).onFocusChanged { isRepsFocused = it.isFocused },
            onValueChange = { newValue ->
                onUpdateDraftReps(exerciseId, setNumber, newValue.filter { it.isDigit() }.take(4))
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
        val checkDescription = stringResource(com.example.baselift.R.string.workout_set_check, setModel.setNumber)
        val savingDescription = stringResource(com.example.baselift.R.string.workout_set_saving)
        val removingDescription = stringResource(com.example.baselift.R.string.workout_set_removing)
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(checkboxBg)
                .semantics {
                    contentDescription = checkDescription
                    if (isBusy) stateDescription = if (isRemoving) removingDescription else savingDescription
                }
                .clickable(enabled = setModel.canBeChecked && !isBusy && !writesBlocked && !otherWritesPending, role = Role.Checkbox) {
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
            if (isBusy) {
                // The background still reflects Room's latest confirmed completion state.
                DelayedPendingSpinner(
                    color = if (setModel.isCompleted) PureBlack else NeonGreen,
                    modifier = Modifier.size(20.dp),
                    spinnerModifier = Modifier.testTag("set-pending-spinner")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = checkTint,
                        modifier = Modifier.size(20.dp))
                }
            } else {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = checkTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Keep input composition separate from the row's checkbox and pending state. Completed
// values need no editor, selection machinery or text-field minimum-size measurement.
private val SetValueTextStyle = Typography().bodyLarge.copy(
    fontSize = 20.sp,
    textAlign = TextAlign.Center,
    fontWeight = FontWeight.Black
)

@Composable
private fun SetValueField(
    value: String,
    hint: String,
    completed: Boolean,
    enabled: Boolean,
    color: Color,
    hintColor: Color,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    val fieldModifier = modifier.background(Color(0xFF353535), RoundedCornerShape(6.dp))
    if (completed) {
        Box(fieldModifier.padding(vertical = 12.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
            Text(value.ifEmpty { hint }, color = color, style = SetValueTextStyle,
                maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth())
        }
    } else {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = fieldModifier,
            textStyle = SetValueTextStyle.copy(color = color),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(CrystalWhite),
            decorationBox = { innerTextField ->
                Box(Modifier.padding(vertical = 12.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(hint, color = hintColor, style = SetValueTextStyle,
                            maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth())
                    }
                    innerTextField()
                }
            }
        )
    }
}
