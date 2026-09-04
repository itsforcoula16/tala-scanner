package com.tala.engine.model

data class BarcodeResult(
    val value: String,
    val format: BarcodeFormat,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val timestampMs: Long = System.currentTimeMillis(),
    val frameIndex: Long = 0,
    val confirmationCount: Int = 1,
    val isConfirmed: Boolean = false
) {
    val fingerprint: String
        get() = "${format.id}:${value.hashCode()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BarcodeResult) return false
        return value == other.value && format == other.format
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + format.hashCode()
        return result
    }
}
