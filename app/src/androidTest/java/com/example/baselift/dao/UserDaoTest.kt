package com.example.baselift.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.dao.UserDao
import com.example.baselift.Model.local.entity.UserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes instrumentados para o UserDao.
 *
 * Usa uma base de dados in-memory que existe apenas na RAM
 * e é destruída após cada teste, garantindo isolamento total.
 */
@RunWith(AndroidJUnit4::class)
class UserDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var userDao: UserDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        userDao = database.userDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // =========================================================================
    // TESTES
    // =========================================================================

    @Test
    fun insertUser_thenGetUser_returnsCorrectData() = runTest {
        val user = UserEntity(
            id = 1, gender = "MALE", age = 25, weight = 80f, height = 180f,
            preferredWeightUnit = "KG", preferredHeightUnit = "CM",
            activityLevel = "Moderate", goal = "Maintenance"
        )

        userDao.insertOrUpdateUser(user)
        val result = userDao.getUser().first()

        assertNotNull(result)
        assertEquals("MALE", result?.gender)
        assertEquals(25, result?.age)
        assertEquals(80f, result?.weight)
        assertEquals(180f, result?.height)
        assertEquals("Moderate", result?.activityLevel)
    }

    @Test
    fun updateUser_upsert_overwritesExistingData() = runTest {
        val original = UserEntity(id = 1, gender = "MALE", age = 25, weight = 80f)
        userDao.insertOrUpdateUser(original)

        val updated = original.copy(weight = 85f, goal = "Weight Gain")
        userDao.insertOrUpdateUser(updated)

        val result = userDao.getUser().first()

        assertEquals(85f, result?.weight)
        assertEquals("Weight Gain", result?.goal)
    }

    @Test
    fun clearUserTable_afterInsert_returnsNull() = runTest {
        userDao.insertOrUpdateUser(UserEntity(id = 1, weight = 80f))
        userDao.clearUserTable()

        val result = userDao.getUser().first()

        assertNull(result)
    }

    @Test
    fun getUser_emptyDatabase_returnsNull() = runTest {
        val result = userDao.getUser().first()
        assertNull(result)
    }
}
