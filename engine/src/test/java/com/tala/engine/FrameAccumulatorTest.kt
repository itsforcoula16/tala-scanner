package com.tala.engine

import com.tala.engine.accumulate.FrameAccumulator
import com.tala.engine.model.AccumulationConfig
import com.tala.engine.model.BarcodeFormat
import com.tala.engine.model.BarcodeResult
import com.tala.engine.model.BoundingBox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FrameAccumulatorTest {

    private lateinit var accumulator: FrameAccumulator

    @Before
    fun setup() {
        accumulator = FrameAccumulator(
            AccumulationConfig(
                minConfirmations = 3,
                autoClearThreshold = 4,
                forceConfirmAfterWindow = false,
                accumulationWindowMs = 5000L
            )
        )
    }

    @Test
    fun `barcode not confirmed before min confirmations`() {
        repeat(2) {
            accumulator.processFrame(listOf(makeResult("GOLD-001")))
        }

        val confirmed = accumulator.getAllConfirmed()
        assertTrue("Should not be confirmed yet", confirmed.isEmpty())
    }

    @Test
    fun `barcode confirmed after min confirmations`() {
        repeat(3) {
            accumulator.processFrame(listOf(makeResult("GOLD-001")))
        }

        val confirmed = accumulator.getAllConfirmed()
        assertEquals(1, confirmed.size)
        assertEquals("GOLD-001", confirmed[0].value)
        assertTrue(confirmed[0].isConfirmed)
    }

    @Test
    fun `multiple different barcodes tracked independently`() {
        repeat(3) {
            accumulator.processFrame(listOf(
                makeResult("GOLD-001"),
                makeResult("GOLD-002"),
                makeResult("GOLD-003")
            ))
        }

        val confirmed = accumulator.getAllConfirmed()
        assertEquals(3, confirmed.size)
    }

    @Test
    fun `frame without barcode clears after threshold`() {
        // Register barcode
        repeat(2) {
            accumulator.processFrame(listOf(makeResult("GOLD-001")))
        }

        // Send empty frames
        repeat(5) {
            accumulator.processFrame(emptyList())
        }

        // Should not be tracked anymore
        assertEquals(0, accumulator.getTrackerCount())
    }

    @Test
    fun `barcode reappears resets clear timer`() {
        // Register
        accumulator.processFrame(listOf(makeResult("GOLD-001")))
        // Brief disappearance (below threshold)
        repeat(2) {
            accumulator.processFrame(emptyList())
        }
        // Reappears
        accumulator.processFrame(listOf(makeResult("GOLD-001")))
        // More empty frames but below threshold
        repeat(2) {
            accumulator.processFrame(emptyList())
        }

        // Should still be tracked
        assertTrue(accumulator.getTrackerCount() > 0)
    }

    @Test
    fun `reset clears all trackers`() {
        repeat(3) {
            accumulator.processFrame(listOf(makeResult("GOLD-001")))
        }
        assertTrue(accumulator.getAllConfirmed().isNotEmpty())

        accumulator.reset()
        assertEquals(0, accumulator.getAllConfirmed().size)
        assertEquals(0, accumulator.getTrackerCount())
    }

    private fun makeResult(value: String) = BarcodeResult(
        value = value,
        format = BarcodeFormat.CODE_128,
        confidence = 0.9f,
        boundingBox = BoundingBox(100f, 100f, 200f, 50f)
    )
}
