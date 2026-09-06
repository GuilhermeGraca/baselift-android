package com.example.baselift.Utils

import kotlin.math.max

object IdealWeightCalculator {

    data class IdealWeightResult(
        val formulaName: String,
        val weightKg: Float,
        val range: Pair<Float, Float>? = null // Used for BMI range
    )

    fun calculateIdealWeights(heightCm: Float, gender: String): List<IdealWeightResult> {
        val heightInches = heightCm / 2.54f
        // Base is 5 feet = 60 inches
        val inchesOver5Feet = max(0f, heightInches - 60f)
        
        // If gender is neither MALE nor FEMALE, we calculate both and average them
        val isMale = gender.equals("MALE", ignoreCase = true)
        val isFemale = gender.equals("FEMALE", ignoreCase = true)
        
        val isOther = !isMale && !isFemale

        // Robinson Formula (1983)
        // Male: 52 kg + 1.9 kg per inch over 5 feet
        // Female: 49 kg + 1.7 kg per inch over 5 feet
        val robinsonM = 52f + (1.9f * inchesOver5Feet)
        val robinsonF = 49f + (1.7f * inchesOver5Feet)
        val robinson = if (isMale) robinsonM else if (isFemale) robinsonF else (robinsonM + robinsonF) / 2f

        // Miller Formula (1983)
        // Male: 56.2 kg + 1.41 kg per inch over 5 feet
        // Female: 53.1 kg + 1.36 kg per inch over 5 feet
        val millerM = 56.2f + (1.41f * inchesOver5Feet)
        val millerF = 53.1f + (1.36f * inchesOver5Feet)
        val miller = if (isMale) millerM else if (isFemale) millerF else (millerM + millerF) / 2f

        // Devine Formula (1974)
        // Male: 50.0 kg + 2.3 kg per inch over 5 feet
        // Female: 45.5 kg + 2.3 kg per inch over 5 feet
        val devineM = 50.0f + (2.3f * inchesOver5Feet)
        val devineF = 45.5f + (2.3f * inchesOver5Feet)
        val devine = if (isMale) devineM else if (isFemale) devineF else (devineM + devineF) / 2f

        // Hamwi Formula (1964)
        // Male: 48.0 kg + 2.7 kg per inch over 5 feet
        // Female: 45.5 kg + 2.2 kg per inch over 5 feet
        val hamwiM = 48.0f + (2.7f * inchesOver5Feet)
        val hamwiF = 45.5f + (2.2f * inchesOver5Feet)
        val hamwi = if (isMale) hamwiM else if (isFemale) hamwiF else (hamwiM + hamwiF) / 2f

        // Healthy BMI Range (18.5 - 25)
        // weight = BMI * (height in meters)^2
        val heightM = heightCm / 100f
        val heightMSquared = heightM * heightM
        val healthyMin = 18.5f * heightMSquared
        val healthyMax = 25.0f * heightMSquared

        return listOf(
            IdealWeightResult("Robinson (1983)", robinson),
            IdealWeightResult("Miller (1983)", miller),
            IdealWeightResult("Devine (1974)", devine),
            IdealWeightResult("Hamwi (1964)", hamwi),
            IdealWeightResult("Healthy BMI Range", 0f, Pair(healthyMin, healthyMax))
        )
    }
}
