package com.example.baselift.View.insights.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.baselift.Utils.IdealWeightCalculator
import com.example.baselift.View.theme.CrystalWhite
import com.example.baselift.View.theme.MediumGrey
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.PureBlack

@Composable
fun IdealWeightDialog(
    heightCm: Float,
    gender: String,
    onDismiss: () -> Unit
) {
    val results = IdealWeightCalculator.calculateIdealWeights(heightCm, gender)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PureBlack)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(com.example.baselift.R.string.insights_ideal_body_weight),
                    color = NeonGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MediumGrey,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onDismiss() }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(com.example.baselift.R.string.insights_ideal_weight_desc),
                color = MediumGrey,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(com.example.baselift.R.string.insights_formula), color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(com.example.baselift.R.string.insights_ideal_weight), color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            
            // Rows
            results.forEach { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.formulaName,
                        color = MediumGrey,
                        fontSize = 14.sp
                    )
                    
                    if (result.range != null) {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", result.range.first)} - ${String.format(java.util.Locale.US, "%.1f", result.range.second)} KG",
                            color = NeonGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", result.weightKg)} KG",
                            color = NeonGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (result != results.last()) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(com.example.baselift.R.string.insights_ideal_weight_note),
                color = MediumGrey.copy(alpha = 0.7f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
