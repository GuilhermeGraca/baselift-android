package com.example.baselift.View.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.baselift.View.theme.*
import com.example.baselift.View.workout.components.ExerciseCard
import com.example.baselift.View.workout.components.RestTimerWidget
import com.example.baselift.ViewModel.workout.WorkoutUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun WorkoutExerciseList(
    workoutId: Int,
    uiState: WorkoutUiState,
    draftWeights: Map<String, String>,
    draftReps: Map<String, String>,
    writesBlocked: Boolean,
    modifier: Modifier = Modifier,
    onMoveExercise: (fromIndex: Int, toIndex: Int, workoutId: Int, exerciseId: Int) -> Unit,
    onUpdateDraftWeight: (exerciseId: Int, setNumber: Int, value: String) -> Unit,
    onUpdateDraftReps: (exerciseId: Int, setNumber: Int, value: String) -> Unit,
    onAddSet: (exerciseId: Int) -> Unit,
    onRemoveLastSet: (exerciseId: Int, setNumber: Int, sessionId: Int?) -> Unit,
    onDeleteExercise: (exerciseId: Int) -> Unit,
    onEditExercise: (exerciseId: Int) -> Unit,
    onLogSet: (exerciseId: Int, setNumber: Int, weight: Float, reps: Int, isCompleted: Boolean, sessionId: Int?) -> Unit,
    onAddExercise: () -> Unit,
    onUpdateWorkoutRestTimer: (seconds: Int) -> Unit,
    onFinalizeWorkout: (sessionId: Int?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var hoverIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOffset by remember { mutableStateOf(0f) }
    var initialDraggedItemOffset by remember { mutableStateOf(0f) }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var settlementJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val shiftPixels = with(density) { 120.dp.toPx() } // Fixed hole size for collapsed cards

    LazyColumn(
        state = lazyListState,
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        items(uiState.exercises.size, key = { uiState.exercises[it].exercise.id }) { index ->
            val exerciseModel = uiState.exercises[index]
            val exerciseId = exerciseModel.exercise.id
            val isDragged = draggedIndex == index
            val isAnyDragging = draggedIndex != null

            // Calculate visual shift for items making way for the dragged item
            val shiftAmount by animateFloatAsState(
                targetValue = if (isAnyDragging && hoverIndex != null && draggedIndex != null) {
                    val dIdx = draggedIndex!!
                    val hIdx = hoverIndex!!

                    if (dIdx < hIdx) {
                        if (index > dIdx && index <= hIdx) -shiftPixels else 0f
                    } else if (dIdx > hIdx) {
                        if (index >= hIdx && index < dIdx) shiftPixels else 0f
                    } else 0f
                } else 0f
            )

            val elevation by animateFloatAsState(if (isDragged) 24f else 0f)

            LaunchedEffect(isDragged) {
                if (isDragged) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            val dragModifier = Modifier.pointerInput(exerciseId, index, writesBlocked) {
                        if (writesBlocked) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { _ ->
                                settlementJob?.cancel()
                                draggedIndex = index
                                hoverIndex = index
                                draggedOffset = 0f
                                val item = lazyListState.layoutInfo.visibleItemsInfo.find { it.index == index }
                                initialDraggedItemOffset = item?.offset?.toFloat() ?: 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedOffset += dragAmount.y

                                val visibleItems = lazyListState.layoutInfo.visibleItemsInfo

                                val draggedTop = initialDraggedItemOffset + draggedOffset
                                val draggedBottom = draggedTop + shiftPixels
                                val centerY = draggedTop + shiftPixels / 2f

                                var newHover = draggedIndex
                                for (item in visibleItems) {
                                    val idx = item.index
                                    if (idx == draggedIndex) continue

                                    val itemTop = item.offset.toFloat()
                                    val itemBottom = itemTop + item.size.toFloat()

                                    if (draggedOffset > 0) { // drag down
                                        if (idx > draggedIndex!! && draggedBottom > itemTop + 40f) {
                                            newHover = maxOf(newHover!!, idx)
                                        }
                                    } else { // drag up
                                        if (idx < draggedIndex!! && draggedTop < itemBottom - 40f) {
                                            newHover = minOf(newHover!!, idx)
                                        }
                                    }
                                }

                                if (newHover != null && newHover < uiState.exercises.size) {
                                    hoverIndex = newHover
                                }

                                // Simple Auto-scroll
                                val viewportTop = lazyListState.layoutInfo.viewportStartOffset
                                val viewportBottom = lazyListState.layoutInfo.viewportEndOffset
                                coroutineScope.launch {
                                    if (centerY > viewportBottom - 200) {
                                        lazyListState.scrollBy(15f)
                                    } else if (centerY < viewportTop + 200) {
                                        lazyListState.scrollBy(-15f)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (draggedIndex != null && hoverIndex != null) {
                                    val dropIdx = hoverIndex!!

                                    // Posição visual do card no momento de largar (relativa ao viewport)
                                    val targetViewportY = (initialDraggedItemOffset + draggedOffset).toInt()

                                    if (draggedIndex != dropIdx) {
                                        onMoveExercise(draggedIndex!!, dropIdx, workoutId, exerciseId)
                                    }

                                    settlementJob?.cancel()
                                    val droppedExerciseId = exerciseId
                                    settlementJob = coroutineScope.launch {
                                        val currentOffset = awaitStableDraggedItemOffset(dropIdx) {
                                            lazyListState.layoutInfo.visibleItemsInfo
                                                .find { it.key == droppedExerciseId }
                                                ?.let { DraggedItemPlacement(it.index, it.offset, it.size) }
                                        } ?: return@launch
                                        val delta = (currentOffset - targetViewportY).toFloat()
                                        if (delta != 0f) {
                                            lazyListState.animateScrollBy(delta)
                                        }
                                    }
                                }
                                draggedIndex = null
                                hoverIndex = null
                                draggedOffset = 0f
                                initialDraggedItemOffset = 0f
                            },
                            onDragCancel = {
                                settlementJob?.cancel()
                                draggedIndex = null
                                hoverIndex = null
                                draggedOffset = 0f
                                initialDraggedItemOffset = 0f
                            }
                        )
                    }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isDragged) {
                            val currentItem = lazyListState.layoutInfo.visibleItemsInfo.find { it.index == index }
                            val currentItemOffset = currentItem?.offset?.toFloat() ?: 0f
                            translationY = initialDraggedItemOffset + draggedOffset - currentItemOffset
                            shadowElevation = elevation
                            shape = RoundedCornerShape(12.dp)
                            ambientShadowColor = Color.Black
                            spotShadowColor = Color.Black.copy(alpha = 0.7f)
                        } else {
                            translationY = shiftAmount
                        }
                    }
                    .shadow(elevation.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = 0.7f))
            ) {
                val displayedSessionId = uiState.activeSession?.id
                ExerciseCard(
                    exerciseModel = exerciseModel,
                    draftWeights = draftWeights,
                    draftReps = draftReps,
                    onUpdateDraftWeight = onUpdateDraftWeight,
                    onUpdateDraftReps = onUpdateDraftReps,
                    onAddSet = { onAddSet(exerciseId) },
                    onRemoveLastSet = { number ->
                        onRemoveLastSet(exerciseId, number, displayedSessionId)
                    },
                    onDeleteExercise = { onDeleteExercise(exerciseId) },
                    onEditExercise = { onEditExercise(exerciseId) },
                    onLogSet = { setNumber, weight, reps, isCompleted, _ ->
                        onLogSet(exerciseId, setNumber, weight, reps, isCompleted, displayedSessionId)
                    },
                    isDragging = isDragged,
                    isAnyDragging = isAnyDragging,
                    dragModifier = dragModifier,
                    savingSetNumbers = uiState.pendingSetWrites.filter {
                        it.sessionId == displayedSessionId && it.exerciseId == exerciseId
                    }.map { it.setNumber }.toSet(),
                    writesBlocked = writesBlocked,
                    removingSetNumbers = uiState.pendingSetRemovals.filter {
                        it.sessionId == displayedSessionId && it.exerciseId == exerciseId
                    }.map { it.setNumber }.toSet(),
                    isAddingSets = exerciseId in uiState.addingSetExerciseIds,
                    isSavingMetadata = uiState.exerciseEdits[exerciseId]?.isSaving == true
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = draggedIndex == null,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Column {
                    // botão para adicionar exercício
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Transparent)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !writesBlocked) { onAddExercise() }
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
                    RestTimerWidget(
                        modifier = Modifier.fillMaxWidth(),
                        initialTimerSeconds = uiState.selectedWorkout?.defaultRestTimer ?: 105,
                        settingsEnabled = !writesBlocked,
                        onTimerSaved = onUpdateWorkoutRestTimer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // botão para finalizar treino
                    if (uiState.exercises.isNotEmpty()) {
                        val hasCompletedSets = uiState.exercises.any { ex -> ex.sets.any { set -> set.isCompleted } }
                        val activeSessionId = uiState.activeSession?.id

                        Button(
                            onClick = { onFinalizeWorkout(activeSessionId) },
                            enabled = hasCompletedSets && !writesBlocked &&
                                uiState.pendingSetWrites.none { it.sessionId == activeSessionId } &&
                                uiState.pendingSetRemovals.none { it.sessionId == activeSessionId },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                disabledContainerColor = Color(0xFF222222),
                                contentColor = PureBlack,
                                disabledContentColor = Color(0xFF555555)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                stringResource(if (uiState.isFinalizing) com.example.baselift.R.string.workout_finalizing else com.example.baselift.R.string.workout_finalize),
                                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
