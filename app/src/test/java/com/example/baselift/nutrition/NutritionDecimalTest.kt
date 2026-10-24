package com.example.baselift.nutrition

import com.example.baselift.Model.nutrition.NutritionPortionCalculator
import com.example.baselift.Model.nutrition.NutritionValues
import com.example.baselift.Model.nutrition.parseNutritionDecimal
import com.example.baselift.Model.nutrition.formatNutritionDecimal
import com.example.baselift.Model.nutrition.caloriesFromMacros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionDecimalTest {
    @Test fun decimalQuantityAndLabelRoundOnlyFinalTotalsWithHalvesUp() {
        assertEquals(NutritionValues(12, 0.31, 0.52, 0.01),
            NutritionPortionCalculator.calculate(NutritionValues(95, 2.5, 4.12, 0.04), true, 12.5))
        assertEquals(NutritionValues(1, 0.01, 0.01, 0.01),
            NutritionPortionCalculator.calculate(NutritionValues(1, 0.01, 0.01, 0.01), true, 50.0))
        assertEquals(NutritionValues(0, 0.01, 0.0, 0.0),
            NutritionPortionCalculator.calculate(NutritionValues(0, 1.0, 0.0, 0.0), true, 1.0))
    }

    @Test fun keepsManualCaloriesAndIgnoresGramsInServingMode() {
        val values = NutritionValues(95, 2.5, 4.12, 0.04)
        assertEquals(values, NutritionPortionCalculator.calculate(values, false, null))
        assertEquals(values, NutritionPortionCalculator.calculate(values, false, 500.5))
    }

    @Test fun rejectsNonFiniteValuesAndMacroOverflow() {
        val values = NutritionValues(95, 2.5, 4.12, 0.04)
        for (invalid in listOf(-0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            assertNull(NutritionPortionCalculator.calculate(values, true, invalid))
            assertNull(NutritionPortionCalculator.calculate(values.copy(protein = invalid), false, null))
        }
        assertNull(NutritionPortionCalculator.calculate(values.copy(protein = Int.MAX_VALUE.toDouble()), true, 101.0))
    }

    @Test fun parsesBothSeparatorsWithoutSilentlyChangingInvalidInput() {
        for (text in listOf("12.5", "12,5", "012.50", "012,50")) {
            assertEquals(12.5, parseNutritionDecimal(text)!!, 0.0)
        }
        assertEquals(0.5, parseNutritionDecimal(",5")!!, 0.0)
        assertEquals(0.0, parseNutritionDecimal("")!!, 0.0)
        assertEquals(12.0, parseNutritionDecimal("12,")!!, 0.0)
        for (text in listOf("1.234", "1,234", "1,2.5", "1e2", "-1", "+1", "NaN", "Infinity", "1 000", ".", " 2", "2147483648")) {
            assertNull(text, parseNutritionDecimal(text))
        }
    }

    @Test fun calorieRecalculationRetainsFractionalMacrosAndRoundsHalfUp() {
        assertEquals(25, caloriesFromMacros(2.5, 1.0, 1.25))
        assertEquals(28, caloriesFromMacros(2.5, 1.0, 1.5))
        assertEquals(1, caloriesFromMacros(0.0, 0.0, 0.06))
        assertNull(caloriesFromMacros(Double.NaN, 0.0, 0.0))
        assertNull(caloriesFromMacros(Int.MAX_VALUE.toDouble(), 0.0, 0.0))
    }

    @Test fun formattingRemovesExtraZeroesAndFloatingPointArtefacts() {
        assertEquals("12", formatNutritionDecimal(12.0))
        assertEquals("12.5", formatNutritionDecimal(12.50))
        assertEquals("0.3", formatNutritionDecimal(0.1 + 0.2))
        assertEquals("0.01", formatNutritionDecimal(0.005))
    }
}
