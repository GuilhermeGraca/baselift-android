package com.example.baselift.View.nutrition.components

import com.example.baselift.Model.nutrition.formatNutritionDecimal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.nutrition.NutritionPortionCalculator
import com.example.baselift.Model.nutrition.NutritionValues
import com.example.baselift.Model.nutrition.parseNutritionDecimal
import com.example.baselift.R
import com.example.baselift.View.theme.MediumGrey
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.PureBlack
import com.example.baselift.View.theme.SoftCoral
import com.example.baselift.ViewModel.nutrition.MealWriteState

@Composable
fun MealQuantityDialog(
    template: MealTemplateEntity,
    submissionState: MealWriteState,
    onDismiss: () -> Unit,
    onLog: (MealTemplateEntity, Double) -> Long?
) {
    var amount by rememberSaveable(template.id) { mutableStateOf("") }
    var activeRequestId by rememberSaveable(template.id) { mutableStateOf<Long?>(null) }
    var showSaveError by rememberSaveable(template.id) { mutableStateOf(false) }
    val isSubmitting = activeRequestId != null
    val grams = parseNutritionDecimal(amount)
    val totals = NutritionPortionCalculator.calculate(
        NutritionValues(template.calories, template.protein, template.carbs, template.fats),
        per100g = true, grams = grams
    )
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(submissionState, activeRequestId) {
        val requestId = activeRequestId ?: return@LaunchedEffect
        when (requestId) {
            submissionState.completedRequestId -> onDismiss()
            submissionState.failedRequestId -> { activeRequestId = null; showSaveError = true }
            else -> if (submissionState.pendingRequestId != requestId) {
                activeRequestId = null
                showSaveError = true
            }
        }
    }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF121214), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF303034), RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
        ) {
            Text(stringResource(R.string.nutrition_amount_dialog_title),
                color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(template.name, color = Color.White, fontSize = 21.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(stringResource(R.string.nutrition_per_100g_kcal, template.calories),
                color = MediumGrey, fontSize = 12.sp)
            Text(stringResource(R.string.nutrition_amount_dialog_reference_macros,
                formatNutritionDecimal(template.protein), formatNutritionDecimal(template.carbs), formatNutritionDecimal(template.fats)),
                color = MediumGrey, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Color(0xFF2A2A2F))
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.nutrition_amount_consumed_g),
                color = Color(0xFF9A9AA2), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFF1D1D21), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF333338), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = amount,
                    onValueChange = { amount = it; showSaveError = false },
                    enabled = !isSubmitting,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(NeonGreen),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("meal_amount_grams"),
                    decorationBox = { innerTextField ->
                        Box {
                            if (amount.isEmpty()) Text("0", color = MediumGrey, fontSize = 28.sp)
                            innerTextField()
                        }
                    }
                )
                Text("g", color = NeonGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFF1A1A1D), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2A2A2F), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp)
                    .testTag("meal_amount_preview")
            ) {
                Text(stringResource(R.string.nutrition_amount_dialog_preview_label),
                    color = Color(0xFF9A9AA2), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                if (totals != null) {
                    Text(stringResource(R.string.nutrition_amount_dialog_preview_calories, totals.calories),
                        color = NeonGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("meal_amount_preview_calories"))
                    Text(stringResource(R.string.nutrition_amount_dialog_preview_macros,
                        formatNutritionDecimal(totals.protein), formatNutritionDecimal(totals.carbs), formatNutritionDecimal(totals.fats)),
                        color = MediumGrey, fontSize = 11.sp, lineHeight = 15.sp,
                        modifier = Modifier.testTag("meal_amount_preview_macros"))
                } else {
                    Text(stringResource(when {
                        amount.isEmpty() -> R.string.nutrition_amount_dialog_preview_hint
                        grams == null -> R.string.nutrition_decimal_input_error
                        else -> R.string.nutrition_enter_valid_amount
                    }), color = if (amount.isEmpty()) MediumGrey else SoftCoral,
                        fontSize = 12.sp)
                }
            }
            if (showSaveError) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.nutrition_amount_dialog_error),
                    color = SoftCoral, fontSize = 11.sp,
                    modifier = Modifier.testTag("meal_amount_error"))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text(stringResource(R.string.nutrition_amount_dialog_cancel), color = MediumGrey)
                }
                Button(
                    onClick = {
                        if (grams != null && totals != null && !isSubmitting) {
                            activeRequestId = onLog(template, grams)
                            if (activeRequestId == null) showSaveError = true
                        }
                    },
                    enabled = totals != null && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen,
                        contentColor = PureBlack, disabledContainerColor = Color(0xFF333338)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("meal_amount_confirm")
                ) {
                    Text(stringResource(if (isSubmitting) R.string.nutrition_saving_entry else R.string.nutrition_add_entry),
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        LaunchedEffect(template.id) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
}
