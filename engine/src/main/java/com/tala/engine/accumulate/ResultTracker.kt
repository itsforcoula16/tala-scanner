package com.tala.engine.accumulate

import com.tala.engine.model.BarcodeResult
import com.tala.engine.model.BoundingBox

class ResultTracker(
    val fingerprint: String,
    val value: String,
    val format: com.tala.engine.model.BarcodeFormat
) {
    var confirmationCount: Int = 0
        private set

    var lastSeenTimestamp: Long = System.currentTimeMillis()
        private set

    var bestBoundingBox: BoundingBox = BoundingBox(0f, 0f, 0f, 0f)
        private set

    var bestConfidence: Float = 0f
        private set

    var isConfirmed: Boolean = false
        private set

    private val positionHistory = mutableListOf<Pair<Float, Float>>()

    fun update(result: BarcodeResult) {
        confirmationCount++
        lastSeenTimestamp = System.currentTimeMillis()
        positionHistory.add(result.boundingBox.centerX to result.boundingBox.centerY)

        if (result.confidence > bestConfidence) {
            bestConfidence = result.confidence
            bestBoundingBox = result.boundingBox
        }

        // Keep only last 10 positions
        if (positionHistory.size > 10) {
            positionHistory.removeFirst()
        }
    }

    fun confirm() {
        isConfirmed = true
    }

    fun getAveragePosition(): Pair<Float, Float> {
        if (positionHistory.isEmpty()) return 0f to 0f
        val avgX = positionHistory.map { it.first }.average().toFloat()
        val avgY = positionHistory.map { it.second }.average().toFloat()
        return avgX to avgY
    }

    fun toResult(): BarcodeResult {
        return BarcodeResult(
            value = value,
            format = format,
            confidence = bestConfidence,
            boundingBox = bestBoundingBox,
            timestampMs = lastSeenTimestamp,
            confirmationCount = confirmationCount,
            isConfirmed = isConfirmed
        )
    }

    fun timeSinceLastSeen(): Long = System.currentTimeMillis() - lastSeenTimestamp
}
