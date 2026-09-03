package com.example.baselift.Model.repository

import com.example.baselift.Model.local.dao.PhotoLogDao
import com.example.baselift.Model.local.dao.WeightLogDao
import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.Model.local.entity.WeightLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface do repositório de progresso.
 * Permite criar implementações fake para testes.
 */
interface IProgressRepository {
    val allWeightLogs: Flow<List<WeightLogEntity>>
    val allPhotoLogs: Flow<List<PhotoLogEntity>>
    suspend fun insertWeightLog(weight: Float, timestamp: Long)
    suspend fun insertPhotoLog(photoUri: String, timestamp: Long)
    suspend fun deleteWeightLog(weightLog: WeightLogEntity)
    suspend fun deletePhotoLog(photoLog: PhotoLogEntity)
    suspend fun clearProgressData()
}

/**
 * Implementação real que delega para os DAOs de peso e foto.
 */
class ProgressRepository(
    private val weightLogDao: WeightLogDao,
    private val photoLogDao: PhotoLogDao
) : IProgressRepository {
    override val allWeightLogs: Flow<List<WeightLogEntity>> = weightLogDao.getAllWeightLogs()
    override val allPhotoLogs: Flow<List<PhotoLogEntity>> = photoLogDao.getAllPhotoLogsDescending()
    
    override suspend fun insertWeightLog(weight: Float, timestamp: Long) {
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = weight, timestamp = timestamp))
    }
    
    override suspend fun insertPhotoLog(photoUri: String, timestamp: Long) {
        photoLogDao.insertPhotoLog(PhotoLogEntity(photoUri = photoUri, timestamp = timestamp))
    }

    override suspend fun deleteWeightLog(weightLog: WeightLogEntity) {
        weightLogDao.deleteWeightLog(weightLog)
    }

    override suspend fun deletePhotoLog(photoLog: PhotoLogEntity) {
        photoLogDao.deletePhotoLog(photoLog)
    }

    override suspend fun clearProgressData() {
        weightLogDao.clearWeightLogsTable()
        photoLogDao.clearPhotoLogsTable()
    }
}

