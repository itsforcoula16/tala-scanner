package com.tala.engine

import com.tala.engine.dedup.Deduplicator
import com.tala.engine.model.BarcodeFormat
import com.tala.engine.model.BarcodeResult
import com.tala.engine.model.BoundingBox
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeduplicatorTest {

    private lateinit var deduplicator: Deduplicator

    @Before
    fun setup() {
        deduplicator = Deduplicator()
    }

    @Test
    fun `first occurrence passes through`() {
        val result = BarcodeResult(
            value = "GOLD-001",
            format = BarcodeFormat.CODE_128,
            confidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )

        val output = deduplicator.processConfirmed(result)
        assertNotNull(output)
        assertEquals("GOLD-001", output!!.value)
    }

    @Test
    fun `duplicate is filtered`() {
        val result = BarcodeResult(
            value = "GOLD-001",
            format = BarcodeFormat.CODE_128,
            confidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )

        deduplicator.processConfirmed(result)
        val duplicate = deduplicator.processConfirmed(result)
        assertNull(duplicate)
    }

    @Test
    fun `different values are not duplicates`() {
        val result1 = BarcodeResult(
            value = "GOLD-001",
            format = BarcodeFormat.CODE_128,
            confidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )
        val result2 = BarcodeResult(
            value = "GOLD-002",
            format = BarcodeFormat.CODE_128,
            confidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )

        deduplicator.processConfirmed(result1)
        val output = deduplicator.processConfirmed(result2)
        assertNotNull(output)
    }

    @Test
    fun `session count is correct`() {
        assertEquals(0, deduplicator.getSessionCount())

        deduplicator.processConfirmed(makeResult("A"))
        deduplicator.processConfirmed(makeResult("B"))
        deduplicator.processConfirmed(makeResult("C"))
        deduplicator.processConfirmed(makeResult("A")) // duplicate

        assertEquals(3, deduplicator.getSessionCount())
    }

    @Test
    fun `reset clears session`() {
        deduplicator.processConfirmed(makeResult("A"))
        deduplicator.processConfirmed(makeResult("B"))
        assertEquals(2, deduplicator.getSessionCount())

        deduplicator.reset()
        assertEquals(0, deduplicator.getSessionCount())
    }

    @Test
    fun `isDuplicate returns true for seen values`() {
        assertFalse(deduplicator.isDuplicate(makeResult("A")))
        deduplicator.processConfirmed(makeResult("A"))
        assertTrue(deduplicator.isDuplicate(makeResult("A")))
    }

    private fun makeResult(value: String) = BarcodeResult(
        value = value,
        format = BarcodeFormat.CODE_128,
        confidence = 0.9f,
        boundingBox = BoundingBox(0f, 0f, 100f, 50f)
    )
}
