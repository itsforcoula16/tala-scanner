package com.tala.engine.detect

import com.tala.engine.model.BoundingBox

data class DetectionResult(
    val boundingBox: BoundingBox,
    val detectionConfidence: Float,
    val detectorType: DetectorType
)

enum class DetectorType {
    ML_YOLO,
    CLASSIC_GRADIENT,
    COMBINED
}
