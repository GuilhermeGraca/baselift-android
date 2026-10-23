package com.example.baselift.View.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import com.example.baselift.View.workout.components.EditExerciseDialog
import com.example.baselift.View.workout.components.GenericInputDialog
import com.example.baselift.View.workout.components.ConfirmDeleteDialog
import com.example.baselift.View.workout.components.WorkoutLoadingContent
import com.example.baselift.View.workout.components.WorkoutRoutineTabsPlaceholder
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit
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
    // Only presentation waits for the animation; the save has already succeeded.
    val finalizedSessionId = uiState.finalizedSessionId
    LaunchedEffect(finalizedSessionId) {
        if (finalizedSessionId != null) {
            viewModel.acknowledgeFinalization(finalizedSessionId)
            showConfetti = true
        }
    }
    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            kotlinx.coroutines.delay(2000)
            showConfetti = false
        }
    }

    uiState.exerciseEdits.values.firstOrNull {
        it.isOpen && it.workoutId == uiState.selectedWorkout?.id &&
            !uiState.isLoading && !uiState.isSelectionLoading && !uiState.selectionLoadFailed
    }?.let { edit ->
        EditExerciseDialog(
            state = edit,
            onNameChange = { viewModel.updateExerciseEditDraft(edit.exerciseId, name = it) },
            onEquipmentChange = { viewModel.updateExerciseEditDraft(edit.exerciseId, equipment = it) },
            onMuscleGroupsChange = { viewModel.updateExerciseEditDraft(edit.exerciseId, muscleGroups = it) },
            onConfirm = { viewModel.saveExerciseEdit(edit.exerciseId) },
            onDismiss = { viewModel.dismissExerciseEdit(edit.exerciseId) }
        )
    }

    val reorderErrorWorkoutId = uiState.selectedWorkout?.id?.takeIf { it in uiState.reorderFailedWorkoutIds }
    if (reorderErrorWorkoutId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissReorderError(reorderErrorWorkoutId) },
            title = { Text(stringResource(com.example.baselift.R.string.workout_reorder_error_title)) },
            text = { Text(stringResource(com.example.baselift.R.string.workout_reorder_error_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissReorderError(reorderErrorWorkoutId) }) {
                    Text(stringResource(com.example.baselift.R.string.workout_finalize_error_dismiss))
                }
            }
        )
    }
    
    if (uiState.finalizationFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFinalizationError,
            title = { Text(stringResource(com.example.baselift.R.string.workout_finalize_error_title)) },
            text = { Text(stringResource(com.example.baselift.R.string.workout_finalize_error_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissFinalizationError) {
                    Text(stringResource(com.example.baselift.R.string.workout_finalize_error_dismiss))
                }
            }
        )
    }

    if (uiState.setSaveFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSetSaveError,
            title = { Text(stringResource(com.example.baselift.R.string.workout_set_save_error_title)) },
            text = { Text(stringResource(com.example.baselift.R.string.workout_set_save_error_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSetSaveError) {
                    Text(stringResource(com.example.baselift.R.string.workout_finalize_error_dismiss))
                }
            }
        )
    }

    if (uiState.setAddFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSetAddError,
            title = { Text(stringResource(com.example.baselift.R.string.workout_set_add_error_title)) },
            text = { Text(stringResource(com.example.baselift.R.string.workout_set_add_error_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSetAddError) {
                    Text(stringResource(com.example.baselift.R.string.workout_finalize_error_dismiss))
                }
            }
        )
    }

    if (uiState.setRemovalFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSetRemovalError,
            title = { Text(stringResource(com.example.baselift.R.string.workout_set_remove_error_title)) },
            text = { Text(stringResource(com.example.baselift.R.string.workout_set_remove_error_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSetRemovalError) {
                    Text(stringResource(com.example.baselift.R.string.workout_finalize_error_dismiss))
                }
            }
        )
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
        val selectedWorkout = uiState.selectedWorkout
        val hasRetainedSnapshot = selectedWorkout != null && uiState.hasRowsSnapshot &&
            uiState.activeSession?.workoutId == selectedWorkout.id
        val rowsRefreshing = uiState.isLoading || !uiState.areRowsValidated
        val writesBlocked = rowsRefreshing || uiState.workoutsLoadFailed ||
            uiState.selectionLoadFailed || uiState.isFinalizing

        Column(modifier = Modifier.fillMaxSize()) {
            // barra superior para abas de treino
            if (uiState.workouts.isNotEmpty() &&
                ((!uiState.isLoading && !uiState.workoutsLoadFailed) || hasRetainedSnapshot)) {
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
                                        .pointerInput(workout.id, uiState.isLoading, uiState.workoutsLoadFailed) {
                                            if (uiState.isLoading || uiState.workoutsLoadFailed) return@pointerInput
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
            } else if (uiState.isLoading && !hasRetainedSnapshot) {
                WorkoutRoutineTabsPlaceholder()
            } else if (!uiState.isLoading && !uiState.workoutsLoadFailed) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(com.example.baselift.R.string.workout_no_routines), color = MediumGrey)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // lista de exercícios
            // A same-session snapshot stays visible while membership and rows are revalidated.
            // Its controls remain blocked until a fresh row emission confirms it.
            if (uiState.isLoading && !hasRetainedSnapshot) {
                WorkoutLoadingContent(emptyList(), Modifier.weight(1f))
            } else if (uiState.workoutsLoadFailed && !hasRetainedSnapshot) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(com.example.baselift.R.string.workout_list_error), color = MediumGrey)
                    TextButton(onClick = viewModel::retryWorkouts) {
                        Text(stringResource(com.example.baselift.R.string.workout_selection_retry), color = NeonGreen)
                    }
                }
            } else if (selectedWorkout != null && uiState.isSelectionLoading && !hasRetainedSnapshot) {
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    if (uiState.loadingExerciseHeaders.isNotEmpty()) {
                        Text(stringResource(com.example.baselift.R.string.workout_preparing_sets),
                            color = MediumGrey, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    WorkoutLoadingContent(uiState.loadingExerciseHeaders, Modifier.weight(1f))
                }
            } else if (selectedWorkout != null && uiState.selectionLoadFailed && !hasRetainedSnapshot) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(com.example.baselift.R.string.workout_selection_error), color = MediumGrey)
                    TextButton(onClick = { viewModel.retryWorkoutSelection(selectedWorkout.id) }) {
                        Text(stringResource(com.example.baselift.R.string.workout_selection_retry), color = NeonGreen)
                    }
                }
            } else if (selectedWorkout != null) {
                if (uiState.workoutsLoadFailed) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(com.example.baselift.R.string.workout_list_error),
                            color = MediumGrey, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::retryWorkouts) {
                            Text(stringResource(com.example.baselift.R.string.workout_selection_retry), color = NeonGreen)
                        }
                    }
                }
                // Reserve this slim status strip so the cards never jump when validation starts.
                Box(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                    if (rowsRefreshing && !uiState.workoutsLoadFailed) {
                        Text(stringResource(com.example.baselift.R.string.workout_refreshing),
                            color = MediumGrey, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
                    }
                }
                key(selectedWorkout.id) {
                    WorkoutExerciseList(
                        workoutId = selectedWorkout.id,
                        uiState = uiState,
                        draftWeights = viewModel.draftWeights,
                        draftReps = viewModel.draftReps,
                        writesBlocked = writesBlocked,
                        modifier = Modifier.weight(1f),
                        onMoveExercise = viewModel::moveExercise,
                        onUpdateDraftWeight = viewModel::updateDraftWeight,
                        onUpdateDraftReps = viewModel::updateDraftReps,
                        onAddSet = viewModel::addSetToExercise,
                        onRemoveLastSet = viewModel::removeLastSet,
                        onDeleteExercise = viewModel::deleteExercise,
                        onEditExercise = viewModel::beginExerciseEdit,
                        onLogSet = { exerciseId, setNumber, weight, reps, completed, sessionId ->
                            viewModel.logSet(exerciseId, setNumber, weight, reps, completed, sessionId)
                        },
                        onAddExercise = { showAddExerciseDialog = true },
                        onUpdateWorkoutRestTimer = viewModel::updateWorkoutRestTimer,
                        onFinalizeWorkout = viewModel::finalizeWorkout
                    )
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
            if (!uiState.isLoading && !uiState.workoutsLoadFailed) FloatingActionButton(
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
                        position

                        = Position.Relative(0.0, 1.0)
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
                confirmEnabled = !uiState.isLoading && !uiState.workoutsLoadFailed,
                content = {
                    BasicTextField(
                        value = newWorkoutName,
                        onValueChange = { newWorkoutName = it.uppercase() },
                        modifier = Modifier.fillMaxWidth().background(DeepCharcoal, RoundedCornerShape(8.dp)).padding(16.dp),
                        textStyle = Typography.bodyLarge.copy(color = CrystalWhite)
                    )
                },
                onConfirm = {
                    if (newWorkoutName.isNotBlank() && !uiState.isLoading && !uiState.workoutsLoadFailed) {
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
                confirmEnabled = !uiState.isLoading && !uiState.workoutsLoadFailed &&
                    !uiState.isSelectionLoading && !uiState.selectionLoadFailed,
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
                    if (exName.isNotBlank() && !uiState.isLoading && !uiState.workoutsLoadFailed &&
                        !uiState.isSelectionLoading && !uiState.selectionLoadFailed) {
                        viewModel.createExercise(exName, exEquipment, exMuscles)
                        showAddExerciseDialog = false
                    }
                },
                onDismiss = { showAddExerciseDialog = false }
            )
        }
    }
}
