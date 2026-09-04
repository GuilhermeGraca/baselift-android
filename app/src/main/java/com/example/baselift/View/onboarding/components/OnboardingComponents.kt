package com.example.baselift.View.onboarding.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.View.theme.*
import com.example.baselift.ViewModel.onboarding.OnboardingViewModel

@Composable
fun OnboardingHeader(
    currentStep: Int,
    totalSteps: Int,
    onBackClick: () -> Unit,
    onStepClick: (Int) -> Unit,
    uiState: UserEntity
) {
    val canJumpToStep: (Int) -> Boolean = { targetStep ->
        when (targetStep) {
            0 -> true
            1 -> uiState.age > 0 && uiState.weight > 0f && uiState.height > 0f
            2 -> uiState.age > 0 && uiState.weight > 0f && uiState.height > 0f && uiState.activityLevel.isNotEmpty()
            3 -> uiState.age > 0 && uiState.weight > 0f && uiState.height > 0f && uiState.activityLevel.isNotEmpty() && uiState.goal.isNotEmpty()
            else -> false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = if (currentStep > 0) CrystalWhite else Color.Transparent,
            modifier = Modifier.clickable(enabled = currentStep > 0) { onBackClick() }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until totalSteps) {
                val isClickable = canJumpToStep(i)
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                i == currentStep -> NeonGreen
                                i < currentStep -> CrystalWhite.copy(alpha = 0.5f)
                                isClickable -> CrystalWhite.copy(alpha = 0.2f)
                                else -> DarkSurface
                            }
                        )
                        .clickable(enabled = isClickable) { onStepClick(i) }
                )
            }
        }

        Text(
            text = stringResource(com.example.baselift.R.string.onboarding_step_of, currentStep + 1, totalSteps),
            color = NeonGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// PASSO 1: PERFIL
@Composable
fun StepProfile(
    uiState: UserEntity,
    viewModel: OnboardingViewModel,
    onNext: () -> Unit
) {
    var ageStr by remember { mutableStateOf(if (uiState.age > 0) uiState.age.toString() else "") }
    var weightStr by remember { mutableStateOf(if (uiState.weight > 0f) uiState.weight.toString() else "") }
    var heightStr by remember { mutableStateOf(if (uiState.height > 0f) uiState.height.toString() else "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(stringResource(com.example.baselift.R.string.onboarding_build_profile), style = Typography.headlineLarge.copy(color = CrystalWhite))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(com.example.baselift.R.string.onboarding_build_profile_desc),
            style = Typography.bodyLarge.copy(color = MediumGrey)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // seleção do género
        Text(stringResource(com.example.baselift.R.string.onboarding_gender), color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            GenderButton(
                text = stringResource(com.example.baselift.R.string.onboarding_male),
                isSelected = uiState.gender == "MALE",
                onClick = { viewModel.updateGender("MALE") },
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Male
            )
            GenderButton(
                text = stringResource(com.example.baselift.R.string.onboarding_female),
                isSelected = uiState.gender == "FEMALE",
                onClick = { viewModel.updateGender("FEMALE") },
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Female
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // idade
        Text(stringResource(com.example.baselift.R.string.onboarding_age), color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextField(
            value = ageStr,
            onValueChange = { 
                ageStr = it.filter { char -> char.isDigit() }
                viewModel.updateAge(ageStr.toIntOrNull() ?: 0) 
            },
            placeholder = "25",
            keyboardType = KeyboardType.Number
        )

        Spacer(modifier = Modifier.height(16.dp))

        // peso
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(com.example.baselift.R.string.onboarding_current_weight), color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            UnitToggle(
                option1 = "KG",
                option2 = "LBS",
                selectedOption = uiState.preferredWeightUnit,
                onSelect = { viewModel.updateWeight(uiState.weight, it) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextField(
            value = weightStr,
            onValueChange = { 
                weightStr = it.replace(",", ".")
                viewModel.updateWeight(weightStr.toFloatOrNull() ?: 0f, uiState.preferredWeightUnit) 
            },
            placeholder = "75.5",
            keyboardType = KeyboardType.Decimal
        )

        Spacer(modifier = Modifier.height(16.dp))

        // altura
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(com.example.baselift.R.string.onboarding_height), color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            UnitToggle(
                option1 = "CM",
                option2 = "FT",
                selectedOption = uiState.preferredHeightUnit,
                onSelect = { viewModel.updateHeight(uiState.height, it) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextField(
            value = heightStr,
            onValueChange = { 
                heightStr = it.replace(",", ".")
                viewModel.updateHeight(heightStr.toFloatOrNull() ?: 0f, uiState.preferredHeightUnit) 
            },
            placeholder = "180",
            keyboardType = KeyboardType.Decimal
        )

        }
        Spacer(modifier = Modifier.height(16.dp))
        NeonButton(text = stringResource(com.example.baselift.R.string.onboarding_continue), onClick = onNext, enabled = uiState.age > 0 && uiState.weight > 0f && uiState.height > 0f)
    }
}

// PASSO 2: NÍVEL DE ATIVIDADE
@Composable
fun StepActivityLevel(
    uiState: UserEntity,
    onLevelSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(stringResource(com.example.baselift.R.string.onboarding_activity_level), style = Typography.headlineLarge.copy(color = CrystalWhite))
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(com.example.baselift.R.string.onboarding_activity_level_desc), style = Typography.bodyLarge.copy(color = MediumGrey))
        Spacer(modifier = Modifier.height(16.dp))

        val levels = listOf(
            "Sedentary" to stringResource(com.example.baselift.R.string.onboarding_sedentary_desc),
            "Light" to stringResource(com.example.baselift.R.string.onboarding_light_desc),
            "Moderate" to stringResource(com.example.baselift.R.string.onboarding_moderate_desc),
            "Active" to stringResource(com.example.baselift.R.string.onboarding_active_desc),
            "Very Active" to stringResource(com.example.baselift.R.string.onboarding_very_active_desc),
            "Extra Active" to stringResource(com.example.baselift.R.string.onboarding_extra_active_desc)
        )

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.forEach { (level, desc) ->
                SelectionCard(
                    title = level,
                    description = desc,
                    isSelected = uiState.activityLevel == level,
                    onClick = { onLevelSelected(level) },
                    verticalPadding = 14.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        NeonButton(text = stringResource(com.example.baselift.R.string.onboarding_continue), onClick = onNext, enabled = uiState.activityLevel.isNotEmpty())
    }
}

// PASSO 3: OBJETIVO DE PESO
@Composable
fun StepWeightGoal(
    uiState: UserEntity,
    onGoalSelected: (String) -> Unit,
    onCalculate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(stringResource(com.example.baselift.R.string.onboarding_weight_goal), style = Typography.headlineLarge.copy(color = CrystalWhite))
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(com.example.baselift.R.string.onboarding_weight_goal_desc), style = Typography.bodyLarge.copy(color = MediumGrey))
        Spacer(modifier = Modifier.height(16.dp))

        data class GoalItem(val title: String, val desc: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)
        val goals = listOf(
            GoalItem("Extreme Loss", stringResource(com.example.baselift.R.string.onboarding_extreme_loss_desc), Icons.Default.TrendingDown, ElectricBlue),
            GoalItem("Weight Loss", stringResource(com.example.baselift.R.string.onboarding_weight_loss_desc), Icons.Default.TrendingDown, ElectricBlue),
            GoalItem("Mild Weight Loss", stringResource(com.example.baselift.R.string.onboarding_mild_weight_loss_desc), Icons.Default.TrendingDown, ElectricBlue),
            GoalItem("Maintenance", stringResource(com.example.baselift.R.string.onboarding_maintenance_desc), Icons.Default.TrendingFlat, CrystalWhite),
            GoalItem("Mild Weight Gain", stringResource(com.example.baselift.R.string.onboarding_mild_weight_gain_desc), Icons.Default.TrendingUp, NeonGreen),
            GoalItem("Weight Gain", stringResource(com.example.baselift.R.string.onboarding_weight_gain_desc), Icons.Default.TrendingUp, NeonGreen),
            GoalItem("Extreme Gain", stringResource(com.example.baselift.R.string.onboarding_extreme_gain_desc), Icons.Default.TrendingUp, NeonGreen)
        )

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            goals.forEach { item ->
                SelectionCard(
                    title = item.title,
                    description = item.desc,
                    isSelected = uiState.goal == item.title,
                    onClick = { onGoalSelected(item.title) },
                    icon = item.icon,
                    iconTint = item.color,
                    verticalPadding = 8.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        NeonButton(text = stringResource(com.example.baselift.R.string.onboarding_calculate_targets), onClick = onCalculate, enabled = uiState.goal.isNotEmpty())
    }
}

// PASSO 4: ALVOS CALCULADOS
@Composable
fun StepCalculatedTargets(
    uiState: UserEntity,
    isRecalibrating: Boolean,
    onGetStarted: () -> Unit,
    onCustomTargets: () -> Unit,
    onRefreshTargets: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (uiState.isCustomTargets) stringResource(com.example.baselift.R.string.onboarding_your_custom_targets) else stringResource(com.example.baselift.R.string.onboarding_your_calculated_targets),
                    style = Typography.headlineLarge.copy(color = CrystalWhite)
                )
                if (uiState.isCustomTargets) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh to Baseline",
                        tint = NeonGreen,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onRefreshTargets() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(com.example.baselift.R.string.onboarding_based_on_profile), style = Typography.bodyLarge.copy(color = MediumGrey))
            Spacer(modifier = Modifier.height(16.dp))

        // mostrar calorias
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(com.example.baselift.R.string.onboarding_daily_maintenance), color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${uiState.dailyCaloriesGoal}",
                        style = Typography.displayLarge.copy(color = NeonGreen, fontSize = 64.sp)
                    )
                    Text("kcal", color = MediumGrey, fontSize = 24.sp, modifier = Modifier.padding(bottom = 12.dp, start = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // mostrar macronutrientes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(16.dp)
        ) {
            MacroRow(stringResource(com.example.baselift.R.string.onboarding_protein), uiState.proteinGoal, stringResource(com.example.baselift.R.string.onboarding_protein_desc), ElectricBlue, 0.3f)
            HorizontalDivider(color = MediumGrey.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
            MacroRow(stringResource(com.example.baselift.R.string.onboarding_carbs), uiState.carbsGoal, stringResource(com.example.baselift.R.string.onboarding_carbs_desc), NeonGreen, 0.45f)
            HorizontalDivider(color = MediumGrey.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
            MacroRow(stringResource(com.example.baselift.R.string.onboarding_fats), uiState.fatGoal, stringResource(com.example.baselift.R.string.onboarding_fats_desc), SoftCoral, 0.25f)
        }

        }

        Spacer(modifier = Modifier.height(16.dp))

        NeonButton(
            text = if (isRecalibrating) stringResource(com.example.baselift.R.string.onboarding_update_baseline) else stringResource(com.example.baselift.R.string.onboarding_get_started),
            onClick = onGetStarted
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(com.example.baselift.R.string.onboarding_custom_plan_msg),
            color = ElectricBlue,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable { onCustomTargets() },
            textAlign = TextAlign.Center
        )
    }
}

// COMPONENTES REUTILIZÁVEIS

@Composable
fun GenderButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) NeonGreen else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) CrystalWhite else MediumGrey)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text, color = if (isSelected) CrystalWhite else MediumGrey, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GlassTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface,
            focusedBorderColor = NeonGreen,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = CrystalWhite,
            unfocusedTextColor = CrystalWhite
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        placeholder = { Text(placeholder, color = MediumGrey.copy(alpha = 0.5f)) },
        singleLine = true
    )
}

@Composable
fun UnitToggle(option1: String, option2: String, selectedOption: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (selectedOption == option1) DeepCharcoal else Color.Transparent)
                .clickable { onSelect(option1) }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) { Text(option1, color = if (selectedOption == option1) CrystalWhite else MediumGrey, fontSize = 12.sp) }
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (selectedOption == option2) DeepCharcoal else Color.Transparent)
                .clickable { onSelect(option2) }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) { Text(option2, color = if (selectedOption == option2) CrystalWhite else MediumGrey, fontSize = 12.sp) }
    }
}

@Composable
fun SelectionCard(title: String, description: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, iconTint: Color? = null, verticalPadding: androidx.compose.ui.unit.Dp = 10.dp) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) NeonGreen else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // indicador de seleção
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, if (isSelected) NeonGreen else MediumGrey, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(NeonGreen))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, color = if (isSelected) NeonGreen else CrystalWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = MediumGrey, fontSize = 11.sp, lineHeight = 14.sp)
        }
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) NeonGreen else (iconTint ?: MediumGrey), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun MacroRow(name: String, amount: Int, percentage: String, color: Color, fraction: Float) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(name, color = CrystalWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(percentage, color = color, fontSize = 12.sp)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(100.dp).height(4.dp).background(DeepCharcoal)) {
                Box(modifier = Modifier.fillMaxWidth(fraction).height(4.dp).background(color))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("${amount}g", color = CrystalWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
fun NeonButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonGreen,
            contentColor = PureBlack,
            disabledContainerColor = DarkSurface,
            disabledContentColor = MediumGrey
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
