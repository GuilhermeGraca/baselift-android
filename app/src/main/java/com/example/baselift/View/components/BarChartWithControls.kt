package com.example.baselift.View.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baselift.View.theme.CrystalWhite
import com.example.baselift.View.theme.DarkSurface
import com.example.baselift.View.theme.MediumGrey
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.PureBlack

@Composable
fun BarChartWithControls(
    title: String,
    dataPoints: List<ChartDataPoint>,
    barColor: Color = NeonGreen,
    yUnit: String = "kg",
    formatYLabel: (Float) -> String,
    formatXLabel: (Long) -> String,
    summaryValue: String?,
    summarySubtitle: String?,
    summarySubtitleColor: Color = CrystalWhite,
    showTimeFilters: Boolean = true,
    chartHeight: androidx.compose.ui.unit.Dp = 250.dp
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    val filters = listOf("1M", "3M", "1Y", "ALL")

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTimeFilters) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = CrystalWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEach { filter ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selectedFilter == filter) barColor else DarkSurface)
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                filter,
                                color = if (selectedFilter == filter) PureBlack else CrystalWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else if (title.isNotEmpty()) {
            Text(title, color = CrystalWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // caixa do gráfico
        var selectedIndex by remember { mutableStateOf<Int?>(null) }
        var selectedOffset by remember { mutableStateOf(Offset.Zero) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .pointerInput(Unit) {
                    detectTapGestures { selectedIndex = null }
                }
        ) {
            val now = System.currentTimeMillis()
            val filteredPoints = if (showTimeFilters) {
                when (selectedFilter) {
                    "1M" -> dataPoints.filter { now - it.xValue <= 30L * 24 * 60 * 60 * 1000 }
                    "3M" -> dataPoints.filter { now - it.xValue <= 90L * 24 * 60 * 60 * 1000 }
                    "1Y" -> dataPoints.filter { now - it.xValue <= 365L * 24 * 60 * 60 * 1000 }
                    else -> dataPoints
                }
            } else {
                dataPoints
            }
            
            val validPoints = aggregateBarData(filteredPoints, 35)

        // Resumo no topo (não sobreposto)
        if (summaryValue != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        summaryValue,
                        color = CrystalWhite,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        yUnit,
                        color = MediumGrey,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                
                if (summarySubtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isPositive = !summarySubtitle.startsWith("-")
                        val arrow = if (isPositive) "↑" else "↓"
                        Text(
                            text = arrow,
                            color = summarySubtitleColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            summarySubtitle,
                            color = summarySubtitleColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CustomCanvasBarChart(
                validPoints = validPoints,
                barColor = barColor,
                formatYLabel = formatYLabel,
                formatXLabel = formatXLabel,
                selectedIndex = selectedIndex,
                selectedOffset = selectedOffset,
                onSelectionChange = { idx: Int?, off: Offset -> 
                    selectedIndex = idx
                    selectedOffset = off
                }
            )
        }
    }
}
}

@Composable
fun CustomCanvasBarChart(
    validPoints: List<ChartDataPoint>,
    barColor: Color,
    formatYLabel: (Float) -> String,
    formatXLabel: (Long) -> String,
    selectedIndex: Int?,
    selectedOffset: Offset,
    onSelectionChange: (Int?, Offset) -> Unit
) {
    if (validPoints.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data available yet.", color = MediumGrey)
        }
        return
    }

    val minY = 0f
    val maxY = validPoints.maxOf { it.yValue }

    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 28f
            isAntiAlias = true
        }
    }

    val dateTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 26f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .padding(top = 16.dp, bottom = 16.dp, end = 24.dp, start = 8.dp)
        .pointerInput(validPoints) {
            detectTapGestures(
                onTap = { tapOffset ->
                    val width = size.width
                    val height = size.height

                    val leftPadding = 80f
                    val bottomPadding = 40f
                    val topPadding = 20f

                    val chartWidth = width - leftPadding
                    val chartHeight = height - bottomPadding

                    val yPadding = if (maxY == minY) 5f else (maxY - minY) * 0.1f
                    val yMax = maxY + yPadding
                    val yRange = yMax - minY

                    var closestIndex: Int? = null
                    var minDistance = Float.MAX_VALUE
                    var closestOffset = Offset.Zero

                    val barWidth = if (validPoints.isNotEmpty()) (chartWidth / validPoints.size) * 0.6f else 0f

                    validPoints.forEachIndexed { index, log ->
                        val stepX = if (validPoints.size > 1) chartWidth / validPoints.size else chartWidth / 2f
                        val x = leftPadding + (index * stepX) + (stepX / 2f)
                        val y = topPadding + (chartHeight - topPadding) - ((log.yValue - minY) / yRange) * (chartHeight - topPadding)

                        val dist = kotlin.math.abs(tapOffset.x - x)
                        if (dist < (barWidth * 1.5f) && tapOffset.y <= chartHeight && dist < minDistance) {
                            minDistance = dist
                            closestIndex = index
                            closestOffset = Offset(x, y)
                        }
                    }

                    onSelectionChange(closestIndex, closestOffset)
                }
            )
        }
    ) {
        val width = size.width
        val height = size.height

        val leftPadding = 80f
        val bottomPadding = 40f
        val topPadding = 20f

        val chartWidth = width - leftPadding
        val chartHeight = height - bottomPadding

        // Eixos
        drawLine(color = MediumGrey, start = Offset(leftPadding, topPadding), end = Offset(leftPadding, chartHeight), strokeWidth = 2f)
        drawLine(color = MediumGrey, start = Offset(leftPadding, chartHeight), end = Offset(width, chartHeight), strokeWidth = 2f)

        val yPadding = if (maxY == minY) 5f else (maxY - minY) * 0.1f
        val yMax = maxY + yPadding
        val yRange = yMax - minY
        val drawHeight = chartHeight - topPadding

        // Desenhar Barras
        val stepX = if (validPoints.size > 1) chartWidth / validPoints.size else chartWidth / 2f
        val barWidth = (stepX * 0.8f).coerceIn(10f, 100f)

        validPoints.forEachIndexed { index, log ->
            val xCenter = leftPadding + (index * stepX) + (stepX / 2f)
            val barHeight = ((log.yValue - minY) / yRange) * drawHeight
            val yTop = chartHeight - barHeight

            val isSelected = index == selectedIndex
            val alpha = if (isSelected || selectedIndex == null) 1f else 0.3f
            val baseColor = barColor.copy(alpha = alpha)
            val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(baseColor, baseColor.copy(alpha = 0.1f)),
                startY = yTop,
                endY = chartHeight
            )

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(xCenter - barWidth / 2f, yTop),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }

        // Desenhar Labels do Eixo Y
        drawContext.canvas.nativeCanvas.drawText(formatYLabel(yMax), 0f, topPadding + 15f, textPaint)
        val yMid = (yMax + minY) / 2
        val yMidPos = topPadding + drawHeight / 2
        drawContext.canvas.nativeCanvas.drawText(formatYLabel(yMid), 0f, yMidPos + 10f, textPaint)
        drawContext.canvas.nativeCanvas.drawText(formatYLabel(minY), 0f, chartHeight, textPaint)

        // Desenhar Labels do Eixo X
        if (validPoints.isNotEmpty()) {
            val startText = formatXLabel(validPoints.first().xValue)
            drawContext.canvas.nativeCanvas.drawText(startText, leftPadding, chartHeight + 30f, dateTextPaint.apply { textAlign = android.graphics.Paint.Align.LEFT })

            if (validPoints.size > 2) {
                val midIndex = validPoints.size / 2
                val midText = formatXLabel(validPoints[midIndex].xValue)
                val midX = leftPadding + (chartWidth / 2f)
                drawContext.canvas.nativeCanvas.drawText(midText, midX, chartHeight + 30f, dateTextPaint.apply { textAlign = android.graphics.Paint.Align.CENTER })
            }

            if (validPoints.size > 1) {
                val endText = formatXLabel(validPoints.last().xValue)
                drawContext.canvas.nativeCanvas.drawText(endText, width, chartHeight + 30f, dateTextPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT })
            }
        }

        // Desenhar Tooltip se selecionado
        selectedIndex?.let { index ->
            val log = validPoints[index]
            val textStr = log.tooltipLabel

            val tooltipPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 30f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val textBounds = android.graphics.Rect()
            
            val lines = textStr.split("\n")
            var maxWidth = 0f
            var totalTextHeight = 0f
            val lineHeights = mutableListOf<Float>()
            
            lines.forEach { line ->
                tooltipPaint.getTextBounds(line, 0, line.length, textBounds)
                val w = textBounds.width().toFloat()
                if (w > maxWidth) maxWidth = w
                val h = textBounds.height().toFloat()
                totalTextHeight += h
                lineHeights.add(h)
            }

            val lineSpacing = 16f
            val tooltipWidth = maxWidth + 48f
            val tooltipHeight = totalTextHeight + (lines.size - 1) * lineSpacing + 40f

            var tooltipX = selectedOffset.x
            if (tooltipX - tooltipWidth / 2 < 0) tooltipX = tooltipWidth / 2
            if (tooltipX + tooltipWidth / 2 > width) tooltipX = width - tooltipWidth / 2

            var tooltipY = selectedOffset.y - tooltipHeight - 30f
            if (tooltipY < 0f) tooltipY = selectedOffset.y + 30f

            drawRoundRect(
                color = PureBlack.copy(alpha = 0.9f),
                topLeft = Offset(tooltipX - tooltipWidth / 2, tooltipY),
                size = Size(tooltipWidth, tooltipHeight),
                cornerRadius = CornerRadius(12f, 12f)
            )
            drawRoundRect(
                color = barColor.copy(alpha = 0.8f),
                topLeft = Offset(tooltipX - tooltipWidth / 2, tooltipY),
                size = Size(tooltipWidth, tooltipHeight),
                cornerRadius = CornerRadius(12f, 12f),
                style = Stroke(width = 3f)
            )

            var currentY = tooltipY + 20f
            lines.forEachIndexed { i, line ->
                val h = lineHeights[i]
                drawContext.canvas.nativeCanvas.drawText(
                    line,
                    tooltipX,
                    currentY + h,
                    tooltipPaint
                )
                currentY += h + lineSpacing
            }
        }
    }
}

fun aggregateBarData(data: List<ChartDataPoint>, maxBars: Int): List<ChartDataPoint> {
    if (data.size <= maxBars || data.isEmpty()) return data
    
    val aggregated = mutableListOf<ChartDataPoint>()
    val bucketSize = data.size.toDouble() / maxBars
    
    for (i in 0 until maxBars) {
        val start = (i * bucketSize).toInt()
        val end = minOf(((i + 1) * bucketSize).toInt(), data.size)
        
        if (start < end) {
            val bucket = data.subList(start, end)
            val avgX = bucket.map { it.xValue }.average().toLong()
            val avgY = bucket.map { it.yValue }.average().toFloat()
            
            val startDate = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(java.util.Date(bucket.first().xValue))
            val endDate = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(java.util.Date(bucket.last().xValue))
            val tooltip = if (start == end - 1) bucket.first().tooltipLabel else "Avg: ${String.format(java.util.Locale.US, "%.1f", avgY)}\n$startDate - $endDate"
            
            aggregated.add(ChartDataPoint(avgX, avgY, tooltip))
        }
    }
    return aggregated
}
