package com.example.baselift.Model.nutrition

import java.math.BigDecimal
import java.math.RoundingMode

data class NutritionValues(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fats: Double
) {
    fun isValid(): Boolean = calories >= 0 &&
        listOf(protein, carbs, fats).all(::isValidNutritionDecimal)

    fun isEmpty(): Boolean = calories == 0 && protein == 0.0 && carbs == 0.0 && fats == 0.0
}

/** Preview and persistence share one conversion: whole kcal and macros rounded once to 0.01 g. */
object NutritionPortionCalculator {
    fun calculate(values: NutritionValues, per100g: Boolean, grams: Double?): NutritionValues? {
        if (!values.isValid()) return null
        if (per100g && (grams == null || !isValidNutritionDecimal(grams) || grams <= 0)) return null
        val factor = if (per100g) BigDecimal.valueOf(grams!!).movePointLeft(2) else BigDecimal.ONE

        fun scale(value: Double, decimals: Int): BigDecimal? {
            val total = BigDecimal.valueOf(value).multiply(factor).setScale(decimals, RoundingMode.HALF_UP)
            return total.takeIf { it <= BigDecimal.valueOf(Int.MAX_VALUE.toLong()) }
        }

        val result = NutritionValues(
            calories = scale(values.calories.toDouble(), 0)?.toInt() ?: return null,
            protein = scale(values.protein, 2)?.toDouble() ?: return null,
            carbs = scale(values.carbs, 2)?.toDouble() ?: return null,
            fats = scale(values.fats, 2)?.toDouble() ?: return null
        )
        return result.takeUnless { it.isEmpty() }
    }
}

private val decimalInput = Regex("(?:[0-9]+(?:[.,][0-9]{0,2})?|[.,][0-9]{1,2})")

fun isValidNutritionDecimal(value: Double): Boolean = value.isFinite() && value in 0.0..Int.MAX_VALUE.toDouble()

/** Accept either decimal separator, without grouping, signs or exponent notation. Empty means zero. */
fun parseNutritionDecimal(text: String): Double? = when {
    text.isEmpty() -> 0.0
    !decimalInput.matches(text) -> null
    else -> text.replace(',', '.').toDoubleOrNull()?.takeIf(::isValidNutritionDecimal)
}

/** Keep whole values compact and hide binary floating point artefacts in sums and +/- edits. */
fun formatNutritionDecimal(value: Double): String =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Explicit recalculation only; manually entered label calories remain independent of macros. */
fun caloriesFromMacros(protein: Double, carbs: Double, fats: Double): Int? {
    if (!listOf(protein, carbs, fats).all(::isValidNutritionDecimal)) return null
    val total = BigDecimal.valueOf(protein).add(BigDecimal.valueOf(carbs)).multiply(BigDecimal.valueOf(4))
        .add(BigDecimal.valueOf(fats).multiply(BigDecimal.valueOf(9))).setScale(0, RoundingMode.HALF_UP)
    return total.takeIf { it <= BigDecimal.valueOf(Int.MAX_VALUE.toLong()) }?.toInt()
}

/** Empty nutrition fields mean zero; all other input must be an unsigned whole number. */
fun parseNutritionWholeNumber(text: String): Int? = when {
    text.isEmpty() -> 0
    text.all { it in '0'..'9' } -> text.toIntOrNull()
    else -> null
}
