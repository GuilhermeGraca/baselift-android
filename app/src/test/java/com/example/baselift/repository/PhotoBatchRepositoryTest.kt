package com.example.baselift.repository

import com.example.baselift.Model.local.dao.PhotoLogDao
import com.example.baselift.Model.local.dao.WeightLogDao
import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.Model.repository.ProgressRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PhotoBatchRepositoryTest {
    private val photos = mockk<PhotoLogDao>(relaxed = true)
    private val repository = ProgressRepository(mockk<WeightLogDao>(relaxed = true), photos)
    @Test fun `one ordered collection insert preserves duplicates date and generated IDs`() = runTest {
        repository.insertPhotoLogs(listOf("first", "second", "first"), 1234L)
        coVerify(exactly = 1) { photos.insertPhotoLogs(listOf(
            PhotoLogEntity(photoUri = "first", timestamp = 1234L),
            PhotoLogEntity(photoUri = "second", timestamp = 1234L),
            PhotoLogEntity(photoUri = "first", timestamp = 1234L))) }
        coVerify(exactly = 0) { photos.insertPhotoLog(any()) }
    }
    @Test fun `empty batch does not start a database transaction`() = runTest {
        repository.insertPhotoLogs(emptyList(), 1234L)
        coVerify(exactly = 0) { photos.insertPhotoLogs(any()) }
    }
}
