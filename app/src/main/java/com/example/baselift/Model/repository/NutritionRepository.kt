package com.example.baselift.Model.repository

import com.example.baselift.Model.local.dao.NutritionDao
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.entity.NutritionLogEntity
import kotlinx.coroutines.flow.Flow
import java.util.TimeZone

/**
 * Interface do repositório de nutrição.
 * Permite criar implementações fake para testes.
 */
interface INutritionRepository {
    suspend fun insertNutritionLog(log: NutritionLogEntity)
    suspend fun deleteNutritionLog(log: NutritionLogEntity)
    suspend fun resetTodayLogs()
    fun getLogsForDay(day: NutritionDay): Flow<List<NutritionLogEntity>>
    suspend fun insertMealTemplate(template: MealTemplateEntity)
    suspend fun deleteMealTemplate(template: MealTemplateEntity)
    fun getAllMealTemplates(): Flow<List<MealTemplateEntity>>
    fun getAllNutritionLogs(): Flow<List<NutritionLogEntity>>
    suspend fun clearAllNutritionData()
}

/**
 * Implementação real que delega para o NutritionDao (Room).
 */
class NutritionRepository(
    private val nutritionDao: NutritionDao,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault
) : INutritionRepository {

    // --- LOGS DIÁRIOS ---

    override suspend fun insertNutritionLog(log: NutritionLogEntity) {
        nutritionDao.insertNutritionLog(log)
    }

    override suspend fun deleteNutritionLog(log: NutritionLogEntity) {
        nutritionDao.deleteNutritionLog(log)
    }

    override suspend fun resetTodayLogs() {
        val day = NutritionDay.at(clockMillis(), timeZone())
        nutritionDao.deleteLogsInTimeRange(day.startMillis, day.endExclusiveMillis - 1)
    }

    override fun getLogsForDay(day: NutritionDay): Flow<List<NutritionLogEntity>> {
        return nutritionDao.getLogsForTimeRange(day.startMillis, day.endExclusiveMillis - 1)
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

    override suspend fun clearAllNutritionData() {
        nutritionDao.clearNutritionLogsTable()
        nutritionDao.clearMealTemplatesTable()
    }
}

