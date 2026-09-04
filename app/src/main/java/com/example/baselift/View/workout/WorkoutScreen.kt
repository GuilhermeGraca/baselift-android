package com.example.baselift.View.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.View.theme.*
import com.example.baselift.ViewModel.workout.WorkoutViewModel
import com.example.baselift.View.workout.components.ExerciseCard
import com.example.baselift.View.workout.components.GenericInputDialog
import com.example.baselift.View.workout.components.RestTimerWidget
import com.example.baselift.View.workout.components.ConfirmDeleteDialog
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showAddWorkoutDialog by remember { mutableStateOf(false) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var workoutToDelete by remember { mutableStateOf<WorkoutEntity?>(null) }
    
    var showConfetti by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Defer rendering of heavy lists by a few frames to make tab transition perfectly smooth
    var isContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        isContentReady = true
    }

    if (workoutToDelete != null) {
        ConfirmDeleteDialog(
            title = stringResource(com.example.baselift.R.string.workout_delete_workout),
            message = stringResource(com.example.baselift.R.string.workout_delete_workout_msg, workoutToDelete!!.name),
            onConfirm = {
                viewModel.deleteWorkout(workoutToDelete!!)
                workoutToDelete = null
            },
            onDismiss = { workoutToDelete = null }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // barra superior para abas de treino
            if (uiState.workouts.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.workouts) { workout ->
                        val isSelected = workout == uiState.selectedWorkout
                        var isPressed by remember { mutableStateOf(false) }
                        val animatedBgAlpha by animateFloatAsState(targetValue = if (isPressed) 0.3f else 0f, animationSpec = tween(600))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) NeonGreen else Color.Transparent)
                                .then(
                                    Modifier
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color(0xFFFF6B6B).copy(alpha = animatedBgAlpha), // cor suave coral
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    isPressed = true
                                                    tryAwaitRelease()
                                                    isPressed = false
                                                },
                                                onLongPress = {
                                                    isPressed = false
                                                    workoutToDelete = workout
                                                },
                                                onTap = {
                                                    viewModel.selectWorkout(workout)
                                                }
                                            )
                                        }
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = workout.name.uppercase(),
                                color = if (isSelected) Color(0xFF556D00) else Color(0xFFC4C9AC),
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(com.example.baselift.R.string.workout_no_routines), color = MediumGrey)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // lista de exercícios
            if (uiState.selectedWorkout != null && isContentReady) {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 120.dp) // espaço para elementos flutuantes
                ) {
                    items(uiState.exercises, key = { it.exercise.id }) { exerciseModel ->
                        ExerciseCard(
                            exerciseModel = exerciseModel,
                            draftWeights = viewModel.draftWeights,
                            draftReps = viewModel.draftReps,
                            onUpdateDraftWeight = { exId, setNum, v -> viewModel.updateDraftWeight(exId, setNum, v) },
                            onUpdateDraftReps = { exId, setNum, v -> viewModel.updateDraftReps(exId, setNum, v) },
                            onAddSet = { viewModel.addSetToExercise(exerciseModel.exercise.id) },
                            onRemoveLastSet = { viewModel.removeLastSet(exerciseModel.exercise.id) },
                            onDeleteExercise = { viewModel.deleteExercise(exerciseModel.exercise.id) },
                            onEditExercise = { name, eq, mg -> viewModel.updateExercise(exerciseModel.exercise.id, name, eq, mg) },
                            onLogSet = { setNumber, weight, reps, isCompleted, existingId ->
                                viewModel.logSet(
                                    exerciseId = exerciseModel.exercise.id,
                                    setNumber = setNumber,
                                    weight = weight,
                                    reps = reps,
                                    isCompleted = isCompleted,
                                    existingSetId = existingId
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        // botão para adicionar exercício
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Transparent)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .clickable { showAddExerciseDialog = true }
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Exercise", tint = CrystalWhite)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(com.example.baselift.R.string.workout_add_exercise_session), color = CrystalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // temporizador de descanso
                        RestTimerWidget(modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(16.dp))

                        // botão para finalizar treino
                        if (uiState.exercises.isNotEmpty()) {
                            val hasCompletedSets = uiState.exercises.any { ex -> ex.sets.any { set -> set.isCompleted } }
                            
                            Button(
                                onClick = { 
                                    showConfetti = true
                                    scope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        showConfetti = false
                                        viewModel.finalizeWorkout()
                                    }
                                },
                                enabled = hasCompletedSets,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen,
                                    disabledContainerColor = Color(0xFF222222),
                                    contentColor = PureBlack,
                                    disabledContentColor = Color(0xFF555555)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(com.example.baselift.R.string.workout_finalize), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }

        // widgets flutuantes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // botão flutuante principal
            FloatingActionButton(
                onClick = { showAddWorkoutDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).size(56.dp),
                containerColor = NeonGreen,
                contentColor = PureBlack,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Workout", modifier = Modifier.size(32.dp))
            }
        }

        if (showConfetti) {
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(
                    Party(
                        speed = 30f,
                        maxSpeed = 60f,
                        damping = 0.9f,
                        angle = 290,
                        spread = 45,
                        colors = listOf(0xCCFF00.toInt(), 0x00E5FF.toInt(), 0xFFFF6B6B.toInt()),
                        emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                        position = Position.Relative(0.0, 1.0)
                    ),
                    Party(
                        speed = 30f,
                        maxSpeed = 60f,
                        damping = 0.9f,
                        angle = 250,
                        spread = 45,
                        colors = listOf(0xCCFF00.toInt(), 0x00E5FF.toInt(), 0xFFFF6B6B.toInt()),
                        emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                        position = Position.Relative(1.0, 1.0)
                    )
                )
            )
        }
    }

    // diálogos
    if (showAddWorkoutDialog) {
        var newWorkoutName by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showAddWorkoutDialog = false }) {
            GenericInputDialog(
                title = stringResource(com.example.baselift.R.string.workout_new_routine),
                content = {
                    BasicTextField(
                        value = newWorkoutName,
                        onValueChange = { newWorkoutName = it.uppercase() },
                        modifier = Modifier.fillMaxWidth().background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(16.dp),
                        textStyle = Typography.bodyLarge.copy(color = CrystalWhite)
                    )
                },
                onConfirm = {
                    if (newWorkoutName.isNotBlank()) {
                        viewModel.createWorkout(newWorkoutName)
                        showAddWorkoutDialog = false
                    }
                },
                onDismiss = { showAddWorkoutDialog = false }
            )
        }
    }

    if (showAddExerciseDialog) {
        var exName by remember { mutableStateOf("") }
        var exEquipment by remember { mutableStateOf("") }
        var exMuscles by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddExerciseDialog = false }) {
            GenericInputDialog(
                title = stringResource(com.example.baselift.R.string.workout_add_exercise),
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(com.example.baselift.R.string.workout_ex_name), color = MediumGrey, fontSize = 12.sp)
                        BasicTextField(
                            value = exName,
                            onValueChange = { exName = it },
                            modifier = Modifier.fillMaxWidth().background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(12.dp),
                            textStyle = Typography.bodyLarge.copy(color = CrystalWhite)
                        )
                        Text(stringResource(com.example.baselift.R.string.workout_ex_equipment), color = MediumGrey, fontSize = 12.sp)
                        BasicTextField(
                            value = exEquipment,
                            onValueChange = { exEquipment = it },
                            modifier = Modifier.fillMaxWidth().background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(12.dp),
                            textStyle = Typography.bodyLarge.copy(color = CrystalWhite)
                        )
                        Text(stringResource(com.example.baselift.R.string.workout_ex_muscles), color = MediumGrey, fontSize = 12.sp)
                        BasicTextField(
                            value = exMuscles,
                            onValueChange = { exMuscles = it.uppercase() },
                            modifier = Modifier.fillMaxWidth().background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(12.dp),
                            textStyle = Typography.bodyLarge.copy(color = CrystalWhite)
                        )
                    }
                },
                onConfirm = {
                    if (exName.isNotBlank()) {
                        viewModel.createExercise(exName, exEquipment, exMuscles)
                        showAddExerciseDialog = false
                    }
                },
                onDismiss = { showAddExerciseDialog = false }
            )
        }
    }
}
