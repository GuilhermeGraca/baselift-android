package com.example.baselift.Model.nutrition

data class NutritionValues(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int
)

/** Converts integer label values to the integer totals stored in a nutrition log. */
object NutritionPortionCalculator {
    fun calculate(values: NutritionValues, per100g: Boolean, grams: Int?): NutritionValues? {
        if (values.calories < 0 || values.protein < 0 || values.carbs < 0 || values.fats < 0) {
            return null
        }
        if (per100g && (grams == null || grams <= 0)) return null

        fun scale(value: Int): Int? {
            if (!per100g) return value
            // Int × Int fits in Long. Add half the divisor before dividing to round .5 up.
            val total = (value.toLong() * grams!!.toLong() + 50L) / 100L
            return total.takeIf { it <= Int.MAX_VALUE }?.toInt()
        }

        val result = NutritionValues(
            calories = scale(values.calories) ?: return null,
            protein = scale(values.protein) ?: return null,
            carbs = scale(values.carbs) ?: return null,
            fats = scale(values.fats) ?: return null
        )
        return result.takeIf {
            it.calories > 0 || it.protein > 0 || it.carbs > 0 || it.fats > 0
        }
    }
}

/** Empty nutrition fields mean zero; all other input must be an unsigned whole number. */
fun parseNutritionWholeNumber(text: String): Int? = when {
    text.isEmpty() -> 0
    text.all { it in '0'..'9' } -> text.toIntOrNull()
    else -> null
}
