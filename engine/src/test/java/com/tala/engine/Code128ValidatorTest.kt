package com.tala.engine

import com.tala.engine.decode.DecodeResult
import com.tala.engine.model.BarcodeFormat
import com.tala.engine.model.BoundingBox
import com.tala.engine.validate.Code128Validator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Code128ValidatorTest {

    private lateinit var validator: Code128Validator

    @Before
    fun setup() {
        validator = Code128Validator()
    }

    @Test
    fun `rejects non-Code128 format`() {
        val result = DecodeResult(
            value = "TEST",
            format = BarcodeFormat.QR_CODE,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )
        val validation = validator.validate(result)
        assertFalse(validation.isValid)
    }

    @Test
    fun `rejects too short value`() {
        val result = DecodeResult(
            value = "A",
            format = BarcodeFormat.CODE_128,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )
        val validation = validator.validate(result)
        assertFalse(validation.isValid)
    }

    @Test
    fun `accepts valid ASCII value`() {
        val result = DecodeResult(
            value = "ABC123",
            format = BarcodeFormat.CODE_128,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )
        val validation = validator.validate(result)
        assertTrue(validation.isValid)
        assertTrue(validation.score > 0.5f)
    }

    @Test
    fun `rejects invalid characters`() {
        val result = DecodeResult(
            value = "ABCÿDEF",
            format = BarcodeFormat.CODE_128,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )
        val validation = validator.validate(result)
        assertFalse(validation.isValid)
    }

    @Test
    fun `accepts typical barcode content`() {
        val result = DecodeResult(
            value = "GOLD-24K-001",
            format = BarcodeFormat.CODE_128,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 200f, 50f)
        )
        val validation = validator.validate(result)
        assertTrue(validation.isValid)
    }
}
