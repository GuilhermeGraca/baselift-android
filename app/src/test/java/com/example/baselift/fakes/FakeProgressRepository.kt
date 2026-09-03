package com.example.baselift.fakes

import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.Model.repository.IProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeProgressRepository : IProgressRepository {
    private val weightLogs = MutableStateFlow<List<WeightLogEntity>>(emptyList())
    private val photoLogs = MutableStateFlow<List<PhotoLogEntity>>(emptyList())
    
    // Auto-increment ID simulation
    private var nextWeightId = 1
    private var nextPhotoId = 1

    override val allWeightLogs: Flow<List<WeightLogEntity>> = weightLogs.map { list ->
        list.sortedByDescending { it.timestamp }
    }
    
    override val allPhotoLogs: Flow<List<PhotoLogEntity>> = photoLogs.map { list ->
        list.sortedByDescending { it.timestamp }
    }

    override suspend fun insertWeightLog(weight: Float, timestamp: Long) {
        val currentList = weightLogs.value.toMutableList()
        currentList.add(WeightLogEntity(id = nextWeightId++, weightValue = weight, timestamp = timestamp))
        weightLogs.value = currentList
    }

    override suspend fun insertPhotoLog(photoUri: String, timestamp: Long) {
        val currentList = photoLogs.value.toMutableList()
        currentList.add(PhotoLogEntity(id = nextPhotoId++, photoUri = photoUri, timestamp = timestamp))
        photoLogs.value = currentList
    }

    override suspend fun deleteWeightLog(weightLog: WeightLogEntity) {
        weightLogs.value = weightLogs.value.filter { it.id != weightLog.id }
    }

    override suspend fun deletePhotoLog(photoLog: PhotoLogEntity) {
        photoLogs.value = photoLogs.value.filter { it.id != photoLog.id }
    }

    override suspend fun clearProgressData() {
        weightLogs.value = emptyList()
        photoLogs.value = emptyList()
        nextWeightId = 1
        nextPhotoId = 1
    }
}
