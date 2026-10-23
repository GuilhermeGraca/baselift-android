package com.example.baselift.ViewModel.workout

import androidx.compose.runtime.Stable
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity

@Stable
data class SetUiModel(
    val setNumber: Int,
    val currentLog: SetLogEntity?, // null se ainda n tiver sido preenchido nesta sessao
    val prevWeight: Float?,
    val prevReps: Int?,
    val isCompleted: Boolean = false,
    val isLastCompleted: Boolean = false,
    val canBeChecked: Boolean = false,
    val canBeRemoved: Boolean = false
)

@Stable
data class ExerciseUiModel(
    val exercise: ExerciseEntity,
    val sets: List<SetUiModel>
)
