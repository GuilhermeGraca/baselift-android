package com.example.baselift.View.nutrition.components

import com.example.baselift.Model.nutrition.parseNutritionDecimal
import com.example.baselift.Model.nutrition.formatNutritionDecimal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.example.baselift.Model.nutrition.NutritionPortionCalculator
import com.example.baselift.Model.nutrition.NutritionValues
import com.example.baselift.Model.nutrition.parseNutritionWholeNumber
import com.example.baselift.R
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.PureBlack
import com.example.baselift.ViewModel.nutrition.QuickLogSubmissionState

private val CardColor = Color(0xFF121214)
private val FieldColor = Color(0xFF19191D)
private val ButtonColor = Color(0xFF25252A)
private val OutlineColor = Color(0xFF2A2A2F)
private val LabelColor = Color(0xFF9999A2)
private val QuietColor = Color(0xFF696970)

@Composable
fun QuickLogSection(
    submissionState: QuickLogSubmissionState,
    onAddEntry: (values: NutritionValues, isCaloriesOnly: Boolean, isPer100g: Boolean, grams: Double?) -> Long?,
    modifier: Modifier = Modifier
) {
    var isCaloriesOnly by rememberSaveable { mutableStateOf(true) }
    var isPer100g by rememberSaveable { mutableStateOf(false) }
    var kcalInput by rememberSaveable { mutableStateOf("") }
    var proteinInput by rememberSaveable { mutableStateOf("") }
    var carbsInput by rememberSaveable { mutableStateOf("") }
    var fatsInput by rememberSaveable { mutableStateOf("") }
    var gramsInput by rememberSaveable { mutableStateOf("") }
    var activeRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSaveError by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(submissionState, activeRequestId) {
        val requestId = activeRequestId ?: return@LaunchedEffect
        when (requestId) {
            submissionState.completedRequestId -> {
                kcalInput = ""
                proteinInput = ""
                carbsInput = ""
                fatsInput = ""
                gramsInput = ""
                activeRequestId = null
                showSaveError = false
            }
            submissionState.failedRequestId -> {
                activeRequestId = null
                showSaveError = true
            }
            // A saved form can outlive its ViewModel after process recreation.
            else -> if (submissionState.pendingRequestId != requestId) {
                activeRequestId = null
                showSaveError = true
            }
        }
    }

    val isSubmitting = activeRequestId != null
    val calories = parseNutritionWholeNumber(kcalInput)
    val protein = if (isCaloriesOnly) 0.0 else parseNutritionDecimal(proteinInput)
    val carbs = if (isCaloriesOnly) 0.0 else parseNutritionDecimal(carbsInput)
    val fats = if (isCaloriesOnly) 0.0 else parseNutritionDecimal(fatsInput)
    val grams = if (isPer100g) parseNutritionDecimal(gramsInput) else null
    val hasInvalidInput = calories == null || protein == null || carbs == null || fats == null ||
        (isPer100g && grams == null && gramsInput.isNotEmpty())
    val sourceValues = if (!hasInvalidInput) NutritionValues(calories!!, protein!!, carbs!!, fats!!) else null
    val totals = sourceValues?.let { NutritionPortionCalculator.calculate(it, isPer100g, grams) }

    fun updateCalories(delta: Int) {
        val current = kcalInput.toIntOrNull() ?: 0
        kcalInput = (current.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toString()
        showSaveError = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CardColor)
            .border(1.dp, OutlineColor, RoundedCornerShape(26.dp))
            .padding(horizontal = 24.dp, vertical = 26.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(R.string.nutrition_quick_log),
                color = Color.White,
                fontSize = 27.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(0.85f)
            )
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.weight(1.65f).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickLogTab(
                    title = stringResource(R.string.nutrition_cal_only),
                    selected = isCaloriesOnly,
                    enabled = !isSubmitting,
                    onClick = { isCaloriesOnly = true; showSaveError = false },
                    modifier = Modifier.weight(1f)
                )
                QuickLogTab(
                    title = stringResource(R.string.nutrition_detailed_macros),
                    selected = !isCaloriesOnly,
                    enabled = !isSubmitting,
                    onClick = { isCaloriesOnly = false; showSaveError = false },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (isPer100g) QuietColor else NeonGreen, CircleShape))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(7.dp).background(if (isPer100g) NeonGreen else QuietColor, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text(
                    text = stringResource(R.string.nutrition_portion_basis),
                    color = LabelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.7.sp
                )
            }
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasisOption(
                    label = stringResource(R.string.nutrition_serving_basis),
                    selected = !isPer100g,
                    enabled = !isSubmitting,
                    underlineWidth = 52.dp,
                    onClick = { isPer100g = false; showSaveError = false }
                )
                Text("/", color = QuietColor, fontSize = 12.sp)
                BasisOption(
                    label = stringResource(R.string.nutrition_100g_basis),
                    selected = isPer100g,
                    enabled = !isSubmitting,
                    underlineWidth = 36.dp,
                    onClick = { isPer100g = true; showSaveError = false },
                    modifier = Modifier.testTag("nutrition_per_100g_toggle")
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineColor))
        Spacer(Modifier.height(20.dp))

        if (isCaloriesOnly) {
            CaloriesTile(
                label = stringResource(if (isPer100g) R.string.nutrition_calories_per_100g else R.string.nutrition_total_cal),
                value = kcalInput,
                enabled = !isSubmitting,
                onValueChange = { kcalInput = it; showSaveError = false },
                onDecrease = { updateCalories(-100) },
                onIncrease = { updateCalories(100) }
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NutrientTile(
                    label = stringResource(R.string.nutrition_kcal_label), value = kcalInput,
                    per100g = isPer100g, enabled = !isSubmitting,
                    onValueChange = { kcalInput = it; showSaveError = false },
                    inputTestTag = "nutrition_calories_input", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f)
                )
                NutrientTile(
                    label = stringResource(R.string.nutrition_protein_g), value = proteinInput,
                    per100g = isPer100g, enabled = !isSubmitting,
                    onValueChange = { proteinInput = it; showSaveError = false },
                    inputTestTag = "nutrition_protein_input", modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NutrientTile(
                    label = stringResource(R.string.nutrition_carbs_g), value = carbsInput,
                    per100g = isPer100g, enabled = !isSubmitting,
                    onValueChange = { carbsInput = it; showSaveError = false },
                    inputTestTag = "nutrition_carbs_input", modifier = Modifier.weight(1f)
                )
                NutrientTile(
                    label = stringResource(R.string.nutrition_fats_g), value = fatsInput,
                    per100g = isPer100g, enabled = !isSubmitting,
                    onValueChange = { fatsInput = it; showSaveError = false },
                    inputTestTag = "nutrition_fats_input", modifier = Modifier.weight(1f)
                )
            }
        }

        if (isPer100g) {
            Spacer(Modifier.height(14.dp))
            AmountTile(
                value = gramsInput,
                enabled = !isSubmitting,
                onValueChange = { gramsInput = it; showSaveError = false }
            )
            if (totals != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isCaloriesOnly) {
                        stringResource(R.string.nutrition_total_to_log_calories, totals.calories)
                    } else {
                        stringResource(R.string.nutrition_total_to_log_macros,
                            totals.calories, formatNutritionDecimal(totals.protein), formatNutritionDecimal(totals.carbs), formatNutritionDecimal(totals.fats))
                    },
                    color = NeonGreen,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("nutrition_portion_preview")
                )
            } else if (gramsInput.isNotEmpty() || hasInvalidInput) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(if (hasInvalidInput) R.string.nutrition_invalid_values else R.string.nutrition_enter_valid_amount),
                    color = LabelColor,
                    fontSize = 11.sp
                )
            }
        } else if (hasInvalidInput) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.nutrition_invalid_values), color = LabelColor, fontSize = 11.sp)
        }

        if (showSaveError) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.nutrition_quick_log_save_error),
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp,
                modifier = Modifier.testTag("nutrition_quick_log_error")
            )
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                if (sourceValues != null && totals != null && !isSubmitting) {
                    showSaveError = false
                    activeRequestId = onAddEntry(sourceValues, isCaloriesOnly, isPer100g, grams)
                    if (activeRequestId == null) showSaveError = true
                }
            },
            enabled = totals != null && !isSubmitting,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonGreen,
                contentColor = PureBlack,
                disabledContainerColor = ButtonColor,
                disabledContentColor = Color(0xFFB5B5BA)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                text = stringResource(if (isSubmitting) R.string.nutrition_saving_entry else R.string.nutrition_add_entry),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun QuickLogTab(title: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val parts = title.split('\n', limit = 2)
    Column(
        modifier = modifier.selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(parts[0], color = if (selected) NeonGreen else QuietColor,
            fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
        Text(parts.getOrElse(1) { "" }, color = if (selected) LabelColor else QuietColor,
            fontWeight = FontWeight.Medium, fontSize = 9.sp, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth(0.9f).height(2.dp)
                .background(if (selected) NeonGreen else Color.Transparent, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun BasisOption(label: String, selected: Boolean, enabled: Boolean, underlineWidth: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = if (selected) NeonGreen else QuietColor,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.width(underlineWidth).height(2.dp)
                .background(if (selected) NeonGreen else Color.Transparent, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun CaloriesTile(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FieldColor)
            .border(1.dp, OutlineColor, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = LabelColor, fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("KCAL", color = NeonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.border(1.dp, NeonGreen.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                    .background(NeonGreen.copy(alpha = 0.09f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled, onDecrease)
            NumericInput(value, onValueChange, enabled, "nutrition_calories_input",
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                fontSize = 30, textAlign = TextAlign.Center)
            StepButton("+", enabled, onIncrease)
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ButtonColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color(0xFFC7C7CB) else QuietColor,
            fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NutrientTile(
    label: String,
    value: String,
    per100g: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    inputTestTag: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(FieldColor)
            .border(1.dp, OutlineColor, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = LabelColor, fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(3.dp))
            Text(
                text = if (per100g) stringResource(R.string.nutrition_100g_basis) else stringResource(R.string.nutrition_serving_badge),
                color = if (per100g) NeonGreen else QuietColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))
        NumericInput(value, onValueChange, enabled, inputTestTag, fontSize = 26, keyboardType = keyboardType)
    }
}

@Composable
private fun AmountTile(value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FieldColor)
            .border(1.dp, OutlineColor, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.nutrition_amount_consumed_g), color = LabelColor,
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.nutrition_grams_badge), color = NeonGreen,
                fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        NumericInput(value, onValueChange, enabled, "nutrition_amount_grams", fontSize = 26, keyboardType = KeyboardType.Decimal)
    }
}

@Composable
private fun NumericInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    fontSize: Int,
    textAlign: TextAlign = TextAlign.Start,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    Box(modifier = modifier, contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
        if (value.isEmpty()) {
            Text("0", color = QuietColor, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = if (enabled) Color.White else LabelColor,
                fontSize = fontSize.sp, fontWeight = FontWeight.Bold, textAlign = textAlign),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(NeonGreen),
            modifier = Modifier.fillMaxWidth().testTag(testTag)
        )
    }
}
