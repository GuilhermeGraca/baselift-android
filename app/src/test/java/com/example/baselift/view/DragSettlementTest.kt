package com.example.baselift.view

import androidx.compose.runtime.BroadcastFrameClock
import com.example.baselift.View.workout.DraggedItemPlacement
import com.example.baselift.View.workout.awaitStableDraggedItemOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DragSettlementTest {
    @Test
    fun `a short positive-height card settles after two actual frames`() = runTest {
        val clock = BroadcastFrameClock()
        val result = async(clock) { awaitStableDraggedItemOffset(2) { DraggedItemPlacement(2, 90, 32) } }
        runCurrent()
        clock.sendFrame(16_000_000L); runCurrent()
        assertFalse(result.isCompleted)
        clock.sendFrame(32_000_000L); runCurrent()
        assertEquals(90, result.await())
    }

    @Test
    fun `expansion and moving offsets must finish before scrolling`() = runTest {
        val clock = BroadcastFrameClock()
        var placement = DraggedItemPlacement(2, 90, 32)
        val result = async(clock) { awaitStableDraggedItemOffset(2) { placement } }
        runCurrent()
        clock.sendFrame(16_000_000L); runCurrent()
        placement = placement.copy(size = 100)
        clock.sendFrame(32_000_000L); runCurrent()
        placement = placement.copy(offset = 150)
        clock.sendFrame(48_000_000L); runCurrent()
        assertFalse(result.isCompleted)
        clock.sendFrame(64_000_000L); runCurrent()
        assertEquals(150, result.await())
    }

    @Test
    fun `old layout index is not acknowledged before reorder reaches layout`() = runTest {
        val clock = BroadcastFrameClock()
        var placement = DraggedItemPlacement(0, 90, 32)
        val result = async(clock) { awaitStableDraggedItemOffset(2) { placement } }
        runCurrent()
        clock.sendFrame(16_000_000L); runCurrent()
        clock.sendFrame(32_000_000L); runCurrent()
        assertFalse(result.isCompleted)
        placement = placement.copy(index = 2, offset = 180)
        clock.sendFrame(48_000_000L); runCurrent()
        clock.sendFrame(64_000_000L); runCurrent()
        assertEquals(180, result.await())
    }

    @Test
    fun `missing or offscreen exercise ends settlement without an offset`() = runTest {
        val clock = BroadcastFrameClock()
        val result = async(clock) { awaitStableDraggedItemOffset(2) { null } }
        runCurrent()
        clock.sendFrame(16_000_000L); runCurrent()
        assertNull(result.await())
    }

    @Test
    fun `unready layout times out even when no more frames arrive`() = runTest {
        val clock = BroadcastFrameClock()
        val result = async(clock) { awaitStableDraggedItemOffset(2) { DraggedItemPlacement(0, 90, 32) } }
        runCurrent()
        clock.sendFrame(16_000_000L); runCurrent()
        advanceUntilIdle()
        assertNull(result.await())
        assertEquals(500L, testScheduler.currentTime)
    }

    @Test
    fun `zero height cannot settle and resets the consecutive frame check`() = runTest {
        val clock = BroadcastFrameClock()
        var placement = DraggedItemPlacement(2, 90, 32)
        val result = async(clock) { awaitStableDraggedItemOffset(2) { placement } }
        runCurrent()
        clock.sendFrame(16_000_000L); runCurrent()
        placement = placement.copy(size = 0)
        clock.sendFrame(32_000_000L); runCurrent()
        placement = placement.copy(size = 32)
        clock.sendFrame(48_000_000L); runCurrent()
        assertFalse(result.isCompleted)
        clock.sendFrame(64_000_000L); runCurrent()
        assertEquals(90, result.await())
    }

    @Test
    fun `owner cancellation stops frame reads without waiting for timeout`() = runTest {
        val clock = BroadcastFrameClock()
        var reads = 0
        val result = async(clock) {
            awaitStableDraggedItemOffset(2) { reads++; DraggedItemPlacement(2, 90, 32) }
        }
        runCurrent()
        clock.sendFrame(16_000_000L); runCurrent()
        result.cancel()
        runCurrent()
        clock.sendFrame(32_000_000L); runCurrent()
        assertTrue(result.isCancelled)
        assertEquals(1, reads)
        assertEquals(0L, testScheduler.currentTime)
    }
}
