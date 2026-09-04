package com.example.baselift.View.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.baselift.View.theme.*
import com.example.baselift.ViewModel.onboarding.OnboardingViewModel
import kotlin.math.roundToInt

@Composable
fun CustomTargetsScreen(
    viewModel: OnboardingViewModel,
    onSaveAndSync: () -> Unit,
    onRevert: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // estado local para edição dos campos
    var calories by remember { mutableStateOf(uiState.dailyCaloriesGoal.toString()) }
    var protein by remember { mutableStateOf(uiState.proteinGoal.toString()) }
    var carbs by remember { mutableStateOf(uiState.carbsGoal.toString()) }
    var fat by remember { mutableStateOf(uiState.fatGoal.toString()) }
    
    var showAdvancedCalc by remember { mutableStateOf(false) }
    var proteinMultiplier by remember { mutableFloatStateOf(1.8f) }

    LaunchedEffect(proteinMultiplier, calories, showAdvancedCalc) {
        if (!showAdvancedCalc) return@LaunchedEffect
        
        val weightKg = if (uiState.preferredWeightUnit == "LBS") uiState.weight * 0.453592f else uiState.weight
        val totalCals = calories.toIntOrNull() ?: uiState.dailyCaloriesGoal
        
        // proteína
        val pGrams = (weightKg * proteinMultiplier).roundToInt()
        val pKcal = pGrams * 4
        
        // gordura com base no género
        val fatMultiplier = if (uiState.gender == "FEMALE") 1.0f else 0.8f
        var fGrams = (weightKg * fatMultiplier).roundToInt()
        var fKcal = fGrams * 9
        
        // verificar limites
        if (pKcal + fKcal > totalCals) {
            val remainingForFat = (totalCals - pKcal).coerceAtLeast(0)
            fGrams = remainingForFat / 9
            fKcal = fGrams * 9
        }
        
        // hidratos
        val cKcal = (totalCals - pKcal - fKcal).coerceAtLeast(0)
        val cGrams = cKcal / 4
        
        protein = pGrams.toString()
        fat = fGrams.toString()
        carbs = cGrams.toString()
    }

    // função para incrementar ou decrementar de forma segura
    fun updateValue(current: String, delta: Int, setter: (String) -> Unit) {
        val valInt = current.toIntOrNull() ?: 0
        setter((valInt + delta).coerceAtLeast(0).toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(16.dp)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(com.example.baselift.R.string.onboarding_custom_targets_title), style = Typography.headlineLarge.copy(color = CrystalWhite), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(com.example.baselift.R.string.onboarding_custom_targets_desc), style = Typography.bodyLarge.copy(color = MediumGrey), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))

        // calorias
        val totalCaloriesLabel = stringResource(com.example.baselift.R.string.onboarding_total_calories)
        MacroAdjustBox(totalCaloriesLabel, stringResource(com.example.baselift.R.string.onboarding_kcal), calories, { calories = it.filter { char -> char.isDigit() } }, { updateValue(calories, it, { calories = it }) }, NeonGreen, showBorder = true)
        Spacer(modifier = Modifier.height(16.dp))

        // calculadora avançada
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (showAdvancedCalc) DarkSurface else Color.Transparent)
                .border(1.dp, if (showAdvancedCalc) Color.Transparent else ElectricBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .clickable { showAdvancedCalc = !showAdvancedCalc }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Calculate, contentDescription = "Calculator", tint = ElectricBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(com.example.baselift.R.string.onboarding_hypertrophy_calc), color = ElectricBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Icon(
                imageVector = if (showAdvancedCalc) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = ElectricBlue
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (showAdvancedCalc) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DarkSurface).padding(16.dp)) {
                Text(stringResource(com.example.baselift.R.string.onboarding_protein_target, String.format("%.1f", proteinMultiplier)), color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                val fatFactor = if (uiState.gender == "FEMALE") "1.0g" else "0.8g"
                Text(stringResource(com.example.baselift.R.string.onboarding_hypertrophy_calc_desc, fatFactor), color = MediumGrey, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("1.6", color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = proteinMultiplier,
                        onValueChange = { proteinMultiplier = it },
                        valueRange = 1.6f..2.2f,
                        steps = 5,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = NeonGreen, 
                            activeTrackColor = NeonGreen,
                            inactiveTrackColor = DeepCharcoal,
                            activeTickColor = DeepCharcoal, 
                            inactiveTickColor = DeepCharcoal
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2.2", color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // macronutrientes
        MacroAdjustBox(stringResource(com.example.baselift.R.string.onboarding_protein_label), stringResource(com.example.baselift.R.string.onboarding_grams), protein, { protein = it.filter { char -> char.isDigit() } }, { updateValue(protein, it, { protein = it }) }, SunYellow)
        Spacer(modifier = Modifier.height(8.dp))
        MacroAdjustBox(stringResource(com.example.baselift.R.string.onboarding_carbs_label), stringResource(com.example.baselift.R.string.onboarding_grams), carbs, { carbs = it.filter { char -> char.isDigit() } }, { updateValue(carbs, it, { carbs = it }) }, ElectricBlue)
        Spacer(modifier = Modifier.height(8.dp))
        MacroAdjustBox(stringResource(com.example.baselift.R.string.onboarding_fats_label), stringResource(com.example.baselift.R.string.onboarding_grams), fat, { fat = it.filter { char -> char.isDigit() } }, { updateValue(fat, it, { fat = it }) }, VibrantPurple)

        Spacer(modifier = Modifier.height(16.dp))

        // informações
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Info, contentDescription = "Info", tint = ElectricBlue)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(com.example.baselift.R.string.onboarding_manual_override_info),
                color = CrystalWhite,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.updateCustomTargets(
                    calories = calories.toIntOrNull() ?: uiState.dailyCaloriesGoal,
                    protein = protein.toIntOrNull() ?: uiState.proteinGoal,
                    carbs = carbs.toIntOrNull() ?: uiState.carbsGoal,
                    fat = fat.toIntOrNull() ?: uiState.fatGoal
                )
                viewModel.saveUserProfile()
                onSaveAndSync()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PureBlack),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(com.example.baselift.R.string.onboarding_save_and_sync), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Refresh, contentDescription = "Sync")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(com.example.baselift.R.string.onboarding_revert_auto),
            color = CrystalWhite,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable { onRevert() },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MacroAdjustBox(
    label: String,
    unit: String,
    value: String,
    onValueChange: (String) -> Unit,
    onAdjust: (Int) -> Unit,
    accentColor: Color,
    showBorder: Boolean = label == "TOTAL CALORIES"
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (showBorder) NeonGreen else Color.Transparent, RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(if (showBorder) Alignment.CenterHorizontally else Alignment.Start)) {
            if (!showBorder) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accentColor))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(label, color = CrystalWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdjustButton(text = "-", onClick = { onAdjust(if (showBorder) -50 else -5) })
            
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.width(100.dp).background(PureBlack, RoundedCornerShape(8.dp)).padding(vertical = 4.dp),
                    textStyle = Typography.displayLarge.copy(fontSize = 24.sp, textAlign = TextAlign.Center, lineHeight = 24.sp, color = CrystalWhite),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(unit, color = MediumGrey, fontSize = 14.sp, modifier = Modifier.padding(bottom = 2.dp))
            }

            AdjustButton(text = "+", onClick = { onAdjust(if (showBorder) 50 else 5) })
        }
    }
}

@Composable
fun AdjustButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MediumGrey.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = CrystalWhite, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.offset(y = (-2).dp))
    }
}
