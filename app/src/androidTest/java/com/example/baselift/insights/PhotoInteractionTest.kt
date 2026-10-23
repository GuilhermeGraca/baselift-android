package com.example.baselift.insights

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.R
import com.example.baselift.View.insights.components.PhotoDetailDialog
import com.example.baselift.View.insights.components.VisualDiarySection
import com.example.baselift.View.insights.components.ZoomableProgressPhoto
import com.example.baselift.View.insights.components.rememberPhotoWeightIndex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalComposeApi::class)
@RunWith(AndroidJUnit4::class)
class PhotoInteractionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var imageFile: File
    private lateinit var photo: PhotoLogEntity
    private lateinit var previousZone: TimeZone
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = 86_400_000L

    @Before fun preparePhoto() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        imageFile = File.createTempFile("photo-interaction-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (row in 0..7) for (column in 0..7) {
            paint.color = if ((row + column) % 2 == 0) android.graphics.Color.RED else android.graphics.Color.BLUE
            canvas.drawRect(column * 30f, row * 30f, (column + 1) * 30f, (row + 1) * 30f, paint)
        }
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        photo = PhotoLogEntity(1, Uri.fromFile(imageFile).toString(), 1_780_000_000_000L)
    }

    @After fun cleanup() {
        imageFile.delete()
        TimeZone.setDefault(previousZone)
    }

    private fun pixelsEqual(first: ImageBitmap, second: ImageBitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        for (y in 0 until first.height step 3) for (x in 0 until first.width step 3) {
            if (a[x, y] != b[x, y]) return false
        }
        return true
    }

    private fun awaitImage(): ImageBitmap {
        val viewer = compose.onNodeWithTag("viewer")
        compose.waitUntil(5_000) {
            val image = viewer.captureToImage()
            val pixel = image.toPixelMap()[image.width / 4, image.height / 2]
            // Scaling can blend adjacent red/blue tiles at this sample position.
            pixel.red + pixel.blue > 0.8f && pixel.green < 0.3f
        }
        compose.waitForIdle()
        return viewer.captureToImage()
    }

    private fun holdPinch() {
        compose.onNodeWithTag("viewer").performTouchInput {
            down(0, center + Offset(-50f, 0f))
            down(1, center + Offset(50f, 0f))
            moveTo(0, center + Offset(-100f, 0f), delayMillis = 16)
            moveTo(1, center + Offset(100f, 0f), delayMillis = 16)
            moveTo(0, center + Offset(-140f, 0f), delayMillis = 16)
            moveTo(1, center + Offset(140f, 0f), delayMillis = 16)
        }
    }

    @Test
    fun pinchAndPanUpdateTheLayerWithoutRecomposingAndReleaseResetsTheImage() {
        compose.setContent { MaterialTheme { ZoomableProgressPhoto(photo, Modifier.testTag("viewer")) } }
        val original = awaitImage()
        val changes = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
        holdPinch()
        val viewer = compose.onNodeWithTag("viewer")
        val zoomed = viewer.captureToImage()
        assertFalse("Pinch must actually transform the loaded image", pixelsEqual(original, zoomed))
        viewer.performTouchInput {
            moveBy(0, Offset(30f, 20f), delayMillis = 16)
            moveBy(1, Offset(30f, 20f), delayMillis = 16)
        }
        assertFalse("Pan must move the zoomed image", pixelsEqual(zoomed, viewer.captureToImage()))
        assertEquals("Gesture frames should not recompose the photo UI", changes,
            Recomposer.runningRecomposers.value.sumOf { it.changeCount })
        viewer.performTouchInput { up(0); up(1) }
        assertTrue("Releasing the gesture restores the original transform", pixelsEqual(original, viewer.captureToImage()))
    }

    @Test
    fun changingPhotoIdentityResetsAnActiveTransform() {
        val selected = mutableStateOf(photo)
        compose.setContent { MaterialTheme { ZoomableProgressPhoto(selected.value, Modifier.testTag("viewer")) } }
        val original = awaitImage()
        holdPinch()
        val viewer = compose.onNodeWithTag("viewer")
        assertFalse(pixelsEqual(original, viewer.captureToImage()))
        compose.runOnIdle { selected.value = photo.copy(id = 2) }
        assertTrue("A new photo starts at 1x even while the old gesture was active", pixelsEqual(original, viewer.captureToImage()))
        viewer.performTouchInput { up(0); up(1) }
    }

    @Test
    fun detailKeepsTheFirstDailyWeightAndRefreshesAfterEditsWithoutRescanningOnConfirmation() {
        val reads = AtomicInteger()
        val original = (1..1_000).map { WeightLogEntity(it, 80f, photo.timestamp - (1_000 - it) * day) }
        val observed = object : AbstractList<WeightLogEntity>() {
            override val size = original.size
            override fun get(index: Int): WeightLogEntity { reads.incrementAndGet(); return original[index] }
        }
        val logs = mutableStateOf<List<WeightLogEntity>>(observed)
        val shown = mutableStateOf(true)
        var deleted = 0
        compose.setContent { MaterialTheme {
            val index = rememberPhotoWeightIndex(logs.value)
            if (shown.value) PhotoDetailDialog(photo, index, onDismiss = { shown.value = false },
                onDelete = { deleted++; shown.value = false })
        } }
        compose.onNodeWithText("80.0 KG").assertIsDisplayed()
        reads.set(0)
        compose.onNodeWithContentDescription("Delete Photo").performClick()
        compose.onNodeWithText(context.getString(R.string.workout_cancel)).performClick()
        assertEquals(0, reads.get())
        compose.runOnIdle {
            logs.value = original.dropLast(1) + original.last().copy(weightValue = 75f) +
                original.last().copy(id = 1_001, weightValue = 99f)
        }
        compose.onNodeWithText("75.0 KG").assertIsDisplayed()
        compose.onNodeWithText("99.0 KG").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete Photo").performClick()
        compose.onNodeWithText(context.getString(R.string.insights_confirm_delete)).performClick()
        compose.runOnIdle { assertEquals(1, deleted) }
    }

    @Test
    fun resumingAfterTimezoneChangesRefreshesTheDailyMatch() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle get() = registry
        }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        val selected = photo.copy(timestamp = Instant.parse("2026-10-11T00:30:00Z").toEpochMilli())
        val logs = listOf(WeightLogEntity(1, 80f, Instant.parse("2026-10-10T23:30:00Z").toEpochMilli()))
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            MaterialTheme { PhotoDetailDialog(selected, rememberPhotoWeightIndex(logs), {}, {}) }
        } }
        compose.onNodeWithText("80.0 KG").assertDoesNotExist() // Different UTC days.
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("80.0 KG").assertIsDisplayed() // Same local day.
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("80.0 KG").assertDoesNotExist()
    }

    @Test
    fun diaryScrollAndPhotoActionsReuseTheDailyIndex() {
        val reads = AtomicInteger()
        val original = (1..1_000).map { WeightLogEntity(it, it.toFloat(), photo.timestamp + it * day) }
        val observed = object : AbstractList<WeightLogEntity>() {
            override val size = original.size
            override fun get(index: Int): WeightLogEntity { reads.incrementAndGet(); return original[index] }
        }
        val first = photo.copy(timestamp = original[998].timestamp)
        val latest = photo.copy(id = 2, timestamp = original[999].timestamp)
        var clicked: PhotoLogEntity? = null
        compose.setContent { MaterialTheme {
            val index = rememberPhotoWeightIndex(observed)
            VisualDiarySection(listOf(first, latest), index, onPhotoClick = { clicked = it }, onAddPhoto = {})
        } }
        compose.onNodeWithText("1000.0 KG").assertIsDisplayed()
        reads.set(0)
        compose.onNodeWithText("999.0 KG").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(first, clicked) }
        compose.onNodeWithText("1000.0 KG").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(latest, clicked) }
        assertEquals("Scrolling and clicking cards must not search the raw weight history", 0, reads.get())
    }
}
