package com.example.baselift.ViewModel.progress

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.Model.repository.IProgressRepository
import com.example.baselift.Model.repository.IUserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PhotoImportResult(val id: Long, val savedCount: Int, val failedCount: Int, val wasCancelled: Boolean = false)
data class PhotoImportState(val isImporting: Boolean = false, val result: PhotoImportResult? = null)

class ProgressViewModel(
    private val progressRepository: IProgressRepository,
    private val userRepository: IUserRepository,
    private val photoIoDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private var profilePhotoRequest: Any = Any()
    private val _photoImport = MutableStateFlow(PhotoImportState())
    val photoImport: StateFlow<PhotoImportState> = _photoImport.asStateFlow()
    private data class PhotoImportRetry(val resultId: Long, val uris: List<Uri>, val timestamp: Long?)
    private var photoImportRetry: PhotoImportRetry? = null
    private var nextPhotoImportId = 1L

    private suspend fun retainPhotoReadPermission(context: Context, uri: Uri) {
        withContext(photoIoDispatcher) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    val user: StateFlow<UserEntity?> = userRepository.getUser()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Keep the last snapshots for return navigation; inactive Insights stops history queries.
    val weightLogs: StateFlow<List<WeightLogEntity>> = progressRepository.allWeightLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_000), emptyList())

    val photoLogs: StateFlow<List<PhotoLogEntity>> = progressRepository.allPhotoLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_000), emptyList())

    fun addWeightLog(weight: Float, timestamp: Long) {
        viewModelScope.launch {
            progressRepository.insertWeightLog(weight, timestamp)
            // atualizar o peso atual na entidade de utilizador se a data for de hoje
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val todayEnd = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }.timeInMillis

            if (timestamp in todayStart..todayEnd) {
                val user = userRepository.getUser().firstOrNull()
                if (user != null) {
                    val userWithNewWeight = user.copy(weight = weight)
                    val recalculatedUser = com.example.baselift.Utils.CalculatorUtils.calculateUserMetrics(userWithNewWeight)
                    userRepository.saveUser(recalculatedUser)
                }
            }
        }
    }

    fun addPhotoLog(context: Context, uri: Uri): Boolean = startPhotoImport(context, listOf(uri), null)

    fun addPhotoLogs(context: Context, uris: List<Uri>, timestamp: Long): Boolean =
        startPhotoImport(context, uris, timestamp)

    fun retryPhotoImport(context: Context, resultId: Long) {
        val retry = photoImportRetry ?: return
        if (retry.resultId == resultId) startPhotoImport(context, retry.uris, retry.timestamp)
    }

    fun dismissPhotoImportResult(resultId: Long) {
        if (_photoImport.value.result?.id != resultId) return
        photoImportRetry = null
        _photoImport.update { it.copy(result = null) }
    }

    private fun startPhotoImport(context: Context, uris: List<Uri>, timestamp: Long?): Boolean {
        if (uris.isEmpty() || _photoImport.value.isImporting) return false
        val applicationContext = context.applicationContext
        val selectedUris = uris.toList()
        val requestId = nextPhotoImportId++
        photoImportRetry = null
        _photoImport.value = PhotoImportState(isImporting = true)
        viewModelScope.launch {
            val readable = mutableListOf<Uri>()
            val rejected = mutableListOf<Uri>()
            fun finish(saved: Int, failed: List<Uri>, cancelled: Boolean = false) {
                photoImportRetry = failed.takeIf { it.isNotEmpty() }?.let { PhotoImportRetry(requestId, it.toList(), timestamp) }
                _photoImport.value = PhotoImportState(result = PhotoImportResult(requestId, saved, failed.size, cancelled))
            }
            try {
                for (uri in selectedUris) {
                    currentCoroutineContext().ensureActive()
                    try {
                        retainPhotoReadPermission(applicationContext, uri)
                        readable += uri
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        rejected += uri
                    }
                }
                currentCoroutineContext().ensureActive()
                // Once commit begins, finish this finite atomic write and record its outcome even
                // if the ViewModel is disposed. Cancellation before this point writes no rows.
                withContext(NonCancellable) {
                    if (readable.isNotEmpty()) {
                        progressRepository.insertPhotoLogs(readable.map { it.toString() }, timestamp ?: System.currentTimeMillis())
                    }
                    finish(readable.size, rejected)
                }
            } catch (cancelled: CancellationException) {
                finish(0, selectedUris, cancelled = true)
                throw cancelled
            } catch (_: Exception) {
                // The repository's batch contract rolls back every eligible row on write failure.
                finish(0, selectedUris)
            }
        }
        return true
    }

    fun setTargetWeight(target: Float?) {
        viewModelScope.launch {
            val currentUser = userRepository.getUser().firstOrNull()
            if (currentUser != null) {
                userRepository.saveUser(currentUser.copy(targetWeight = target))
            }
        }
    }

    fun deleteWeightLog(weightLog: WeightLogEntity) {
        viewModelScope.launch {
            progressRepository.deleteWeightLog(weightLog)
        }
    }

    fun deletePhotoLog(photoLog: PhotoLogEntity) {
        viewModelScope.launch {
            progressRepository.deletePhotoLog(photoLog)
        }
    }

    fun updateProfileName(name: String?) {
        viewModelScope.launch {
            val currentUser = userRepository.getUser().firstOrNull()
            if (currentUser != null) {
                userRepository.saveUser(currentUser.copy(name = name))
            }
        }
    }

    fun updateProfilePhoto(context: Context, uri: Uri?) {
        val applicationContext = context.applicationContext
        val request = Any()
        profilePhotoRequest = request
        viewModelScope.launch {
            if (profilePhotoRequest !== request) return@launch
            if (uri != null) {
                try {
                    retainPhotoReadPermission(applicationContext, uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    // Preserve the existing best-effort profile permission policy.
                    e.printStackTrace()
                }
            }
            currentCoroutineContext().ensureActive()
            if (profilePhotoRequest !== request) return@launch
            // Provider work may be slow. Read current profile fields after it completes.
            val currentUser = userRepository.getUser().firstOrNull() ?: return@launch
            if (profilePhotoRequest !== request) return@launch
            userRepository.saveUser(currentUser.copy(profilePhotoUri = uri?.toString()))
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            progressRepository.clearProgressData()
            userRepository.clearUserData()
        }
    }

    // Nota: Estas funções delegam aos respetivos repositórios que já devem estar no construtor
    // Como ProgressViewModel não tem WorkoutRepository/NutritionRepository, o mais fácil
    // é delegar isto no DashboardViewModel ou passar uma flag na UI para quem tem acesso.
}

class ProgressViewModelFactory(
    private val progressRepository: IProgressRepository,
    private val userRepository: IUserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProgressViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProgressViewModel(progressRepository, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
