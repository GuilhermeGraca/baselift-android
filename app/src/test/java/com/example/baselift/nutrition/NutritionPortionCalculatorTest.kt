package com.example.baselift.nutrition

import com.example.baselift.Model.nutrition.NutritionPortionCalculator
import com.example.baselift.Model.nutrition.NutritionValues
import com.example.baselift.Model.nutrition.parseNutritionWholeNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionPortionCalculatorTest {
    private val label = NutritionValues(calories = 200, protein = 10.0, carbs = 20.0, fats = 5.0)

    @Test fun scalesEachFieldOnceAndRoundsHalvesUp() {
        assertEquals(NutritionValues(100, 5.0, 10.0, 2.5), NutritionPortionCalculator.calculate(label, true, 50.0))
        assertEquals(label, NutritionPortionCalculator.calculate(label, true, 100.0))
        assertEquals(NutritionValues(250, 12.5, 25.0, 6.25), NutritionPortionCalculator.calculate(label, true, 125.0))
        assertEquals(NutritionValues(300, 15.0, 30.0, 7.5), NutritionPortionCalculator.calculate(label, true, 150.0))
    }

    @Test fun keepsManualCaloriesAndIgnoresGramsInTotalMode() {
        assertEquals(label, NutritionPortionCalculator.calculate(label, false, null))
        assertEquals(label, NutritionPortionCalculator.calculate(label, false, 500.0))
    }

    @Test fun rejectsMissingOrInvalidAmountAndResultsThatCannotBeLogged() {
        assertNull(NutritionPortionCalculator.calculate(label, true, null))
        assertNull(NutritionPortionCalculator.calculate(label, true, 0.0))
        assertNull(NutritionPortionCalculator.calculate(label, true, -1.0))
        assertNull(NutritionPortionCalculator.calculate(NutritionValues(0, 0.0, 0.0, 0.0), true, 100.0))
        assertNull(NutritionPortionCalculator.calculate(NutritionValues(1, 0.0, 0.0, 0.0), true, 1.0))
        assertNull(NutritionPortionCalculator.calculate(NutritionValues(-1, 0.0, 0.0, 0.0), false, null))
        assertNull(NutritionPortionCalculator.calculate(NutritionValues(Int.MAX_VALUE, 0.0, 0.0, 0.0), true, 101.0))
    }

    @Test fun rejectsDecimalPasteInsteadOfChangingItsMeaning() {
        assertEquals(0, parseNutritionWholeNumber(""))
        assertEquals(12, parseNutritionWholeNumber("012"))
        assertNull(parseNutritionWholeNumber("12,5"))
        assertNull(parseNutritionWholeNumber("12.5"))
        assertNull(parseNutritionWholeNumber("-12"))
        assertNull(parseNutritionWholeNumber("999999999999999999999"))
    }
}
