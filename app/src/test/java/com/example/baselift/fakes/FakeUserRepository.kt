package com.example.baselift.fakes

import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.repository.IUserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserRepository : IUserRepository {
    private val userFlow = MutableStateFlow<UserEntity?>(null)

    override fun getUser(): Flow<UserEntity?> {
        return userFlow
    }

    override suspend fun saveUser(user: UserEntity) {
        userFlow.value = user
    }

    override suspend fun clearUserData() {
        userFlow.value = null
    }
}
