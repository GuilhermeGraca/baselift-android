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

class ProgressViewModel(
    private val progressRepository: IProgressRepository,
    private val userRepository: IUserRepository,
    private val photoIoDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private var profilePhotoRequest: Any = Any()

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

    fun addPhotoLog(context: Context, uri: Uri) {
        val applicationContext = context.applicationContext
        viewModelScope.launch {
            try {
                retainPhotoReadPermission(applicationContext, uri)
                progressRepository.insertPhotoLog(uri.toString(), System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addPhotoLogs(context: Context, uris: List<Uri>, timestamp: Long) {
        val applicationContext = context.applicationContext
        val selectedUris = uris.toList()
        viewModelScope.launch {
            selectedUris.forEach { uri ->
                currentCoroutineContext().ensureActive()
                try {
                    retainPhotoReadPermission(applicationContext, uri)
                    progressRepository.insertPhotoLog(uri.toString(), timestamp)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
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
