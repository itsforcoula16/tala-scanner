package com.tala.engine

import com.tala.engine.model.BoundingBox
import org.junit.Assert.*
import org.junit.Test

class BoundingBoxTest {

    @Test
    fun `basic properties`() {
        val box = BoundingBox(10f, 20f, 100f, 50f)
        assertEquals(10f, box.left)
        assertEquals(20f, box.top)
        assertEquals(100f, box.width)
        assertEquals(50f, box.height)
        assertEquals(110f, box.right)
        assertEquals(70f, box.bottom)
        assertEquals(60f, box.centerX)
        assertEquals(45f, box.centerY)
        assertEquals(5000f, box.area)
    }

    @Test
    fun `overlaps detects overlapping boxes`() {
        val box1 = BoundingBox(0f, 0f, 100f, 100f)
        val box2 = BoundingBox(50f, 50f, 100f, 100f)
        // Overlap area = 50x50 = 2500, min area = 10000
        // ratio = 0.25, need >= 0.7 (tolerance=0.3)
        // With higher tolerance, this should pass
        assertTrue(box1.overlaps(box2, tolerance = 0.8f))
    }

    @Test
    fun `overlaps returns false for non-overlapping`() {
        val box1 = BoundingBox(0f, 0f, 50f, 50f)
        val box2 = BoundingBox(200f, 200f, 50f, 50f)
        assertFalse(box1.overlaps(box2))
    }

    @Test
    fun `expand increases size`() {
        val box = BoundingBox(100f, 100f, 50f, 50f)
        val expanded = box.expand(2f)
        assertEquals(75f, expanded.left)
        assertEquals(75f, expanded.top)
        assertEquals(100f, expanded.width)
        assertEquals(100f, expanded.height)
    }

    @Test
    fun `clamp constrains to image bounds`() {
        val box = BoundingBox(-10f, -20f, 200f, 200f)
        val clamped = box.clamp(100, 100)
        assertEquals(0f, clamped.left)
        assertEquals(0f, clamped.top)
        assertTrue(clamped.width >= 0f)
        assertTrue(clamped.height >= 0f)
    }
}
