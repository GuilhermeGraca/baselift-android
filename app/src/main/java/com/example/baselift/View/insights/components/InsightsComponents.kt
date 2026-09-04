package com.example.baselift.View.insights.components
import com.example.baselift.View.components.ChartDataPoint
import com.example.baselift.View.components.InteractiveChartWithControls

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.View.theme.*
import com.example.baselift.ViewModel.onboarding.OnboardingViewModel
import com.example.baselift.ViewModel.progress.ProgressViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable

fun WeightTrendSection(weightLogs: List<WeightLogEntity>, targetWeight: Float?, onSetTargetWeight: (Float?) -> Unit) {
    val dataPoints = weightLogs.map { log ->
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(log.timestamp))
        ChartDataPoint(
            xValue = log.timestamp,
            yValue = log.weightValue,
            tooltipLabel = "${log.weightValue} KG - $dateStr"
        )
    }

    InteractiveChartWithControls(
        title = stringResource(com.example.baselift.R.string.insights_weight_trend),
        dataPoints = dataPoints,
        targetValue = targetWeight,
        onSetTargetValue = onSetTargetWeight,
        lineColor = NeonGreen,
        targetLineColor = ElectricBlue,
        yUnit = "KG"
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWeightSection(onConfirmEntry: (Float, Long) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }
    
    var selectedTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var showCalendarDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
    ) {
        if (!isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = true }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = NeonGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(com.example.baselift.R.string.insights_log_weight), color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Expand", tint = MediumGrey)
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(com.example.baselift.R.string.insights_new_manual_entry), color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Add, contentDescription = "Collapse", tint = MediumGrey, modifier = Modifier.clickable { isExpanded = false })
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(stringResource(com.example.baselift.R.string.insights_weight_kg), color = MediumGrey, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it.replace("\n", "").replace("\r", "") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("00.0", color = MediumGrey) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = PureBlack,
                        focusedContainerColor = PureBlack,
                        unfocusedContainerColor = PureBlack,
                        focusedTextColor = CrystalWhite,
                        unfocusedTextColor = CrystalWhite
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(stringResource(com.example.baselift.R.string.insights_entry_date), color = MediumGrey, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = dateFormat.format(Date(selectedTimestamp)),
                    onValueChange = { },
                    readOnly = true,
                    trailingIcon = {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Select Date", tint = MediumGrey)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCalendarDialog = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = PureBlack,
                        disabledContainerColor = PureBlack,
                        disabledTextColor = CrystalWhite
                    ),
                    enabled = false
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val w = weightInput.toFloatOrNull()
                        if (w != null && w > 0) {
                            onConfirmEntry(w, selectedTimestamp)
                            isExpanded = false
                            weightInput = ""
                            selectedTimestamp = System.currentTimeMillis()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text(stringResource(com.example.baselift.R.string.insights_confirm_entry), color = PureBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCalendarDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCalendarDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureBlack)
                    .border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                com.example.baselift.View.components.CustomCalendar(
                    mode = com.example.baselift.View.components.CalendarMode.SELECT,
                    selectedDate = selectedTimestamp,
                    maxDate = System.currentTimeMillis(),
                    onDateSelected = { timestamp ->
                        selectedTimestamp = timestamp
                        showCalendarDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun TechnicalHistoryLedger(weightLogs: List<WeightLogEntity>, user: UserEntity?, onDelete: (WeightLogEntity) -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    val sortedLogs = weightLogs.sortedByDescending { it.timestamp }
    var showAllLogs by remember { mutableStateOf(false) }

    val isGainGoal = user?.goal?.contains("Gain", ignoreCase = true) ?: false

    var logToDelete by remember { mutableStateOf<WeightLogEntity?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(com.example.baselift.R.string.insights_technical_history), color = MediumGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(NeonGreen.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(stringResource(com.example.baselift.R.string.insights_raw_data_synced), color = NeonGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (sortedLogs.isEmpty()) {
            Text(stringResource(com.example.baselift.R.string.insights_no_entries), color = MediumGrey, fontSize = 12.sp)
        } else {
            val displayLogs = if (showAllLogs) sortedLogs else sortedLogs.take(4)
            
            displayLogs.forEachIndexed { index, log ->
                val previousLogIndex = sortedLogs.indexOf(log) + 1
                val previousLog = if (previousLogIndex < sortedLogs.size) sortedLogs[previousLogIndex] else null
                val delta = if (previousLog != null) log.weightValue - previousLog.weightValue else 0f
                val deltaText = if (delta > 0) "+${String.format(Locale.US, "%.1f", delta)}" else String.format(Locale.US, "%.1f", delta)
                
                val deltaColor = if (delta > 0) {
                    if (isGainGoal) NeonGreen else SoftCoral
                } else if (delta < 0) {
                    if (isGainGoal) SoftCoral else NeonGreen
                } else {
                    MediumGrey
                }
                
                val alphaValue = if (!showAllLogs && index == 3) 0.4f else 1f

                var isPressed by remember { mutableStateOf(false) }
                val animatedBgAlpha by animateFloatAsState(targetValue = if (isPressed) 0.3f else 0f, animationSpec = tween(600))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(alphaValue)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    SoftCoral.copy(alpha = animatedBgAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    val success = tryAwaitRelease()
                                    isPressed = false
                                },
                                onLongPress = {
                                    isPressed = false
                                    logToDelete = log
                                }
                            )
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(dateFormat.format(Date(log.timestamp)).uppercase(Locale.US), color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("ID: ${log.id}-${log.timestamp.toString().takeLast(4)}", color = MediumGrey, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${log.weightValue} KG", color = CrystalWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (previousLog != null) {
                            Text("$deltaText KG DELTA", color = deltaColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            if (sortedLogs.size > 4) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MediumGrey.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (showAllLogs) stringResource(com.example.baselift.R.string.insights_load_less_data) else stringResource(com.example.baselift.R.string.insights_load_more_data),
                    color = MediumGrey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { showAllLogs = !showAllLogs },
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (logToDelete != null) {
        Dialog(onDismissRequest = { logToDelete = null }) {
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
                Text(stringResource(com.example.baselift.R.string.insights_delete_entry), color = SoftCoral, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(com.example.baselift.R.string.insights_delete_weight_record_msg), color = CrystalWhite, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { logToDelete = null }) {
                        Text(stringResource(com.example.baselift.R.string.workout_cancel), color = MediumGrey)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            logToDelete?.let { onDelete(it) }
                            logToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SoftCoral),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(com.example.baselift.R.string.workout_delete), color = PureBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun VisualDiarySection(
    photoLogs: List<PhotoLogEntity>,
    weightLogs: List<WeightLogEntity>,
    onPhotoClick: (PhotoLogEntity) -> Unit,
    onAddPhoto: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    val sortedPhotos = remember(photoLogs) { photoLogs.sortedBy { it.timestamp } } // mais antigas à esquerda e recentes à direita
    val lazyListState = rememberLazyListState()

    // deslizar automaticamente para a última foto
    LaunchedEffect(sortedPhotos) {
        if (sortedPhotos.isNotEmpty()) {
            lazyListState.scrollToItem(sortedPhotos.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(com.example.baselift.R.string.insights_visual_diary), color = CrystalWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.CameraAlt, contentDescription = "Add Photo", tint = NeonGreen, modifier = Modifier.clickable { onAddPhoto() })
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (sortedPhotos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(com.example.baselift.R.string.insights_no_photos), color = MediumGrey)
            }
        } else {
            val leftFadeAlpha by animateFloatAsState(
                targetValue = if (lazyListState.canScrollBackward) 0.6f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "leftFadeAlpha"
            )
            val rightFadeAlpha by animateFloatAsState(
                targetValue = if (lazyListState.canScrollForward) 0.6f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "rightFadeAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                LazyRow(
                    state = lazyListState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(sortedPhotos) { index, photoLog ->
                        val isLatest = index == sortedPhotos.size - 1
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, if (isLatest) NeonGreen else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { onPhotoClick(photoLog) }
                        ) {
                            AsyncImage(
                                model = Uri.parse(photoLog.photoUri),
                                contentDescription = "Progress Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            if (isLatest) {
                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(NeonGreen).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(stringResource(com.example.baselift.R.string.insights_latest), color = PureBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            val associatedWeight = weightLogs.find { isSameCalendarDay(it.timestamp, photoLog.timestamp) }
                            if (associatedWeight != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "${associatedWeight.weightValue} KG",
                                        color = CrystalWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Text(
                                text = dateFormat.format(Date(photoLog.timestamp)).uppercase(Locale.US),
                                color = CrystalWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                            )
                        }
                    }
                }

                // máscara de desvanecimento esquerda
                if (leftFadeAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(48.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(PureBlack.copy(alpha = leftFadeAlpha), Color.Transparent)
                                )
                            )
                    )
                }

                // máscara de desvanecimento direita
                if (rightFadeAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(48.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, PureBlack.copy(alpha = rightFadeAlpha))
                                )
                            )
                    )
                }
            }
        }
    }
}

fun isSameCalendarDay(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun PhotoDetailDialog(
    photoLog: PhotoLogEntity,
    weightLogs: List<WeightLogEntity>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US)
    val associatedWeight = weightLogs.find { isSameCalendarDay(it.timestamp, photoLog.timestamp) }
    
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureBlack, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(com.example.baselift.R.string.insights_progress_photo),
                            color = NeonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = stringResource(com.example.baselift.R.string.insights_close),
                            color = MediumGrey,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onDismiss() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // imagem com zoom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .zIndex(1f)
                            .background(DarkSurface, RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val oldScale = scale
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    
                                    val fractionalX = centroid.x - size.width / 2
                                    val fractionalY = centroid.y - size.height / 2
                                    
                                    offset = Offset(
                                        offset.x * (scale / oldScale) + pan.x - fractionalX * (scale - oldScale),
                                        offset.y * (scale / oldScale) + pan.y - fractionalY * (scale - oldScale)
                                    )
                                }
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    do {
                                        val event = awaitPointerEvent()
                                    } while (event.changes.any { it.pressed })
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                            }
                        ,
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = Uri.parse(photoLog.photoUri),
                            contentDescription = "Zoomed Progress Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // visualização da data
                    Text(
                        text = dateFormat.format(Date(photoLog.timestamp)).uppercase(Locale.US),
                        color = CrystalWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    var showConfirmDelete by remember { mutableStateOf(false) }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (!showConfirmDelete) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (associatedWeight != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NeonGreen.copy(alpha = 0.1f))
                                        .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${associatedWeight.weightValue} KG",
                                        color = NeonGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SoftCoral.copy(alpha = 0.1f))
                                    .border(1.dp, SoftCoral.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { showConfirmDelete = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Photo",
                                    tint = SoftCoral,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showConfirmDelete = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MediumGrey),
                                border = BorderStroke(1.dp, MediumGrey.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(com.example.baselift.R.string.workout_cancel), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    showConfirmDelete = false
                                    onDelete()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SoftCoral),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(com.example.baselift.R.string.insights_confirm_delete), color = PureBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// componentes visuais e de ajuda mantidos intactos
@Composable
fun MetricCard(
    label: String, 
    value: String, 
    unit: String, 
    modifier: Modifier = Modifier, 
    icon: ImageVector? = null,
    borderColor: Color = Color.White.copy(alpha = 0.15f),
    valueColor: Color = CrystalWhite
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MediumGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MediumGrey, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, color = MediumGrey, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
fun BmiBar(bmi: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxBmiRange = 40f
            val fraction = (bmi / maxBmiRange).coerceIn(0f, 1f)
            val offsetDp = maxWidth * fraction
            
            Icon(
                Icons.Default.ArrowDropDown, 
                contentDescription = "Your BMI", 
                tint = CrystalWhite, 
                modifier = Modifier.offset(x = offsetDp - 12.dp)
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Box(modifier = Modifier.weight(18.5f).fillMaxHeight().background(ElectricBlue))
            Box(modifier = Modifier.weight(6.5f).fillMaxHeight().background(NeonGreen))
            Box(modifier = Modifier.weight(5f).fillMaxHeight().background(SunYellow))
            Box(modifier = Modifier.weight(10f).fillMaxHeight().background(SoftCoral))
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(18.5f), contentAlignment = Alignment.TopEnd) {
                Text("18.5", color = MediumGrey, fontSize = 10.sp, modifier = Modifier.offset(x = 10.dp))
            }
            Box(modifier = Modifier.weight(6.5f), contentAlignment = Alignment.TopEnd) {
                Text("25", color = MediumGrey, fontSize = 10.sp, modifier = Modifier.offset(x = 6.dp))
            }
            Box(modifier = Modifier.weight(5f), contentAlignment = Alignment.TopEnd) {
                Text("30", color = MediumGrey, fontSize = 10.sp, modifier = Modifier.offset(x = 6.dp))
            }
            Box(modifier = Modifier.weight(10f))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(18.5f), contentAlignment = Alignment.Center) {
                Text(stringResource(com.example.baselift.R.string.insights_under), color = ElectricBlue, fontSize = 10.sp)
            }
            Box(modifier = Modifier.weight(6.5f), contentAlignment = Alignment.Center) {
                Text(stringResource(com.example.baselift.R.string.insights_normal), color = NeonGreen, fontSize = 10.sp)
            }
            Box(modifier = Modifier.weight(5f), contentAlignment = Alignment.Center) {
                Text(stringResource(com.example.baselift.R.string.insights_over), color = SunYellow, fontSize = 10.sp)
            }
            Box(modifier = Modifier.weight(10f), contentAlignment = Alignment.Center) {
                Text(stringResource(com.example.baselift.R.string.insights_obese), color = SoftCoral, fontSize = 10.sp)
            }
        }
    }
}

fun getBmiCategory(bmi: Float): String {
    return when {
        bmi < 18.5f -> "UNDERWEIGHT"
        bmi < 25f -> "HEALTHY RANGE"
        bmi < 30f -> "OVERWEIGHT"
        else -> "OBESE"
    }
}

fun getBmiColor(bmi: Float): Color {
    return when {
        bmi < 18.5f -> ElectricBlue
        bmi < 25f -> NeonGreen
        bmi < 30f -> SunYellow
        else -> SoftCoral
    }
}
