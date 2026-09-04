package com.example.baselift.View.insights

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.baselift.View.insights.components.*

@Composable
fun InsightsScreen(
    onboardingViewModel: OnboardingViewModel,
    progressViewModel: ProgressViewModel,
    onRecalibrate: () -> Unit
) {
    val uiState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    val weightLogs by progressViewModel.weightLogs.collectAsStateWithLifecycle()
    val photoLogs by progressViewModel.photoLogs.collectAsStateWithLifecycle()
    val user by progressViewModel.user.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var selectedUrisForLogging by remember { mutableStateOf<List<Uri>?>(null) }
    var clickedPhoto by remember { mutableStateOf<PhotoLogEntity?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedUrisForLogging = uris
        }
    }

    val profilePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            progressViewModel.updateProfilePhoto(context, uri)
        }
    }

    var showNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(user?.name ?: "") }

    LaunchedEffect(user) {
        user?.let { currentUser ->
            nameInput = currentUser.name ?: ""
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack),
        contentPadding = PaddingValues(24.dp)
    ) {
        item {
            // cabeçalho de perfil
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // fotografia de perfil
                Box(
                    modifier = Modifier.size(105.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(2.dp, NeonGreen, CircleShape)
                            .background(DeepCharcoal)
                            .clickable {
                                profilePhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!user?.profilePhotoUri.isNullOrEmpty()) {
                            AsyncImage(
                                model = user?.profilePhotoUri,
                                contentDescription = "Profile Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "No Profile Photo",
                                tint = MediumGrey,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    // ícone da câmara
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .clip(CircleShape)
                            .background(NeonGreen)
                            .clickable {
                                profilePhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = PureBlack,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // nome do utilizador
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showNameDialog = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = (user?.name ?: stringResource(com.example.baselift.R.string.insights_no_name)).uppercase(),
                        color = CrystalWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Name",
                        tint = MediumGrey,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            // métricas base
            Text(stringResource(com.example.baselift.R.string.insights_baseline_metrics), color = CrystalWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard(label = stringResource(com.example.baselift.R.string.insights_gender), value = uiState.gender, unit = "", icon = Icons.Default.Person, modifier = Modifier.weight(1f))
                MetricCard(label = stringResource(com.example.baselift.R.string.insights_age), value = uiState.age.toString(), unit = stringResource(com.example.baselift.R.string.insights_yrs), icon = Icons.Default.DateRange, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard(label = stringResource(com.example.baselift.R.string.insights_height), value = uiState.height.toString(), unit = uiState.preferredHeightUnit, icon = Icons.Default.Height, modifier = Modifier.weight(1f))
                MetricCard(
                    label = stringResource(com.example.baselift.R.string.insights_current_weight), 
                    value = uiState.weight.toString(), 
                    unit = uiState.preferredWeightUnit, 
                    icon = Icons.Default.FitnessCenter,
                    modifier = Modifier.weight(1f),
                    borderColor = NeonGreen,
                    valueColor = NeonGreen
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            MetricCard(label = stringResource(com.example.baselift.R.string.insights_lifestyle), value = uiState.activityLevel.uppercase(), unit = "", icon = Icons.Default.DirectionsRun, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            // botão de recalibrar
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(DarkSurface)
                        .clickable { onRecalibrate() }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recalibrate", tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(com.example.baselift.R.string.insights_recalibrate), color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }

        item {
            // secção de IMC
            val bmiColor = getBmiColor(uiState.bmi)
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
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(com.example.baselift.R.string.insights_bmi), color = CrystalWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(bmiColor.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(getBmiCategory(uiState.bmi), color = bmiColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.bmi.toString(), color = bmiColor, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(24.dp))
                BmiBar(uiState.bmi)

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(com.example.baselift.R.string.insights_bmi_description, getBmiCategory(uiState.bmi)),
                    color = MediumGrey,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = ElectricBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(com.example.baselift.R.string.insights_bmi_disclaimer),
                        color = MediumGrey,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }

        item {
            // tendência de peso
            WeightTrendSection(
                weightLogs = weightLogs, 
                targetWeight = user?.targetWeight,
                onSetTargetWeight = { progressViewModel.setTargetWeight(it) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            // registo de peso
            LogWeightSection { weight, timestamp ->
                progressViewModel.addWeightLog(weight, timestamp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            // histórico
            TechnicalHistoryLedger(weightLogs, user, onDelete = { progressViewModel.deleteWeightLog(it) })
            Spacer(modifier = Modifier.height(48.dp))
        }

        item {
            // diário visual
            VisualDiarySection(
                photoLogs = photoLogs,
                onPhotoClick = { clickedPhoto = it }
            ) {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Spacer(modifier = Modifier.height(100.dp)) // espaço para navegação inferior
        }
    }

    // diálogo para adicionar foto
    if (selectedUrisForLogging != null) {
        var photoTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
        var showPhotoCalendarDialog by remember { mutableStateOf(false) }
        val photoDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        Dialog(onDismissRequest = { selectedUrisForLogging = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureBlack)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(com.example.baselift.R.string.insights_associate_date),
                            color = NeonGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(com.example.baselift.R.string.insights_select_date_desc, selectedUrisForLogging?.size ?: 0),
                            color = CrystalWhite,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // caixa de seleção de data
                        OutlinedTextField(
                            value = photoDateFormat.format(Date(photoTimestamp)),
                            onValueChange = { },
                            readOnly = true,
                            trailingIcon = {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select Date", tint = MediumGrey)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPhotoCalendarDialog = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = Color.White.copy(alpha = 0.15f),
                                disabledContainerColor = PureBlack,
                                disabledTextColor = CrystalWhite
                            ),
                            enabled = false
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { selectedUrisForLogging = null }) {
                                Text(stringResource(com.example.baselift.R.string.workout_cancel), color = MediumGrey)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    selectedUrisForLogging?.let { uris ->
                                        progressViewModel.addPhotoLogs(context, uris, photoTimestamp)
                                    }
                                    selectedUrisForLogging = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(com.example.baselift.R.string.insights_save_photos), color = PureBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (showPhotoCalendarDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showPhotoCalendarDialog = false },
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
                        selectedDate = photoTimestamp,
                        maxDate = System.currentTimeMillis(),
                        onDateSelected = { timestamp ->
                            photoTimestamp = timestamp
                            showPhotoCalendarDialog = false
                        }
                    )
                }
            }
        }
    }

    // diálogo de detalhe da foto
    clickedPhoto?.let { photo ->
        PhotoDetailDialog(
            photoLog = photo,
            weightLogs = weightLogs,
            onDismiss = { clickedPhoto = null },
            onDelete = {
                progressViewModel.deletePhotoLog(photo)
                clickedPhoto = null
            }
        )
    }

    if (showNameDialog) {
        Dialog(onDismissRequest = { showNameDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureBlack)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(com.example.baselift.R.string.insights_edit_name),
                            color = NeonGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text(stringResource(com.example.baselift.R.string.insights_name_label), color = MediumGrey) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreen,
                                unfocusedBorderColor = PureBlack,
                                focusedContainerColor = PureBlack,
                                unfocusedContainerColor = PureBlack,
                                focusedTextColor = CrystalWhite,
                                unfocusedTextColor = CrystalWhite
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showNameDialog = false }) {
                                Text(stringResource(com.example.baselift.R.string.workout_cancel), color = MediumGrey)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    progressViewModel.updateProfileName(nameInput.ifBlank { null })
                                    showNameDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(com.example.baselift.R.string.insights_save), color = PureBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

