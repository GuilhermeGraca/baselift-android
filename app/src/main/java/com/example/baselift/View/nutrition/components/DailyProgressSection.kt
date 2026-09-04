package com.example.baselift.View.nutrition.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baselift.View.theme.DarkElectricBlue
import com.example.baselift.View.theme.DarkSunYellow
import com.example.baselift.View.theme.DarkVibrantPurple
import com.example.baselift.View.theme.ElectricBlue
import com.example.baselift.View.theme.MediumGrey
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.SoftCoral
import com.example.baselift.View.theme.SunYellow
import com.example.baselift.View.theme.VibrantPurple

// barra de progresso circular e barras horizontais para os totais do dia
@Composable
fun DailyProgressSection(
    targetCalories: Int,
    targetProtein: Int,
    targetCarbs: Int,
    targetFats: Int,
    consumedCalories: Int,
    consumedProtein: Int,
    consumedCarbs: Int,
    consumedFats: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val remainingCalories = maxOf(0, targetCalories - consumedCalories)
        val progressPercent = if (targetCalories > 0) {
            (consumedCalories.toFloat() / targetCalories.toFloat()).coerceIn(0f, 1f)
        } else 0f

        // gráfico circular de calorias
        Box(
            modifier = Modifier
                .size(236.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)
                
                // fundo da barra
                drawArc(
                    color = Color(0xFF2A2A2A),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )

                // progresso da barra
                val circleColor = if (remainingCalories <= 0) SoftCoral else NeonGreen
                drawArc(
                    color = circleColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progressPercent,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    text = remainingCalories.toString(),
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                androidx.compose.material3.Text(
                    text = stringResource(com.example.baselift.R.string.nutrition_remaining_kcal, targetCalories),
                    color = MediumGrey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // calorias consumidas vs objetivo (barra horizontal)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    androidx.compose.material3.Text(stringResource(com.example.baselift.R.string.nutrition_consumed), color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        androidx.compose.material3.Text(consumedCalories.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Text(stringResource(com.example.baselift.R.string.nutrition_kcal), color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    androidx.compose.material3.Text(stringResource(com.example.baselift.R.string.nutrition_goal), color = MediumGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        androidx.compose.material3.Text(targetCalories.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Text(stringResource(com.example.baselift.R.string.nutrition_kcal), color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                val maxVal = maxOf(targetCalories, consumedCalories)
                val scaleMax = if (maxVal > 0) maxVal / 0.95f else 1f
                val targetFraction = if (scaleMax > 0) targetCalories / scaleMax else 0f
                val consumedFraction = if (scaleMax > 0) consumedCalories / scaleMax else 0f
                
                // background track
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.CenterStart).background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp)))
                
                val overLimit = consumedCalories > targetCalories
                
                if (consumedFraction > 0) {
                    Row(modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(consumedFraction).height(8.dp)) {
                        if (overLimit && targetFraction > 0) {
                            Box(modifier = Modifier.weight(targetFraction / consumedFraction).fillMaxHeight().background(NeonGreen, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                            Box(modifier = Modifier.weight(1f - (targetFraction / consumedFraction)).fillMaxHeight().background(SoftCoral, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(NeonGreen, RoundedCornerShape(4.dp)))
                        }
                    }
                }
                
                // target marker
                if (targetFraction > 0) {
                    Box(
                        modifier = Modifier.fillMaxWidth(targetFraction).align(Alignment.CenterStart)
                    ) {
                        Box(
                            modifier = Modifier.width(4.dp).height(16.dp).background(MediumGrey, RoundedCornerShape(2.dp)).align(Alignment.CenterEnd)
                        )
                    }
                }
            }
            
            if (consumedCalories > targetCalories) {
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = stringResource(com.example.baselift.R.string.nutrition_over_limit, consumedCalories - targetCalories),
                    color = SoftCoral,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // barras de macros
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1B1B), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                MacroBar(
                    label = stringResource(com.example.baselift.R.string.nutrition_protein),
                    consumed = consumedProtein,
                    target = targetProtein,
                    color = SunYellow,
                    overColor = DarkSunYellow
                )
                MacroBar(
                    label = stringResource(com.example.baselift.R.string.nutrition_carbs),
                    consumed = consumedCarbs,
                    target = targetCarbs,
                    color = ElectricBlue,
                    overColor = DarkElectricBlue
                )
                MacroBar(
                    label = stringResource(com.example.baselift.R.string.nutrition_fats),
                    consumed = consumedFats,
                    target = targetFats,
                    color = VibrantPurple,
                    overColor = DarkVibrantPurple
                )
            }
        }
    }
}

@Composable
fun MacroBar(
    label: String,
    consumed: Int,
    target: Int,
    color: Color,
    overColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            androidx.compose.material3.Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                androidx.compose.material3.Text(text = consumed.toString(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                androidx.compose.material3.Text(text = stringResource(com.example.baselift.R.string.nutrition_grams, target), color = MediumGrey, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val maxVal = maxOf(target, consumed)
            val scaleMax = if (maxVal > 0) maxVal / 0.95f else 1f
            val targetFraction = if (scaleMax > 0) target.toFloat() / scaleMax else 0f
            val consumedFraction = if (scaleMax > 0) consumed.toFloat() / scaleMax else 0f
            
            // background track
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.CenterStart).background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp)))
            
            val overLimit = consumed > target
            
            // filled track
            if (consumedFraction > 0) {
                Row(modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(consumedFraction).height(8.dp)) {
                    if (overLimit && targetFraction > 0) {
                        Box(modifier = Modifier.weight(targetFraction / consumedFraction).fillMaxHeight().background(color, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                        Box(modifier = Modifier.weight(1f - (targetFraction / consumedFraction)).fillMaxHeight().background(overColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(4.dp)))
                    }
                }
            }
            
            // target marker
            if (targetFraction > 0) {
                Box(
                    modifier = Modifier.fillMaxWidth(targetFraction).align(Alignment.CenterStart)
                ) {
                    Box(
                        modifier = Modifier.width(4.dp).height(16.dp).background(MediumGrey, RoundedCornerShape(2.dp)).align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}
