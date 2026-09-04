package com.tala.engine

import com.tala.engine.decode.DecodeResult
import com.tala.engine.model.BarcodeFormat
import com.tala.engine.model.BoundingBox
import com.tala.engine.validate.QrValidator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QrValidatorTest {

    private lateinit var validator: QrValidator

    @Before
    fun setup() {
        validator = QrValidator()
    }

    @Test
    fun `rejects non-QR format`() {
        val result = DecodeResult(
            value = "TEST",
            format = BarcodeFormat.CODE_128,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )
        val validation = validator.validate(result)
        assertFalse(validation.isValid)
    }

    @Test
    fun `rejects empty value`() {
        val result = DecodeResult(
            value = "",
            format = BarcodeFormat.QR_CODE,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 100f)
        )
        val validation = validator.validate(result)
        assertFalse(validation.isValid)
    }

    @Test
    fun `accepts URL content`() {
        val result = DecodeResult(
            value = "https://example.com/gold/001",
            format = BarcodeFormat.QR_CODE,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 100f)
        )
        val validation = validator.validate(result)
        assertTrue(validation.isValid)
        assertTrue(validation.score > 0.7f)
    }

    @Test
    fun `accepts plain text`() {
        val result = DecodeResult(
            value = "GOLD-24K-001-WEIGHT:10.5g",
            format = BarcodeFormat.QR_CODE,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 100f)
        )
        val validation = validator.validate(result)
        assertTrue(validation.isValid)
    }

    @Test
    fun `accepts numeric content`() {
        val result = DecodeResult(
            value = "1234567890",
            format = BarcodeFormat.QR_CODE,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 100f)
        )
        val validation = validator.validate(result)
        assertTrue(validation.isValid)
    }
}
