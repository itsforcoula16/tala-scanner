package com.tala.engine.validate

import com.tala.engine.decode.DecodeResult
import com.tala.engine.detect.DetectionResult
import kotlin.math.ln

class ConfidenceScorer(
    private val weights: Weights = Weights()
) {
    data class Weights(
        val detection: Float = 0.25f,
        val decode: Float = 0.25f,
        val validation: Float = 0.20f,
        val stability: Float = 0.20f,
        val sharpness: Float = 0.10f
    )

    fun calculateScore(
        decodeResult: DecodeResult,
        detection: DetectionResult?,
        isValid: Boolean,
        validationScore: Float,
        previousConfirmations: Int = 0
    ): Float {
        val detectionScore = detection?.detectionConfidence ?: 0.5f
        val decodeScore = decodeResult.decodeConfidence
        val validScore = if (isValid) validationScore else 0f
        val stabilityScore = calculateStabilityScore(previousConfirmations)
        val sharpnessScore = calculateSharpnessFromConfidence(detectionScore, decodeScore)

        return (weights.detection * detectionScore +
                weights.decode * decodeScore +
                weights.validation * validScore +
                weights.stability * stabilityScore +
                weights.sharpness * sharpnessScore)
            .coerceIn(0f, 1f)
    }

    private fun calculateStabilityScore(confirmations: Int): Float {
        // Logarithmic scale: each additional confirmation increases confidence
        // 0 confirmations = 0.0
        // 1 confirmation = 0.5
        // 2 confirmations = 0.75
        // 3+ confirmations = 0.85+
        return when {
            confirmations == 0 -> 0f
            confirmations == 1 -> 0.5f
            confirmations == 2 -> 0.75f
            else -> (0.85f + 0.05f * ln(confirmations.toFloat())).coerceIn(0f, 1f)
        }
    }

    private fun calculateSharpnessFromConfidence(detectionConf: Float, decodeConf: Float): Float {
        // Heuristic: if both detection and decode are confident, image is likely sharp
        return ((detectionConf + decodeConf) / 2f).coerceIn(0f, 1f)
    }
}
