package com.example.baselift.viewmodel

import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.ViewModel.workout.buildExerciseModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseModelsMapperTest {
    private fun log(exerciseId: Int, setNumber: Int, completed: Boolean, id: Int = setNumber) =
        SetLogEntity(id = id, sessionId = 20, exerciseId = exerciseId, setNumber = setNumber,
            weight = 80f, reps = 8, isCompleted = completed, timestamp = 1_000L)

    @Test fun `maps previous defaults and sequential check permissions per exercise`() {
        val squat = ExerciseEntity(id = 1, workoutId = 10, name = "Squat", setCount = 3)
        val press = ExerciseEntity(id = 2, workoutId = 10, name = "Press", setCount = 2)
        val first = log(1, 1, completed = true)
        val second = log(1, 2, completed = false)
        val previousSquat = log(1, 2, completed = true).copy(weight = 75f, reps = 10)
        val previousPress = log(2, 1, completed = true).copy(weight = 40f, reps = 12)

        val mapped = buildExerciseModels(
            exercises = listOf(squat, press),
            setLogs = listOf(first, second),
            previousSetsByKey = mapOf((1 to 2) to previousSquat, (2 to 1) to previousPress)
        )

        assertEquals(listOf(squat, press), mapped.map { it.exercise })
        val squatSets = mapped[0].sets
        assertEquals(listOf(1, 2, 3), squatSets.map { it.setNumber })
        assertSame(first, squatSets[0].currentLog)
        assertTrue(squatSets[0].isCompleted)
        assertTrue(squatSets[0].isLastCompleted)
        assertTrue(squatSets[0].canBeChecked)
        assertSame(second, squatSets[1].currentLog)
        assertEquals(75f, squatSets[1].prevWeight)
        assertEquals(10, squatSets[1].prevReps)
        assertFalse(squatSets[1].isCompleted)
        assertTrue(squatSets[1].canBeChecked)
        assertFalse(squatSets[2].canBeChecked)
        assertTrue(squatSets[2].canBeRemoved)
        assertFalse(squatSets[1].canBeRemoved)

        val pressSets = mapped[1].sets
        assertEquals(listOf(1, 2), pressSets.map { it.setNumber })
        assertEquals(40f, pressSets[0].prevWeight)
        assertEquals(12, pressSets[0].prevReps)
        assertTrue(pressSets[0].canBeChecked)
        assertFalse(pressSets[1].canBeChecked)
        assertTrue(pressSets[1].canBeRemoved)
    }

    @Test fun `retains first duplicate log and extends rows through highest current set`() {
        val exercise = ExerciseEntity(id = 1, workoutId = 10, name = "Squat", setCount = 1)
        val first = log(1, 1, completed = false, id = 1)
        val duplicate = log(1, 1, completed = false, id = 2)
        val third = log(1, 3, completed = false, id = 3)

        val sets = buildExerciseModels(listOf(exercise), listOf(first, duplicate, third), emptyMap())
            .single().sets

        assertEquals(listOf(1, 2, 3), sets.map { it.setNumber })
        assertSame(first, sets[0].currentLog)
        assertEquals(null, sets[1].currentLog)
        assertSame(third, sets[2].currentLog)
        assertTrue(sets[0].canBeChecked)
        assertFalse(sets[1].canBeChecked)
        assertTrue(sets[2].canBeRemoved)
    }
}
