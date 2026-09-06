package com.example.baselift.Model.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val orderIndex: Int = 0,
    val defaultRestTimer: Int = 105 // Tempo de descanso padrão (1:45)
)
