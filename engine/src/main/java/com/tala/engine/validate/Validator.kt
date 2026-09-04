package com.tala.engine.validate

import com.tala.engine.decode.DecodeResult

interface Validator {
    fun validate(result: DecodeResult): ValidationResult
}

data class ValidationResult(
    val isValid: Boolean,
    val score: Float,
    val reason: String
)
