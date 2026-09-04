package com.example.baselift.View.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius as GeoCornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.baselift.View.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun InteractiveChartWithControls(
    title: String,
    dataPoints: List<ChartDataPoint>,
    targetValue: Float?,
    onSetTargetValue: (Float?) -> Unit,
    lineColor: Color = NeonGreen,
    targetLineColor: Color = ElectricBlue,
    yUnit: String = "KG",
    formatYLabel: (Float) -> String = { String.format(Locale.US, "%.1f", it) },
    formatXLabel: (Long) -> String = { SimpleDateFormat("MMM dd", Locale.US).format(Date(it)) },
    showTimeFilters: Boolean = true,
    showTargetControls: Boolean = true,
    chartHeight: androidx.compose.ui.unit.Dp = 250.dp
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    val filters = listOf("7D", "30D", "1Y", "ALL")
    
    var showGoalDialog by remember { mutableStateOf(false) }
    var goalInput by remember { mutableStateOf(targetValue?.toString() ?: "") }
    var isChronologicalScale by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTimeFilters) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = CrystalWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEach { filter ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selectedFilter == filter) lineColor else DarkSurface)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
        ) {
            val now = System.currentTimeMillis()
            val filteredPoints = if (showTimeFilters) {
                when (selectedFilter) {
                    "7D" -> dataPoints.filter { now - it.xValue <= 7L * 24 * 60 * 60 * 1000 }
                    "30D" -> dataPoints.filter { now - it.xValue <= 30L * 24 * 60 * 60 * 1000 }
                    "1Y" -> dataPoints.filter { now - it.xValue <= 365L * 24 * 60 * 60 * 1000 }
                    else -> dataPoints
                }
            } else {
                dataPoints
            }
            val rawPoints = if (filteredPoints.isEmpty()) dataPoints.takeLast(1) else filteredPoints
            val validPoints = downsampleLTTB(rawPoints, 20)

            CustomCanvasChart(
                validPoints = validPoints,
                targetValue = targetValue,
                lineColor = lineColor,
                targetLineColor = targetLineColor,
                isChronologicalScale = isChronologicalScale,
                formatYLabel = formatYLabel,
                formatXLabel = formatXLabel
            )
            
            if (validPoints.isNotEmpty()) {
                val latestPoint = validPoints.last()
                val previousPoint = if (validPoints.size > 1) validPoints[validPoints.size - 2] else latestPoint
                val delta = latestPoint.yValue - previousPoint.yValue
                val sign = if (delta > 0) "+" else ""
                
                Column(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    Text("LATEST", color = CrystalWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    Text("${sign}${formatYLabel(delta)} $yUnit", color = lineColor, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // botões de adicionar/remover objetivo e toggle de escala
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            // Toggle scale button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (!isChronologicalScale) lineColor else Color.Transparent)
                    .border(1.dp, lineColor, RoundedCornerShape(6.dp))
                    .clickable { isChronologicalScale = !isChronologicalScale }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LinearScale, 
                    contentDescription = "Toggle Scale", 
                    tint = if (!isChronologicalScale) PureBlack else lineColor, 
                    modifier = Modifier.size(12.dp)
                )
            }
            
            if (showTargetControls) {
                Spacer(modifier = Modifier.width(12.dp))
                
                if (targetValue != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, SoftCoral, RoundedCornerShape(6.dp))
                            .clickable { onSetTargetValue(null) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove Goal", tint = SoftCoral, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("REMOVE GOAL", color = SoftCoral, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElectricBlue.copy(alpha = 0.2f))
                            .clickable { showGoalDialog = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("ADD GOAL +", color = ElectricBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showGoalDialog) {
        Dialog(onDismissRequest = { showGoalDialog = false }) {
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
                Text("Set Target Goal", color = lineColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it.replace("\n", "").replace("\r", "") },
                    label = { Text("Target ($yUnit)", color = MediumGrey) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = lineColor,
                        unfocusedBorderColor = PureBlack,
                        focusedContainerColor = PureBlack,
                        unfocusedContainerColor = PureBlack,
                        focusedTextColor = CrystalWhite,
                        unfocusedTextColor = CrystalWhite
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showGoalDialog = false }) {
                        Text("CANCEL", color = MediumGrey)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val v = goalInput.toFloatOrNull()
                            if (v != null && v > 0) {
                                onSetTargetValue(v)
                            }
                            showGoalDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = lineColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SAVE", color = PureBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomCanvasChart(
    validPoints: List<ChartDataPoint>,
    targetValue: Float?,
    lineColor: Color,
    targetLineColor: Color,
    isChronologicalScale: Boolean,
    formatYLabel: (Float) -> String,
    formatXLabel: (Long) -> String
) {
    if (validPoints.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data available yet.", color = MediumGrey)
        }
        return
    }

    val minY = validPoints.minOf { it.yValue }
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
    
    val targetPaint = remember(targetLineColor) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor(String.format("#%06X", (0xFFFFFF and targetLineColor.toArgb())))
            textSize = 24f
            isAntiAlias = true
        }
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var selectedOffset by remember { mutableStateOf(Offset.Zero) }

    Spacer(modifier = Modifier
        .fillMaxSize()
        .padding(top = 16.dp, bottom = 16.dp, end = 24.dp, start = 8.dp)
        .pointerInput(validPoints, isChronologicalScale) {
            detectTapGestures { tapOffset ->
                val width = size.width
                val height = size.height
                
                val leftPadding = 80f
                val bottomPadding = 40f
                val topPadding = 40f
                
                val chartWidth = width - leftPadding
                val chartHeight = height - bottomPadding
                
                val yPadding = if (maxY == minY) 5f else (maxY - minY) * 0.2f
                var yMin = (minY - yPadding).coerceAtLeast(0f)
                var yMax = maxY + yPadding
                
                if (targetValue != null) {
                    if (targetValue < yMin) yMin = (targetValue - yPadding).coerceAtLeast(0f)
                    if (targetValue > yMax) yMax = targetValue + yPadding
                }
                
                val yRange = yMax - yMin
                
                val minTime = validPoints.first().xValue
                val maxTime = validPoints.last().xValue
                val timeRange = maxTime - minTime
                
                var closestIndex: Int? = null
                var minDistance = Float.MAX_VALUE
                var closestOffset = Offset.Zero
                
                validPoints.forEachIndexed { index, log ->
                    val x = if (isChronologicalScale) {
                        if (timeRange == 0L) leftPadding + chartWidth / 2f
                        else leftPadding + ((log.xValue - minTime).toFloat() / timeRange.toFloat()) * chartWidth
                    } else {
                        val stepX = if (validPoints.size > 1) chartWidth / (validPoints.size - 1) else chartWidth / 2f
                        leftPadding + (index * stepX)
                    }
                    val y = topPadding + (chartHeight - topPadding) - ((log.yValue - yMin) / yRange) * (chartHeight - topPadding)
                    
                    val dist = kotlin.math.sqrt((tapOffset.x - x)*(tapOffset.x - x) + (tapOffset.y - y)*(tapOffset.y - y))
                    if (dist < 60f && dist < minDistance) { 
                        minDistance = dist
                        closestIndex = index
                        closestOffset = Offset(x, y)
                    }
                }
                
                if (closestIndex != null) {
                    selectedIndex = closestIndex
                    selectedOffset = closestOffset
                } else {
                    selectedIndex = null
                }
            }
        }
        .drawWithCache {
            val width = size.width
            val height = size.height
            
            val leftPadding = 80f
            val bottomPadding = 40f
            val topPadding = 40f
            
            val chartWidth = width - leftPadding
            val chartHeight = height - bottomPadding
            
            val yPadding = if (maxY == minY) 5f else (maxY - minY) * 0.2f
            var yMin = (minY - yPadding).coerceAtLeast(0f)
            var yMax = maxY + yPadding
            
            if (targetValue != null) {
                if (targetValue < yMin) yMin = (targetValue - yPadding).coerceAtLeast(0f)
                if (targetValue > yMax) yMax = targetValue + yPadding
            }
            
            val yRange = yMax - yMin
            val drawHeight = chartHeight - topPadding
            
            val path = Path()
            val points = mutableListOf<Offset>()
            
            val minTime = validPoints.first().xValue
            val maxTime = validPoints.last().xValue
            val timeRange = maxTime - minTime
            
            validPoints.forEachIndexed { index, log ->
                val x = if (isChronologicalScale) {
                    if (timeRange == 0L) leftPadding + chartWidth / 2f
                    else leftPadding + ((log.xValue - minTime).toFloat() / timeRange.toFloat()) * chartWidth
                } else {
                    val stepX = if (validPoints.size > 1) chartWidth / (validPoints.size - 1) else chartWidth / 2f
                    leftPadding + (index * stepX)
                }
                
                val y = topPadding + drawHeight - ((log.yValue - yMin) / yRange) * drawHeight
                points.add(Offset(x, y))
                
                if (index == 0) path.moveTo(x, y)
                else path.lineTo(x, y)
            }
            
            val fillPath = if (points.isNotEmpty()) {
                Path().apply {
                    addPath(path)
                    lineTo(points.last().x, chartHeight)
                    lineTo(points.first().x, chartHeight)
                    close()
                }
            } else null
            
            val gradientBrush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.6f), Color.Transparent),
                startY = topPadding,
                endY = chartHeight
            )
            
            onDrawBehind {
                drawLine(color = MediumGrey, start = Offset(leftPadding, topPadding), end = Offset(leftPadding, chartHeight), strokeWidth = 2f)
                drawLine(color = MediumGrey, start = Offset(leftPadding, chartHeight), end = Offset(width, chartHeight), strokeWidth = 2f)
                
                if (targetValue != null && targetValue in yMin..yMax) {
                    val yPos = topPadding + drawHeight - ((targetValue - yMin) / yRange) * drawHeight
                    drawLine(
                        color = targetLineColor,
                        start = Offset(leftPadding, yPos),
                        end = Offset(width, yPos),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                    )
                    drawContext.canvas.nativeCanvas.drawText(formatYLabel(targetValue), leftPadding + 10f, yPos - 10f, targetPaint)
                }
                
                if (fillPath != null) {
                    drawPath(path = fillPath, brush = gradientBrush)
                }
                
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 6f)
                )
                
                points.forEach { pt ->
                    drawCircle(color = PureBlack, radius = 10f, center = pt)
                    drawCircle(color = lineColor, radius = 10f, center = pt, style = Stroke(width = 4f))
                }
                
                drawContext.canvas.nativeCanvas.drawText(formatYLabel(yMax), 0f, topPadding + 15f, textPaint)
                val yMid = (yMax + yMin) / 2
                val yMidPos = topPadding + drawHeight / 2
                drawContext.canvas.nativeCanvas.drawText(formatYLabel(yMid), 0f, yMidPos + 10f, textPaint)
                drawContext.canvas.nativeCanvas.drawText(formatYLabel(yMin), 0f, chartHeight, textPaint)
                
                if (validPoints.isNotEmpty()) {
                    val startText = formatXLabel(validPoints.first().xValue)
                    drawContext.canvas.nativeCanvas.drawText(startText, leftPadding, 25f, dateTextPaint.apply { textAlign = android.graphics.Paint.Align.LEFT })
                    
                    if (validPoints.size > 2) {
                        val midIndex = validPoints.size / 2
                        val midText = formatXLabel(validPoints[midIndex].xValue)
                        val midX = leftPadding + (chartWidth / 2f)
                        drawContext.canvas.nativeCanvas.drawText(midText, midX, 25f, dateTextPaint.apply { textAlign = android.graphics.Paint.Align.CENTER })
                    }
                    
                    if (validPoints.size > 1) {
                        val endText = formatXLabel(validPoints.last().xValue)
                        drawContext.canvas.nativeCanvas.drawText(endText, width, 25f, dateTextPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT })
                    }
                }
                
                selectedIndex?.let { index ->
                    val log = validPoints[index]
                    val textStr = log.tooltipLabel
                    
                    val tooltipPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 32f
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
                    if (tooltipX - tooltipWidth/2 < 0) tooltipX = tooltipWidth/2
                    if (tooltipX + tooltipWidth/2 > width) tooltipX = width - tooltipWidth/2
                    
                    var tooltipY = selectedOffset.y - tooltipHeight - 30f
                    if (tooltipY < 0f) tooltipY = selectedOffset.y + 30f
                    
                    drawRoundRect(
                        color = PureBlack.copy(alpha = 0.9f),
                        topLeft = Offset(tooltipX - tooltipWidth/2, tooltipY),
                        size = Size(tooltipWidth, tooltipHeight),
                        cornerRadius = GeoCornerRadius(12f, 12f)
                    )
                    drawRoundRect(
                        color = lineColor.copy(alpha = 0.8f),
                        topLeft = Offset(tooltipX - tooltipWidth/2, tooltipY),
                        size = Size(tooltipWidth, tooltipHeight),
                        cornerRadius = GeoCornerRadius(12f, 12f),
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
    )
}

fun downsampleLTTB(data: List<ChartDataPoint>, threshold: Int): List<ChartDataPoint> {
    if (threshold >= data.size || threshold == 0) return data

    val sampled = ArrayList<ChartDataPoint>(threshold)
    sampled.add(data.first()) // Always add the first point

    val bucketSize = (data.size - 2).toDouble() / (threshold - 2)
    var a = 0 // Initially a is the first point in the triangle

    for (i in 0 until threshold - 2) {
        var avgX = 0.0
        var avgY = 0.0
        var avgRangeStart = (kotlin.math.floor((i + 1) * bucketSize) + 1).toInt()
        var avgRangeEnd = (kotlin.math.floor((i + 2) * bucketSize) + 1).toInt()
        
        avgRangeStart = avgRangeStart.coerceAtMost(data.size - 1)
        avgRangeEnd = avgRangeEnd.coerceAtMost(data.size)
        
        val avgRangeLength = avgRangeEnd - avgRangeStart
        
        if (avgRangeLength > 0) {
            for (j in avgRangeStart until avgRangeEnd) {
                avgX += data[j].xValue.toDouble()
                avgY += data[j].yValue.toDouble()
            }
            avgX /= avgRangeLength
            avgY /= avgRangeLength
        } else {
            avgX = data.last().xValue.toDouble()
            avgY = data.last().yValue.toDouble()
        }

        val rangeStart = (kotlin.math.floor(i * bucketSize) + 1).toInt()
        val rangeEnd = (kotlin.math.floor((i + 1) * bucketSize) + 1).toInt()
        
        val safeRangeStart = rangeStart.coerceAtMost(data.size - 1)
        val safeRangeEnd = rangeEnd.coerceAtMost(data.size)

        var maxArea = -1.0
        var maxAreaIndex = safeRangeStart

        val pointAx = data[a].xValue.toDouble()
        val pointAy = data[a].yValue.toDouble()

        for (j in safeRangeStart until safeRangeEnd) {
            val area = kotlin.math.abs(
                (pointAx - avgX) * (data[j].yValue.toDouble() - pointAy) -
                (pointAx - data[j].xValue.toDouble()) * (avgY - pointAy)
            ) * 0.5
            
            if (area > maxArea) {
                maxArea = area
                maxAreaIndex = j
            }
        }
        
        sampled.add(data[maxAreaIndex])
        a = maxAreaIndex
    }

    sampled.add(data.last())
    return sampled
}
