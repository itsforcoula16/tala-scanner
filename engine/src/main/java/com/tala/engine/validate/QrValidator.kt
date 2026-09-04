package com.tala.engine.validate

import com.tala.engine.decode.DecodeResult
import com.tala.engine.model.BarcodeFormat

class QrValidator : Validator {

    override fun validate(result: DecodeResult): ValidationResult {
        if (result.format != BarcodeFormat.QR_CODE) {
            return ValidationResult(false, 0f, "Not a QR code")
        }

        val value = result.value

        // Check minimum length
        if (value.isEmpty()) {
            return ValidationResult(false, 0f, "Empty QR code")
        }

        // Check valid UTF-8
        try {
            value.toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            return ValidationResult(false, 0f, "Invalid UTF-8 in QR code")
        }

        // Verify data patterns
        val dataScore = analyzeDataPattern(value)

        return if (dataScore > 0.5f) {
            ValidationResult(true, dataScore, "Valid QR code data")
        } else {
            ValidationResult(false, dataScore, "QR data pattern suspicious")
        }
    }

    private fun analyzeDataPattern(value: String): Float {
        var score = 0.8f

        // Common QR content types
        if (value.startsWith("http://") || value.startsWith("https://")) score += 0.1f
        if (value.all { it.isLetterOrDigit() || it in " _-.:/?#@!$&'()*+,;=" }) score += 0.05f
        if (value.length in 1..4296) score += 0.05f // Valid QR version length

        return score.coerceIn(0f, 1f)
    }
}
