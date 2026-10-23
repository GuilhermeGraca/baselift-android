package com.example.baselift.ViewModel.workout

import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity

/** Maps validated session rows and previous-session defaults without owning any ViewModel state. */
internal fun buildExerciseModels(
    exercises: List<ExerciseEntity>,
    setLogs: List<SetLogEntity>,
    previousSetsByKey: Map<Pair<Int, Int>, SetLogEntity>
): List<ExerciseUiModel> {
    val setsByExercise = setLogs.groupBy { it.exerciseId }
    return exercises.map { exercise ->
        val currentLogsByNumber = mutableMapOf<Int, SetLogEntity>()
        var maxSetNumber = exercise.setCount
        var maxCompleted = 0
        setsByExercise[exercise.id].orEmpty().forEach { log ->
            // Preserve first-log behavior if current-session data contains duplicates.
            currentLogsByNumber.putIfAbsent(log.setNumber, log)
            maxSetNumber = maxOf(maxSetNumber, log.setNumber)
            if (log.isCompleted) maxCompleted = maxOf(maxCompleted, log.setNumber)
        }

        val uiSets = (1..maxSetNumber).map { setNum ->
            val currentLog = currentLogsByNumber[setNum]
            val prevLog = previousSetsByKey[exercise.id to setNum]
            val isSetCompleted = currentLog?.isCompleted == true
            val isLastCompleted = setNum == maxCompleted
            val isNextAvailable = setNum == maxCompleted + 1

            SetUiModel(
                setNumber = setNum,
                currentLog = currentLog,
                prevWeight = prevLog?.weight,
                prevReps = prevLog?.reps,
                isCompleted = isSetCompleted,
                isLastCompleted = isLastCompleted,
                canBeChecked = (!isSetCompleted && isNextAvailable) || (isSetCompleted && isLastCompleted),
                canBeRemoved = setNum == maxSetNumber && maxSetNumber > 1
            )
        }
        ExerciseUiModel(exercise, uiSets)
    }
}
