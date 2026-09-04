package com.example.baselift.View.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.baselift.ViewModel.workout.ExerciseUiModel
import com.example.baselift.View.theme.*

@Composable
fun ExerciseCard(
    exerciseModel: ExerciseUiModel,
    draftWeights: Map<String, String>,
    draftReps: Map<String, String>,
    onUpdateDraftWeight: (Int, Int, String) -> Unit,
    onUpdateDraftReps: (Int, Int, String) -> Unit,
    onRemoveLastSet: () -> Unit,
    onAddSet: () -> Unit,
    onLogSet: (Int, Float, Int, Boolean, Int) -> Unit,
    onDeleteExercise: () -> Unit,
    onEditExercise: (String, String, String) -> Unit
) {
    val exercise = exerciseModel.exercise
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditExerciseDialog(
            currentName = exercise.name,
            currentEquipment = exercise.equipment,
            currentMuscleGroups = exercise.muscleGroups,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, eq, musc ->
                onEditExercise(name, eq, musc)
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(com.example.baselift.R.string.workout_delete_exercise),
            message = stringResource(com.example.baselift.R.string.workout_delete_exercise_msg, exercise.name),
            onConfirm = {
                onDeleteExercise()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1B1B))
    ) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            // cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, color = CrystalWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (exercise.equipment.isNotBlank()) {
                        Text(exercise.equipment, color = MediumGrey, fontSize = 12.sp)
                    }
                }
                // menu de três pontos
                Box {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color(0xFFC4C9AC),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showMenu = true }
                            .padding(8.dp)
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.baselift.R.string.workout_edit_exercise), color = CrystalWhite, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MediumGrey, modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; showEditDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.baselift.R.string.workout_delete_exercise), color = Color(0xFFFF4444), fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF4444), modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; showDeleteDialog = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // etiquetas
            if (exercise.muscleGroups.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    exercise.muscleGroups.split(",").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF333333), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(tag.trim(), color = Color(0xFFC4C9AC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // cabeçalho da tabela de séries
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(com.example.baselift.R.string.workout_set_col), color = Color(0xFFC4C9AC), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f).padding(start = 8.dp))
                Text(stringResource(com.example.baselift.R.string.workout_prev_col), color = Color(0xFFC4C9AC), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(stringResource(com.example.baselift.R.string.workout_kg_col), color = Color(0xFFC4C9AC), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(com.example.baselift.R.string.workout_reps_col), color = Color(0xFFC4C9AC), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(64.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFF232323), thickness = 1.dp)
            Spacer(modifier = Modifier.height(2.dp))
        }

        Column(modifier = Modifier.padding(vertical = 0.dp)) {
            exerciseModel.sets.forEach { setModel ->
                androidx.compose.runtime.key(setModel.setNumber) {
                    SetRow(
                        exerciseId = exerciseModel.exercise.id,
                        setModel = setModel,
                        draftWeights = draftWeights,
                        draftReps = draftReps,
                        onUpdateDraftWeight = onUpdateDraftWeight,
                        onUpdateDraftReps = onUpdateDraftReps,
                        onRemove = onRemoveLastSet,
                        onLogSet = onLogSet
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // botão para adicionar série
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .clickable { onAddSet() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = stringResource(com.example.baselift.R.string.workout_add_set), color = Color(0xFFC4C9AC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
