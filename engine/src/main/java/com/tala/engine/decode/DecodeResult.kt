package com.tala.engine.decode

import com.tala.engine.model.BarcodeFormat
import com.tala.engine.model.BoundingBox

data class DecodeResult(
    val value: String,
    val format: BarcodeFormat,
    val decodeConfidence: Float,
    val boundingBox: BoundingBox,
    val decodePass: DecodePass = DecodePass.PRIMARY,
    val rawBytes: ByteArray? = null
) {
    enum class DecodePass {
        PRIMARY,
        UPSCALED,
        DE_SKEWED,
        SECONDARY_FORMAT
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodeResult) return false
        return value == other.value && format == other.format
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + format.hashCode()
        return result
    }
}
