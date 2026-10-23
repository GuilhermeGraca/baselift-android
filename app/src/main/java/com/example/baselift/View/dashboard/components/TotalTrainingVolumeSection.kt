package com.example.baselift.View.dashboard.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.baselift.View.components.BarChartWithControls
import com.example.baselift.View.components.ChartDataPoint
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.SoftCoral
import com.example.baselift.ViewModel.dashboard.WeeklyVolume
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun TotalTrainingVolumeSection(weeklyVolumes: List<WeeklyVolume>) {
    // converter WeeklyVolume para ChartDataPoint
    val zone = TimeZone.getDefault()
    val dataPoints = remember(weeklyVolumes, zone.id) {
        val numberFormat = NumberFormat.getNumberInstance(Locale.US)
        val dateFormat = SimpleDateFormat("MMM dd", Locale.US).apply { timeZone = zone }
        weeklyVolumes.map {
            ChartDataPoint(
                xValue = it.weekStartTimestamp,
                yValue = it.totalVolume,
                tooltipLabel = "${numberFormat.format(it.totalVolume)} kg\nWeek of ${dateFormat.format(Date(it.weekStartTimestamp))}"
            )
        }
    }

    // calcular a variação em percentagem entre a última e a penúltima semana
    var subtitle: String? = null
    var subtitleColor = NeonGreen

    if (weeklyVolumes.size >= 2) {
        val currentWeek = weeklyVolumes.last()
        val previousWeek = weeklyVolumes[weeklyVolumes.size - 2]
        
        if (previousWeek.totalVolume > 0f) {
            val delta = currentWeek.totalVolume - previousWeek.totalVolume
            val percentChange = (delta / previousWeek.totalVolume) * 100
            
            val sign = if (percentChange >= 0) "+" else ""
            subtitle = stringResource(com.example.baselift.R.string.dash_vs_last_period, "$sign${String.format(Locale.US, "%.1f", percentChange)}%")
            subtitleColor = if (percentChange >= 0) NeonGreen else SoftCoral
        }
    }

    // calcular volume total da lista completa por defeito
    val summaryValueStr = remember(weeklyVolumes) {
        val totalVolume = weeklyVolumes.sumOf { it.totalVolume.toDouble() }.toFloat()
        NumberFormat.getNumberInstance(Locale.US).format(totalVolume)
    }

    BarChartWithControls(
        title = stringResource(com.example.baselift.R.string.dash_total_volume),
        dataPoints = dataPoints,
        barColor = NeonGreen,
        yUnit = "kg",
        formatYLabel = { String.format(Locale.US, "%.0f", it) },
        formatXLabel = { SimpleDateFormat("MMM dd", Locale.US).format(Date(it)) },
        summaryValue = summaryValueStr,
        summarySubtitle = subtitle,
        summarySubtitleColor = subtitleColor
    )

    Spacer(modifier = Modifier.height(32.dp))
}
