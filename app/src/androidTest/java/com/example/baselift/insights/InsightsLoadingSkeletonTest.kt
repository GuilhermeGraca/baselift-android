package com.example.baselift.insights

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baselift.Model.local.AppDatabase
import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.Model.repository.IProgressRepository
import com.example.baselift.Model.repository.ProgressRepository
import com.example.baselift.Model.repository.UserRepository
import com.example.baselift.R
import com.example.baselift.View.insights.InsightsScreen
import com.example.baselift.ViewModel.onboarding.OnboardingViewModel
import com.example.baselift.ViewModel.progress.ProgressViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsightsLoadingSkeletonTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var database: AppDatabase? = null

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database?.close()
    }

    @Test fun weightAndPhotoSkeletonsResolveIndependentlyToConfirmedEmptyHistories() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        database = db
        val real = ProgressRepository(db.weightLogDao(), db.photoLogDao())
        val users = UserRepository(db.userDao())
        runBlocking { users.saveUser(UserEntity(name = "Test")) }
        val releaseWeights = CompletableDeferred<Unit>()
        val releasePhotos = CompletableDeferred<Unit>()
        val delayed = object : IProgressRepository by real {
            override val allWeightLogs: Flow<List<WeightLogEntity>> = flow {
                releaseWeights.await()
                emitAll(real.allWeightLogs)
            }
            override val allPhotoLogs: Flow<List<PhotoLogEntity>> = flow {
                releasePhotos.await()
                emitAll(real.allPhotoLogs)
            }
        }
        lateinit var progress: ProgressViewModel
        lateinit var onboarding: OnboardingViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            progress = ProgressViewModel(delayed, users)
            onboarding = OnboardingViewModel(users, delayed)
            store.put("progress", progress)
            store.put("onboarding", onboarding)
        }
        compose.setContent { MaterialTheme { InsightsScreen(onboarding, progress, {}) } }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        compose.onNodeWithTag("insights_weight_chart_loading").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        compose.onNodeWithTag("insights_weight_history_loading").assertIsDisplayed()
        assertFalse(progress.weightLogsReady.value)
        assertFalse(progress.photoLogsReady.value)

        releaseWeights.complete(Unit)
        compose.waitUntil(5_000) { progress.weightLogsReady.value }
        compose.onNodeWithTag("insights_weight_history_loading").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(7)
        compose.onNodeWithText(context.getString(R.string.insights_no_entries)).assertIsDisplayed()
        assertFalse(progress.photoLogsReady.value)

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(9)
        compose.onNodeWithTag("insights_photo_gallery_loading").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.insights_no_photos)).assertDoesNotExist()
        releasePhotos.complete(Unit)
        compose.waitUntil(5_000) { progress.photoLogsReady.value }
        compose.onNodeWithTag("insights_photo_gallery_loading").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.insights_no_photos)).assertIsDisplayed()
        assertTrue(progress.weightLogsReady.value)
    }
}
