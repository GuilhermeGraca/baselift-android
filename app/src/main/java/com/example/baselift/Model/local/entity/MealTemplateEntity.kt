package com.example.baselift.Model.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

// entidade para os templates de refeições configuradas
@Entity(tableName = "meal_templates")
data class MealTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val iconName: String, // nome do ícone para apresentar na interface
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int,
    @ColumnInfo(defaultValue = "0")
    val isPer100g: Boolean = false
)
