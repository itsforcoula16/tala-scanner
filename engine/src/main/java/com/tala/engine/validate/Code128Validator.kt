package com.tala.engine.validate

import com.tala.engine.decode.DecodeResult
import com.tala.engine.model.BarcodeFormat

class Code128Validator : Validator {

    override fun validate(result: DecodeResult): ValidationResult {
        if (result.format != BarcodeFormat.CODE_128) {
            return ValidationResult(false, 0f, "Not a Code128 barcode")
        }

        val value = result.value

        // Check minimum length
        if (value.length < 2) {
            return ValidationResult(false, 0f, "Too short for Code128")
        }

        // Check character set (Code128 supports ASCII 0-127)
        for (c in value) {
            if (c.code < 0 || c.code > 127) {
                return ValidationResult(false, 0f, "Invalid character in Code128: ${c.code}")
            }
        }

        // Verify check digit (mod 103)
        if (value.length >= 4) {
            val checksumValid = verifyCheckDigit(value)
            if (checksumValid) {
                return ValidationResult(true, 0.95f, "Valid Code128 with correct checksum")
            } else {
                return ValidationResult(false, 0.3f, "Code128 checksum mismatch")
            }
        }

        return ValidationResult(true, 0.7f, "Code128 format valid (short, no checksum verification)")
    }

    private fun verifyCheckDigit(value: String): Boolean {
        // Code128 checksum: sum of (position * value) mod 103
        // The last character is the checksum
        if (value.length < 3) return false

        val body = value.dropLast(1)
        var sum = 0

        for ((index, c) in body.withIndex()) {
            val code = c.code
            val value = when {
                code >= 32 && code <= 126 -> code - 32
                code == 0 -> 64 // SP fallback
                else -> code
            }
            val position = if (index == 0) 1 else index + 1
            sum += position * value
        }

        val expectedChecksum = sum % 103
        val actualChecksumChar = value.last()
        val actualChecksum = when {
            actualChecksumChar.code >= 32 && actualChecksumChar.code <= 126 ->
                actualChecksumChar.code - 32
            else -> actualChecksumChar.code
        }

        return expectedChecksum == actualChecksum
    }
}
