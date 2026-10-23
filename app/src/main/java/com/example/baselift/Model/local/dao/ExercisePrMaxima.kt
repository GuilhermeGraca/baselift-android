package com.example.baselift.Model.local.dao

// Query projection, not a persisted entity. Both maxima come from one history snapshot.
data class ExercisePrMaxima(val maxWeight: Float?, val max1RM: Float?)
