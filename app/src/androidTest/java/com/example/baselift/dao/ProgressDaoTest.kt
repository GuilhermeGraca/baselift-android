package com.example.baselift.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.dao.PhotoLogDao
import com.example.baselift.Model.local.dao.WeightLogDao
import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.Model.local.entity.WeightLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes instrumentados para o WeightLogDao e PhotoLogDao.
 *
 * Verifica as operações CRUD e a ordenação dos logs de progresso.
 */
@RunWith(AndroidJUnit4::class)
class ProgressDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var weightLogDao: WeightLogDao
    private lateinit var photoLogDao: PhotoLogDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        weightLogDao = database.weightLogDao()
        photoLogDao = database.photoLogDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // =========================================================================
    // TESTES DE WEIGHT LOG
    // =========================================================================

    @Test
    fun insertWeightLog_appearsInGetAll() = runTest {
        val log = WeightLogEntity(weightValue = 80.5f, timestamp = 1000L)
        weightLogDao.insertWeightLog(log)

        val results = weightLogDao.getAllWeightLogs().first()

        assertEquals(1, results.size)
        assertEquals(80.5f, results[0].weightValue, 0.01f)
    }

    @Test
    fun getAllWeightLogs_orderedByTimestampAsc() = runTest {
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = 82f, timestamp = 3000L))
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = 80f, timestamp = 1000L))
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = 81f, timestamp = 2000L))

        val results = weightLogDao.getAllWeightLogs().first()

        assertEquals(80f, results[0].weightValue, 0.01f) // timestamp 1000
        assertEquals(81f, results[1].weightValue, 0.01f) // timestamp 2000
        assertEquals(82f, results[2].weightValue, 0.01f) // timestamp 3000
    }

    @Test
    fun getLatestWeightLog_returnsNewest() = runTest {
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = 80f, timestamp = 1000L))
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = 85f, timestamp = 3000L))
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = 82f, timestamp = 2000L))

        val latest = weightLogDao.getLatestWeightLog().first()

        assertNotNull(latest)
        assertEquals(85f, latest!!.weightValue, 0.01f) // timestamp DESC LIMIT 1
    }

    @Test
    fun deleteWeightLog_removesSpecificEntry() = runTest {
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = 80f, timestamp = 1000L))

        val inserted = weightLogDao.getAllWeightLogs().first()[0]
        weightLogDao.deleteWeightLog(inserted)

        val remaining = weightLogDao.getAllWeightLogs().first()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun clearWeightLogsTable_removesAll() = runTest {
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = 80f, timestamp = 1000L))
        weightLogDao.insertWeightLog(WeightLogEntity(weightValue = 82f, timestamp = 2000L))

        weightLogDao.clearWeightLogsTable()

        val remaining = weightLogDao.getAllWeightLogs().first()
        assertTrue(remaining.isEmpty())
    }

    // =========================================================================
    // TESTES DE PHOTO LOG
    // =========================================================================

    @Test
    fun insertPhotoLog_appearsInGetAll() = runTest {
        val log = PhotoLogEntity(photoUri = "content://photos/1", timestamp = 1000L)
        photoLogDao.insertPhotoLog(log)

        val results = photoLogDao.getAllPhotoLogsDescending().first()

        assertEquals(1, results.size)
        assertEquals("content://photos/1", results[0].photoUri)
    }

    @Test
    fun getAllPhotoLogs_orderedByTimestampDesc() = runTest {
        photoLogDao.insertPhotoLog(PhotoLogEntity(photoUri = "old", timestamp = 1000L))
        photoLogDao.insertPhotoLog(PhotoLogEntity(photoUri = "newest", timestamp = 3000L))
        photoLogDao.insertPhotoLog(PhotoLogEntity(photoUri = "middle", timestamp = 2000L))

        val results = photoLogDao.getAllPhotoLogsDescending().first()

        assertEquals("newest", results[0].photoUri)  // timestamp DESC
        assertEquals("middle", results[1].photoUri)
        assertEquals("old", results[2].photoUri)
    }

    @Test
    fun deletePhotoLog_removesSpecificEntry() = runTest {
        photoLogDao.insertPhotoLog(PhotoLogEntity(photoUri = "photo1", timestamp = 1000L))

        val inserted = photoLogDao.getAllPhotoLogsDescending().first()[0]
        photoLogDao.deletePhotoLog(inserted)

        val remaining = photoLogDao.getAllPhotoLogsDescending().first()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun clearPhotoLogsTable_removesAll() = runTest {
        photoLogDao.insertPhotoLog(PhotoLogEntity(photoUri = "a", timestamp = 1000L))
        photoLogDao.insertPhotoLog(PhotoLogEntity(photoUri = "b", timestamp = 2000L))

        photoLogDao.clearPhotoLogsTable()

        val remaining = photoLogDao.getAllPhotoLogsDescending().first()
        assertTrue(remaining.isEmpty())
    }
}
