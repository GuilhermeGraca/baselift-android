package com.example.baselift.Model.repository

import com.example.baselift.Model.local.dao.NutritionDao
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * Interface do repositório de nutrição.
 * Permite criar implementações fake para testes.
 */
interface INutritionRepository {
    suspend fun insertNutritionLog(log: NutritionLogEntity)
    suspend fun deleteNutritionLog(log: NutritionLogEntity)
    suspend fun resetTodayLogs()
    fun getTodayLogs(): Flow<List<NutritionLogEntity>>
    suspend fun insertMealTemplate(template: MealTemplateEntity)
    suspend fun deleteMealTemplate(template: MealTemplateEntity)
    fun getAllMealTemplates(): Flow<List<MealTemplateEntity>>
    fun getAllNutritionLogs(): Flow<List<NutritionLogEntity>>
    suspend fun clearAllNutritionData()
}

/**
 * Implementação real que delega para o NutritionDao (Room).
 */
class NutritionRepository(private val nutritionDao: NutritionDao) : INutritionRepository {

    // --- LOGS DIÁRIOS ---

    override suspend fun insertNutritionLog(log: NutritionLogEntity) {
        nutritionDao.insertNutritionLog(log)
    }

    override suspend fun deleteNutritionLog(log: NutritionLogEntity) {
        nutritionDao.deleteNutritionLog(log)
    }

    override suspend fun resetTodayLogs() {
        val startOfDay = getStartOfDayTimestamp()
        val endOfDay = getEndOfDayTimestamp()
        nutritionDao.deleteLogsInTimeRange(startOfDay, endOfDay)
    }

    override fun getTodayLogs(): Flow<List<NutritionLogEntity>> {
        val startOfDay = getStartOfDayTimestamp()
        val endOfDay = getEndOfDayTimestamp()
        return nutritionDao.getLogsForTimeRange(startOfDay, endOfDay)
    }

    // --- TEMPLATES DE REFEIÇÕES ---

    override suspend fun insertMealTemplate(template: MealTemplateEntity) {
        nutritionDao.insertMealTemplate(template)
    }

    override suspend fun deleteMealTemplate(template: MealTemplateEntity) {
        nutritionDao.deleteMealTemplate(template)
    }

    override fun getAllMealTemplates(): Flow<List<MealTemplateEntity>> {
        return nutritionDao.getAllMealTemplates()
    }

    // --- DASHBOARD ---

    override fun getAllNutritionLogs() = nutritionDao.getAllNutritionLogs()

    // funções auxiliares para calcular limites do dia atual
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

    override suspend fun clearAllNutritionData() {
        nutritionDao.clearNutritionLogsTable()
        nutritionDao.clearMealTemplatesTable()
    }
}

