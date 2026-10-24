package com.example.baselift.Model.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Index

// entidade para cada entrada de calorias e macros
@Entity(
    tableName = "nutrition_logs",
    indices = [Index(value = ["timestamp"])]
)
data class NutritionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String? = null, // opcional para quando é apenas calorias ou macros detalhados sem nome
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val timestamp: Long // data em que o registo foi inserido
)
