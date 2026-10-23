package com.example.baselift.View.nutrition.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.nutrition.parseNutritionWholeNumber
import com.example.baselift.ViewModel.nutrition.MealWriteState
import com.example.baselift.View.theme.ElectricBlue
import com.example.baselift.View.theme.MediumGrey
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.PureBlack
import com.example.baselift.View.theme.SunYellow
import com.example.baselift.View.theme.VibrantPurple
import com.example.baselift.View.theme.SoftCoral

@Composable
fun ConfigureMealDialog(
    onDismiss: () -> Unit,
    onSave: (MealTemplateEntity) -> Long?,
    saveState: MealWriteState = MealWriteState()
) {
    var name by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fats by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var isPer100g by rememberSaveable { mutableStateOf(false) }
    var activeRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSaveError by rememberSaveable { mutableStateOf(false) }
    val isSaving = activeRequestId != null

    LaunchedEffect(saveState, activeRequestId) {
        val requestId = activeRequestId ?: return@LaunchedEffect
        when (requestId) {
            saveState.completedRequestId -> onDismiss()
            saveState.failedRequestId -> { activeRequestId = null; showSaveError = true }
            else -> if (saveState.pendingRequestId != requestId) {
                activeRequestId = null
                showSaveError = true
            }
        }
    }

    val parsedCalories = parseNutritionWholeNumber(calories)
    val parsedProtein = parseNutritionWholeNumber(protein)
    val parsedCarbs = parseNutritionWholeNumber(carbs)
    val parsedFats = parseNutritionWholeNumber(fats)
    val valuesValid = listOf(parsedCalories, parsedProtein, parsedCarbs, parsedFats).all { it != null } &&
        (!isPer100g || listOf(parsedCalories, parsedProtein, parsedCarbs, parsedFats).any { it != 0 })
    val hasEnteredValues = listOf(calories, protein, carbs, fats).any { it.isNotEmpty() }
    val canSave = name.isNotBlank() && valuesValid && !isSaving

    val iconOptions = listOf(
        Triple("restaurant", Icons.Default.Restaurant, NeonGreen),
        Triple("drink", Icons.Default.LocalDrink, ElectricBlue),
        Triple("cake", Icons.Default.Cake, SunYellow),
        Triple("fastfood", Icons.Default.Fastfood, SoftCoral),
        Triple("coffee", Icons.Default.LocalCafe, VibrantPurple)
    )
    var selectedIconIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentIconOption = iconOptions[selectedIconIndex]

    fun recalculateCalories() {
        val p = parseNutritionWholeNumber(protein) ?: return
        val c = parseNutritionWholeNumber(carbs) ?: return
        val f = parseNutritionWholeNumber(fats) ?: return
        val result = p.toLong() * 4 + c.toLong() * 4 + f.toLong() * 9
        if (result <= Int.MAX_VALUE) calories = result.toString()
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack.copy(alpha = 0.8f))
                .clickable(enabled = !isSaving) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = maxHeight * 0.9f)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1F1F1F))
                    .border(2.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {} // block dismiss on dialog click
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(stringResource(com.example.baselift.R.string.nutrition_configure_meal), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(com.example.baselift.R.string.nutrition_macro_override), color = MediumGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2A2A))
                            .clickable(enabled = !isSaving) { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MediumGrey, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Template Name
                Text(stringResource(com.example.baselift.R.string.nutrition_template_name), color = MediumGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B1B1B)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(56.dp)
                            .border(1.dp, currentIconOption.third, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                            .clickable(enabled = !isSaving) { selectedIconIndex = (selectedIconIndex + 1) % iconOptions.size },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(currentIconOption.second, contentDescription = null, tint = currentIconOption.third, modifier = Modifier.size(24.dp))
                    }
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        if (name.isEmpty()) {
                            Text(stringResource(com.example.baselift.R.string.nutrition_meal_name), color = MediumGrey.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it; showSaveError = false },
                            enabled = !isSaving,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth().testTag("meal_template_name")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF161618))
                        .border(1.dp, Color(0xFF303034), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(com.example.baselift.R.string.nutrition_template_per_100g),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(stringResource(com.example.baselift.R.string.nutrition_template_per_100g_hint),
                            color = MediumGrey, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF252529))
                            .border(1.dp, Color(0xFF333338), RoundedCornerShape(12.dp))
                            .padding(3.dp)
                            .selectableGroup(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(false, true).forEach { per100g ->
                            val selected = isPer100g == per100g
                            val choiceShape = RoundedCornerShape(9.dp)
                            Box(
                                modifier = Modifier.clip(choiceShape)
                                    .background(if (selected) Color(0xFF2D2E2D) else Color.Transparent)
                                    .border(1.dp,
                                        if (selected) NeonGreen.copy(alpha = 0.55f) else Color.Transparent,
                                        choiceShape)
                                    .selectable(selected = selected, enabled = !isSaving,
                                        role = Role.RadioButton,
                                        onClick = { isPer100g = per100g; showSaveError = false })
                                    .then(if (per100g) Modifier.testTag("meal_template_per_100g_toggle") else Modifier)
                                    .padding(horizontal = 8.dp, vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(if (per100g) com.example.baselift.R.string.nutrition_100g_basis
                                        else com.example.baselift.R.string.nutrition_serving_basis).uppercase(),
                                    color = if (selected) NeonGreen else Color(0xFFB0B0B5),
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Estimated Total
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1D1D1D))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(if (isPer100g) com.example.baselift.R.string.nutrition_calories_per_100g else com.example.baselift.R.string.nutrition_estimated_total), color = MediumGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Box(contentAlignment = Alignment.BottomStart) {
                                if (calories.isEmpty()) {
                                    Text("0", color = Color.White.copy(alpha = 0.3f), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                BasicTextField(
                                    value = calories,
                                    onValueChange = { calories = it; showSaveError = false },
                                    enabled = !isSaving,
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                    cursorBrush = SolidColor(NeonGreen),
                                    modifier = Modifier.widthIn(min = 20.dp, max = 100.dp).testTag("meal_template_calories")
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(com.example.baselift.R.string.nutrition_kcal_label), color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }

                    androidx.compose.material3.IconButton(
                        onClick = { recalculateCalories() },
                        enabled = !isSaving,
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Recalculate", tint = NeonGreen, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Macros
                MealMacroAdjustBox(label = stringResource(com.example.baselift.R.string.nutrition_protein), color = SunYellow, value = protein, onValueChange = { protein = it; showSaveError = false }, enabled = !isSaving, inputTestTag = "meal_template_protein")
                Spacer(modifier = Modifier.height(12.dp))
                MealMacroAdjustBox(label = stringResource(com.example.baselift.R.string.nutrition_carbs), color = ElectricBlue, value = carbs, onValueChange = { carbs = it; showSaveError = false }, enabled = !isSaving, inputTestTag = "meal_template_carbs")
                Spacer(modifier = Modifier.height(12.dp))
                MealMacroAdjustBox(label = stringResource(com.example.baselift.R.string.nutrition_fats), color = VibrantPurple, value = fats, onValueChange = { fats = it; showSaveError = false }, enabled = !isSaving, inputTestTag = "meal_template_fats")

                if ((!valuesValid && hasEnteredValues) || showSaveError) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(if (showSaveError) com.example.baselift.R.string.nutrition_template_save_error else com.example.baselift.R.string.nutrition_template_values_error),
                        color = SoftCoral, fontSize = 11.sp,
                        modifier = Modifier.testTag("meal_template_error")
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Confirm Button
                Button(
                    onClick = {
                        if (canSave) {
                            val newTemplate = MealTemplateEntity(
                                name = name,
                                iconName = currentIconOption.first,
                                calories = parsedCalories!!,
                                protein = parsedProtein!!,
                                carbs = parsedCarbs!!,
                                fats = parsedFats!!,
                                isPer100g = isPer100g
                            )
                            activeRequestId = onSave(newTemplate)
                            if (activeRequestId == null) showSaveError = true
                        }
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(if (isSaving) com.example.baselift.R.string.nutrition_saving_entry else com.example.baselift.R.string.nutrition_confirm_template), color = if (canSave) PureBlack else MediumGrey, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun MealMacroAdjustBox(
    label: String,
    color: Color,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    inputTestTag: String? = null
) {
    fun adjust(delta: Int) {
        val current = value.toIntOrNull() ?: 0
        onValueChange((current.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toString())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1B1B))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable(enabled = enabled) { adjust(-1) },
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = MediumGrey, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            
            Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.width(36.dp).then(if (inputTestTag != null) Modifier.testTag(inputTestTag) else Modifier)
                    )
                    Text("g", color = MediumGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                }
                if (value.isEmpty()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("0", color = MediumGrey.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
                        Text("g", color = MediumGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable(enabled = enabled) { adjust(1) },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = MediumGrey, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
