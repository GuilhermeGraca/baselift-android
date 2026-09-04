package com.example.baselift.Utils

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class IdealWeightCalculatorTest {

    // Helper to assert floats with a small delta
    private fun assertFloatEquals(expected: Float, actual: Float, delta: Float = 0.1f) {
        assertEquals(expected, actual, delta)
    }

    @Test
    fun testMaleFormulas() {
        // Height 180cm (approx 70.866 inches -> 10.866 inches over 5 ft)
        // Robinson: 52 + 1.9 * 10.866 = 72.645
        // Miller: 56.2 + 1.41 * 10.866 = 71.521
        // Devine: 50.0 + 2.3 * 10.866 = 74.991
        // Hamwi: 48.0 + 2.7 * 10.866 = 77.338
        val results = IdealWeightCalculator.calculateIdealWeights(180f, "MALE")

        assertFloatEquals(72.6f, results.find { it.formulaName.contains("Robinson") }!!.weightKg)
        assertFloatEquals(71.5f, results.find { it.formulaName.contains("Miller") }!!.weightKg)
        assertFloatEquals(75.0f, results.find { it.formulaName.contains("Devine") }!!.weightKg)
        assertFloatEquals(77.3f, results.find { it.formulaName.contains("Hamwi") }!!.weightKg)
        
        val healthyRange = results.find { it.formulaName.contains("BMI") }!!.range!!
        assertFloatEquals(59.9f, healthyRange.first)
        assertFloatEquals(81.0f, healthyRange.second)
    }

    @Test
    fun testFemaleFormulas() {
        // Height 160cm (approx 62.99 inches -> 2.99 inches over 5 ft)
        // Robinson: 49 + 1.7 * 2.992 = 54.08
        // Miller: 53.1 + 1.36 * 2.992 = 57.16
        // Devine: 45.5 + 2.3 * 2.992 = 52.38
        // Hamwi: 45.5 + 2.2 * 2.992 = 52.08
        val results = IdealWeightCalculator.calculateIdealWeights(160f, "FEMALE")

        assertFloatEquals(54.1f, results.find { it.formulaName.contains("Robinson") }!!.weightKg)
        assertFloatEquals(57.2f, results.find { it.formulaName.contains("Miller") }!!.weightKg)
        assertFloatEquals(52.4f, results.find { it.formulaName.contains("Devine") }!!.weightKg)
        assertFloatEquals(52.1f, results.find { it.formulaName.contains("Hamwi") }!!.weightKg)
    }
    
    @Test
    fun testOtherGenderAverages() {
        // Same 180cm height, test if "OTHER" gender averages Male and Female formulas
        val maleResults = IdealWeightCalculator.calculateIdealWeights(180f, "MALE")
        val femaleResults = IdealWeightCalculator.calculateIdealWeights(180f, "FEMALE")
        val otherResults = IdealWeightCalculator.calculateIdealWeights(180f, "OTHER")
        
        val maleRobinson = maleResults.find { it.formulaName.contains("Robinson") }!!.weightKg
        val femaleRobinson = femaleResults.find { it.formulaName.contains("Robinson") }!!.weightKg
        val otherRobinson = otherResults.find { it.formulaName.contains("Robinson") }!!.weightKg
        
        assertFloatEquals((maleRobinson + femaleRobinson) / 2f, otherRobinson)
    }

    @Test
    fun testHeightUnder5Feet() {
        // Height 150cm (< 5 feet). Should clamp inchesOver5Feet to 0.
        // Robinson Male: 52 + 1.9 * 0 = 52
        val results = IdealWeightCalculator.calculateIdealWeights(150f, "MALE")
        assertFloatEquals(52.0f, results.find { it.formulaName.contains("Robinson") }!!.weightKg)
        assertFloatEquals(56.2f, results.find { it.formulaName.contains("Miller") }!!.weightKg)
        assertFloatEquals(50.0f, results.find { it.formulaName.contains("Devine") }!!.weightKg)
        assertFloatEquals(48.0f, results.find { it.formulaName.contains("Hamwi") }!!.weightKg)
    }
}
