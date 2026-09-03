package com.example.baselift.Model.repository

import com.example.baselift.Model.local.dao.UserDao
import com.example.baselift.Model.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface do repositório de utilizador.
 * Permite criar implementações fake para testes.
 */
interface IUserRepository {
    fun getUser(): Flow<UserEntity?>
    suspend fun saveUser(user: UserEntity)
    suspend fun clearUserData()
}

/**
 * Implementação real que delega para o UserDao (Room).
 */
class UserRepository(private val userDao: UserDao) : IUserRepository {

    override fun getUser(): Flow<UserEntity?> {
        return userDao.getUser()
    }

    override suspend fun saveUser(user: UserEntity) {
        userDao.insertOrUpdateUser(user)
    }

    override suspend fun clearUserData() {
        userDao.clearUserTable()
    }
}

