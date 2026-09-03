package com.example.baselift.fakes

import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.repository.INutritionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class FakeNutritionRepository : INutritionRepository {
    private val nutritionLogs = MutableStateFlow<List<NutritionLogEntity>>(emptyList())
    private val mealTemplates = MutableStateFlow<List<MealTemplateEntity>>(emptyList())

    private var nextLogId = 1
    private var nextTemplateId = 1

    override suspend fun insertNutritionLog(log: NutritionLogEntity) {
        val currentLogs = nutritionLogs.value.toMutableList()
        // Simulate REPLACE strategy: if id > 0 and exists, replace it
        if (log.id > 0) {
            val index = currentLogs.indexOfFirst { it.id == log.id }
            if (index != -1) {
                currentLogs[index] = log
                nutritionLogs.value = currentLogs
                return
            }
        }
        val newLog = log.copy(id = if (log.id > 0) log.id else nextLogId++)
        currentLogs.add(newLog)
        nutritionLogs.value = currentLogs
    }

    override suspend fun deleteNutritionLog(log: NutritionLogEntity) {
        nutritionLogs.value = nutritionLogs.value.filter { it.id != log.id }
    }

    override suspend fun resetTodayLogs() {
        val startOfDay = getStartOfDayTimestamp()
        val endOfDay = getEndOfDayTimestamp()
        nutritionLogs.value = nutritionLogs.value.filterNot { 
            it.timestamp in startOfDay..endOfDay 
        }
    }

    override fun getTodayLogs(): Flow<List<NutritionLogEntity>> {
        val startOfDay = getStartOfDayTimestamp()
        val endOfDay = getEndOfDayTimestamp()
        return nutritionLogs.map { logs ->
            logs.filter { it.timestamp in startOfDay..endOfDay }
                .sortedByDescending { it.timestamp }
        }
    }

    override suspend fun insertMealTemplate(template: MealTemplateEntity) {
        val currentTemplates = mealTemplates.value.toMutableList()
        if (template.id > 0) {
            val index = currentTemplates.indexOfFirst { it.id == template.id }
            if (index != -1) {
                currentTemplates[index] = template
                mealTemplates.value = currentTemplates
                return
            }
        }
        val newTemplate = template.copy(id = if (template.id > 0) template.id else nextTemplateId++)
        currentTemplates.add(newTemplate)
        mealTemplates.value = currentTemplates
    }

    override suspend fun deleteMealTemplate(template: MealTemplateEntity) {
        mealTemplates.value = mealTemplates.value.filter { it.id != template.id }
    }

    override fun getAllMealTemplates(): Flow<List<MealTemplateEntity>> {
        return mealTemplates
    }

    override fun getAllNutritionLogs(): Flow<List<NutritionLogEntity>> {
        return nutritionLogs.map { logs -> logs.sortedByDescending { it.timestamp } }
    }

    override suspend fun clearAllNutritionData() {
        nutritionLogs.value = emptyList()
        mealTemplates.value = emptyList()
        nextLogId = 1
        nextTemplateId = 1
    }

    private fun getStartOfDayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfDayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
