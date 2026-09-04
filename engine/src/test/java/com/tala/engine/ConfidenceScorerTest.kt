package com.tala.engine

import com.tala.engine.decode.DecodeResult
import com.tala.engine.detect.DetectionResult
import com.tala.engine.detect.DetectorType
import com.tala.engine.model.BarcodeFormat
import com.tala.engine.model.BoundingBox
import com.tala.engine.validate.ConfidenceScorer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConfidenceScorerTest {

    private lateinit var scorer: ConfidenceScorer

    @Before
    fun setup() {
        scorer = ConfidenceScorer()
    }

    @Test
    fun `high confidence for all good inputs`() {
        val decode = DecodeResult(
            value = "TEST",
            format = BarcodeFormat.CODE_128,
            decodeConfidence = 0.95f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )
        val detection = DetectionResult(
            boundingBox = BoundingBox(0f, 0f, 100f, 50f),
            detectionConfidence = 0.95f,
            detectorType = DetectorType.ML_YOLO
        )

        val score = scorer.calculateScore(
            decodeResult = decode,
            detection = detection,
            isValid = true,
            validationScore = 0.9f,
            previousConfirmations = 3
        )

        assertTrue("Score should be > 0.7, was $score", score > 0.7f)
    }

    @Test
    fun `low confidence when invalid`() {
        val decode = DecodeResult(
            value = "TEST",
            format = BarcodeFormat.CODE_128,
            decodeConfidence = 0.9f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )

        val score = scorer.calculateScore(
            decodeResult = decode,
            detection = null,
            isValid = false,
            validationScore = 0f,
            previousConfirmations = 0
        )

        assertTrue("Score should be < 0.5, was $score", score < 0.5f)
    }

    @Test
    fun `stability increases with confirmations`() {
        val decode = DecodeResult(
            value = "TEST",
            format = BarcodeFormat.CODE_128,
            decodeConfidence = 0.8f,
            boundingBox = BoundingBox(0f, 0f, 100f, 50f)
        )
        val detection = DetectionResult(
            boundingBox = BoundingBox(0f, 0f, 100f, 50f),
            detectionConfidence = 0.8f,
            detectorType = DetectorType.CLASSIC_GRADIENT
        )

        val score0 = scorer.calculateScore(decode, detection, true, 0.8f, 0)
        val score1 = scorer.calculateScore(decode, detection, true, 0.8f, 1)
        val score3 = scorer.calculateScore(decode, detection, true, 0.8f, 3)

        assertTrue("Score with 3 confirmations should be > score with 0", score3 > score0)
        assertTrue("Score with 1 confirmation should be > score with 0", score1 > score0)
    }

    @Test
    fun `score always between 0 and 1`() {
        val decode = DecodeResult(
            value = "TEST",
            format = BarcodeFormat.QR_CODE,
            decodeConfidence = 1.0f,
            boundingBox = BoundingBox(0f, 0f, 100f, 100f)
        )

        val score = scorer.calculateScore(decode, null, true, 1.0f, 100)
        assertTrue("Score should be <= 1.0, was $score", score <= 1.0f)
        assertTrue("Score should be >= 0.0, was $score", score >= 0.0f)
    }
}
