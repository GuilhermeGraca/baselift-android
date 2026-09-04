package com.example.baselift.View.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Sync
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.baselift.View.nutrition.components.DailyProgressSection
import com.example.baselift.View.nutrition.components.QuickLogSection
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.PureBlack
import androidx.compose.ui.res.stringResource
import com.example.baselift.ViewModel.nutrition.NutritionViewModel

// ecrã principal de nutrição
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel,
    onNavigateToCustomTargets: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(PureBlack), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NeonGreen)
        }
        return
    }

    var showConfigureMealDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showConfigureMealDialog) {
        com.example.baselift.View.nutrition.components.ConfigureMealDialog(
            onDismiss = { showConfigureMealDialog = false },
            onSave = { template -> viewModel.saveMealTemplate(template) }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // secção de progresso (barras e gráfico circular)
            DailyProgressSection(
                targetCalories = uiState.targetCalories,
                targetProtein = uiState.targetProtein,
                targetCarbs = uiState.targetCarbs,
                targetFats = uiState.targetFats,
                consumedCalories = uiState.consumedCalories,
                consumedProtein = uiState.consumedProtein,
                consumedCarbs = uiState.consumedCarbs,
                consumedFats = uiState.consumedFats
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            // botão set custom target
            androidx.compose.material3.OutlinedButton(
                onClick = onNavigateToCustomTargets,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF333333))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Sync, contentDescription = "Set Custom Target", modifier = Modifier.size(16.dp), tint = com.example.baselift.View.theme.MediumGrey)
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Text(stringResource(com.example.baselift.R.string.nutrition_set_new_target), fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            // secção de registo rápido
            QuickLogSection(
                onAddEntry = { kcal, p, c, f, isCaloriesOnly ->
                    viewModel.addQuickLog(kcal, p, c, f, isCaloriesOnly)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            // quick add meals
            com.example.baselift.View.nutrition.components.QuickAddMealsSection(
                templates = uiState.mealTemplates,
                onLogMeal = { template -> viewModel.logMealTemplate(template) },
                onDeleteTemplate = { template -> viewModel.deleteMealTemplate(template) },
                onConfigureClick = { showConfigureMealDialog = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            // tabela de logs de hoje
            com.example.baselift.View.nutrition.components.TodaysLogsSection(
                logs = uiState.todayLogs,
                onDeleteLog = { log -> viewModel.deleteLog(log) },
                onResetAll = { viewModel.resetTodayLogs() }
            )

            // espaço extra para não ficar colado à barra de navegação
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
