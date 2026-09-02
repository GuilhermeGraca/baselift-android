package com.example.baselift

import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Utils.CalculatorUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Testes unitários para o CalculatorUtils.
 *
 * Cobre toda a lógica de cálculo de métricas do utilizador:
 * BMI, BMR (Mifflin-St Jeor), TDEE, ajuste calórico por objetivo,
 * safety floor de 1200 kcal, divisão de macros (30/45/25),
 * conversão de unidades imperiais, e custom targets.
 */
class CalculatorUtilsTest {

    // =========================================================================
    // HELPER — utilizador base reutilizável nos testes
    // =========================================================================

    private fun baseUser(
        gender: String = "MALE",
        age: Int = 25,
        weight: Float = 80f,
        height: Float = 180f,
        weightUnit: String = "KG",
        heightUnit: String = "CM",
        activityLevel: String = "Moderate",
        goal: String = "Maintenance",
        isCustomTargets: Boolean = false,
        dailyCaloriesGoal: Int = 0,
        proteinGoal: Int = 0,
        carbsGoal: Int = 0,
        fatGoal: Int = 0
    ) = UserEntity(
        gender = gender,
        age = age,
        weight = weight,
        height = height,
        preferredWeightUnit = weightUnit,
        preferredHeightUnit = heightUnit,
        activityLevel = activityLevel,
        goal = goal,
        isCustomTargets = isCustomTargets,
        dailyCaloriesGoal = dailyCaloriesGoal,
        proteinGoal = proteinGoal,
        carbsGoal = carbsGoal,
        fatGoal = fatGoal
    )

    // =========================================================================
    // Task 1.1 — TESTES DE BMI
    // =========================================================================

    @Test
    fun calculateBmi_maleMetricUnits_returnsCorrectValue() {
        // BMI = 80 / (1.80)² = 80 / 3.24 ≈ 24.7
        val user = baseUser()
        val result = CalculatorUtils.calculateUserMetrics(user)
        assertEquals(24.7f, result.bmi, 0.1f)
    }

    @Test
    fun calculateBmi_imperialUnits_convertsAndCalculatesCorrectly() {
        // 176 lbs ≈ 79.83 kg, 5.9 ft ≈ 179.8 cm
        // BMI ≈ 79.83 / (1.798)² ≈ 24.7
        val user = baseUser(weight = 176f, height = 5.9f, weightUnit = "LBS", heightUnit = "FT")
        val result = CalculatorUtils.calculateUserMetrics(user)
        assertEquals(24.7f, result.bmi, 1.0f) // margem maior por dupla conversão
    }

    @Test
    fun calculateBmi_zeroHeight_returnsZero() {
        val user = baseUser(height = 0f)
        val result = CalculatorUtils.calculateUserMetrics(user)
        assertEquals(0f, result.bmi, 0.01f)
    }

    // =========================================================================
    // Task 1.2 — TESTES DE BMR E TDEE
    // =========================================================================

    @Test
    fun calculateBmr_male_usesCorrectMifflinFormula() {
        // BMR masculino = (10 × 80) + (6.25 × 180) - (5 × 25) + 5
        //               = 800 + 1125 - 125 + 5 = 1805
        // TDEE Moderate = 1805 × 1.55 = 2797.75 ≈ 2798
        val user = baseUser()
        val result = CalculatorUtils.calculateUserMetrics(user)
        assertEquals(2798f, result.dailyCaloriesGoal.toFloat(), 5f)
    }

    @Test
    fun calculateBmr_female_usesCorrectMifflinFormula() {
        // BMR feminino = (10 × 80) + (6.25 × 180) - (5 × 25) - 161
        //             = 800 + 1125 - 125 - 161 = 1639
        // TDEE Moderate = 1639 × 1.55 = 2540.45 ≈ 2540
        val user = baseUser(gender = "FEMALE")
        val result = CalculatorUtils.calculateUserMetrics(user)
        assertEquals(2540f, result.dailyCaloriesGoal.toFloat(), 5f)
    }

    @Test
    fun calculateBmr_femaleAlwaysLowerThanMale_sameInputs() {
        val male = CalculatorUtils.calculateUserMetrics(baseUser(gender = "MALE"))
        val female = CalculatorUtils.calculateUserMetrics(baseUser(gender = "FEMALE"))
        assertTrue(
            "Calorias femininas (${female.dailyCaloriesGoal}) devem ser < masculinas (${male.dailyCaloriesGoal})",
            female.dailyCaloriesGoal < male.dailyCaloriesGoal
        )
    }

    @Test
    fun calculateTdee_allActivityLevels_increasesMonotonically() {
        val levels = listOf("Sedentary", "Light", "Moderate", "Active", "Very Active", "Extra Active")
        val calories = levels.map { level ->
            CalculatorUtils.calculateUserMetrics(baseUser(activityLevel = level)).dailyCaloriesGoal
        }
        // cada nível deve ter mais calorias que o anterior
        for (i in 1 until calories.size) {
            assertTrue(
                "${levels[i]} (${calories[i]}) deve ser > ${levels[i - 1]} (${calories[i - 1]})",
                calories[i] > calories[i - 1]
            )
        }
    }

    @Test
    fun calculateTdee_unknownActivityLevel_defaultsToSedentary() {
        val sedentary = CalculatorUtils.calculateUserMetrics(baseUser(activityLevel = "Sedentary"))
        val unknown = CalculatorUtils.calculateUserMetrics(baseUser(activityLevel = "INVALID_LEVEL"))
        assertEquals(sedentary.dailyCaloriesGoal, unknown.dailyCaloriesGoal)
    }

    // =========================================================================
    // Task 1.3 — TESTES DE AJUSTE CALÓRICO E SAFETY FLOOR
    // =========================================================================

    @Test
    fun calculateCalories_maintenance_noAdjustment() {
        // TDEE Moderate male 80kg 180cm 25yo = ~2798
        val result = CalculatorUtils.calculateUserMetrics(baseUser(goal = "Maintenance"))
        assertEquals(2798f, result.dailyCaloriesGoal.toFloat(), 5f)
    }

    @Test
    fun calculateCalories_extremeLoss_subtractsOneThousand() {
        val maintenance = CalculatorUtils.calculateUserMetrics(baseUser(goal = "Maintenance"))
        val extremeLoss = CalculatorUtils.calculateUserMetrics(baseUser(goal = "Extreme Loss"))
        assertEquals(
            maintenance.dailyCaloriesGoal.toFloat() - 1000f,
            extremeLoss.dailyCaloriesGoal.toFloat(),
            5f
        )
    }

    @Test
    fun calculateCalories_extremeGain_addsOneThousand() {
        val maintenance = CalculatorUtils.calculateUserMetrics(baseUser(goal = "Maintenance"))
        val extremeGain = CalculatorUtils.calculateUserMetrics(baseUser(goal = "Extreme Gain"))
        assertEquals(
            maintenance.dailyCaloriesGoal.toFloat() + 1000f,
            extremeGain.dailyCaloriesGoal.toFloat(),
            5f
        )
    }

    @Test
    fun calculateCalories_safetyFloor_neverBelow1200() {
        // mulher pequena, sedentária, extreme loss → TDEE muito baixo
        val user = baseUser(
            gender = "FEMALE", age = 50, weight = 45f, height = 150f,
            activityLevel = "Sedentary", goal = "Extreme Loss"
        )
        val result = CalculatorUtils.calculateUserMetrics(user)
        assertTrue(
            "Calorias (${result.dailyCaloriesGoal}) devem ser >= 1200",
            result.dailyCaloriesGoal >= 1200
        )
        assertEquals(1200, result.dailyCaloriesGoal)
    }

    @Test
    fun calculateCalories_allGoalsSortedCorrectly() {
        val goals = listOf(
            "Extreme Loss", "Weight Loss", "Mild Weight Loss",
            "Maintenance",
            "Mild Weight Gain", "Weight Gain", "Extreme Gain"
        )
        // usar um user cujo extreme loss NÃO ativa o floor (para que a ordem seja pura)
        val user = baseUser(activityLevel = "Extra Active")
        val calories = goals.map { goal ->
            CalculatorUtils.calculateUserMetrics(user.copy(goal = goal)).dailyCaloriesGoal
        }
        for (i in 1 until calories.size) {
            assertTrue(
                "${goals[i]} (${calories[i]}) deve ser >= ${goals[i - 1]} (${calories[i - 1]})",
                calories[i] >= calories[i - 1]
            )
        }
    }

    // =========================================================================
    // Task 1.4 — TESTES DE MACROS
    // =========================================================================

    @Test
    fun calculateMacros_standardSplit_sumsToTotalCalories() {
        val result = CalculatorUtils.calculateUserMetrics(baseUser())
        // Proteína×4 + Carbs×4 + Fat×9 deve ≈ calorias totais
        val macroCalories = (result.proteinGoal * 4) + (result.carbsGoal * 4) + (result.fatGoal * 9)
        assertEquals(
            result.dailyCaloriesGoal.toFloat(),
            macroCalories.toFloat(),
            20f // margem por arredondamento de gramas inteiras
        )
    }

    @Test
    fun calculateMacros_proteinIs30Percent() {
        val result = CalculatorUtils.calculateUserMetrics(baseUser())
        // proteína em gramas = (calorias × 0.30) / 4
        val expectedProtein = (result.dailyCaloriesGoal * 0.30f / 4f).toInt()
        assertEquals(expectedProtein.toFloat(), result.proteinGoal.toFloat(), 1f)
    }

    @Test
    fun calculateMacros_carbsIs45Percent() {
        val result = CalculatorUtils.calculateUserMetrics(baseUser())
        val expectedCarbs = (result.dailyCaloriesGoal * 0.45f / 4f).toInt()
        assertEquals(expectedCarbs.toFloat(), result.carbsGoal.toFloat(), 1f)
    }

    @Test
    fun calculateMacros_fatIs25Percent() {
        val result = CalculatorUtils.calculateUserMetrics(baseUser())
        val expectedFat = (result.dailyCaloriesGoal * 0.25f / 9f).toInt()
        assertEquals(expectedFat.toFloat(), result.fatGoal.toFloat(), 1f)
    }

    // =========================================================================
    // Task 1.5 — TESTES DE CUSTOM TARGETS
    // =========================================================================

    @Test
    fun customTargets_doesNotRecalculateCaloriesOrMacros() {
        val user = baseUser(
            isCustomTargets = true,
            dailyCaloriesGoal = 3000,
            proteinGoal = 200,
            carbsGoal = 300,
            fatGoal = 80
        )
        val result = CalculatorUtils.calculateUserMetrics(user)

        assertEquals(3000, result.dailyCaloriesGoal)
        assertEquals(200, result.proteinGoal)
        assertEquals(300, result.carbsGoal)
        assertEquals(80, result.fatGoal)
    }

    @Test
    fun customTargets_stillCalculatesBmi() {
        val user = baseUser(
            isCustomTargets = true,
            dailyCaloriesGoal = 3000,
            proteinGoal = 200,
            carbsGoal = 300,
            fatGoal = 80
        )
        val result = CalculatorUtils.calculateUserMetrics(user)

        assertTrue("BMI (${result.bmi}) deve ser > 0", result.bmi > 0f)
        assertEquals(24.7f, result.bmi, 0.1f)
    }
}
