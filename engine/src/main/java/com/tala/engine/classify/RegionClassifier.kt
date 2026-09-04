package com.tala.engine.classify

import android.graphics.Bitmap
import android.graphics.Color
import com.tala.engine.detect.DetectionResult
import com.tala.engine.model.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class RegionClassifier {

    data class ClassificationResult(
        val primaryFormat: BarcodeFormat,
        val confidence: Float,
        val tryBothFormats: Boolean = true
    )

    suspend fun classify(
        bitmap: Bitmap,
        detection: DetectionResult
    ): ClassificationResult = withContext(Dispatchers.Default) {
        val bbox = detection.boundingBox
        val left = bbox.left.toInt().coerceAtLeast(0)
        val top = bbox.top.toInt().coerceAtLeast(0)
        val width = bbox.width.toInt().coerceAtMost(bitmap.width - left)
        val height = bbox.height.toInt().coerceAtMost(bitmap.height - top)

        if (width <= 0 || height <= 0) {
            return@withContext ClassificationResult(BarcodeFormat.QR_CODE, 0.5f, true)
        }

        val region = Bitmap.createBitmap(bitmap, left, top, width, height)
        val pixels = IntArray(width * height)
        region.getPixels(pixels, 0, width, 0, 0, width, height)
        region.recycle()

        val qrScore = analyzeQrLikeness(pixels, width, height)
        val code128Score = analyzeCode128Likeness(pixels, width, height)

        val aspectRatio = width.toFloat() / height.toFloat()
        val arQrBoost = if (aspectRatio in 0.8f..1.2f) 0.15f else 0f
        val arCode128Boost = if (aspectRatio > 2f) 0.15f else 0f

        val finalQrScore = (qrScore + arQrBoost).coerceIn(0f, 1f)
        val finalCode128Score = (code128Score + arCode128Boost).coerceIn(0f, 1f)

        return@withContext if (finalQrScore > finalCode128Score) {
            ClassificationResult(
                primaryFormat = BarcodeFormat.QR_CODE,
                confidence = finalQrScore,
                tryBothFormats = finalQrScore < 0.7f
            )
        } else {
            ClassificationResult(
                primaryFormat = BarcodeFormat.CODE_128,
                confidence = finalCode128Score,
                tryBothFormats = finalCode128Score < 0.7f
            )
        }
    }

    private fun analyzeQrLikeness(pixels: IntArray, width: Int, height: Int): Float {
        var score = 0f

        // Check for finder patterns (three corner squares)
        val corners = listOf(
            0 to 0,
            width - 21 to 0,
            0 to height - 21
        )

        var finderPatternCount = 0
        for ((cx, cy) in corners) {
            if (cx >= 0 && cy >= 0 && cx + 21 <= width && cy + 21 <= height) {
                if (hasFinderPattern(pixels, cx, cy, width)) {
                    finderPatternCount++
                }
            }
        }

        score += finderPatternCount * 0.3f

        // 2D edge density analysis (QR has edges in both directions)
        val hEdges = countHorizontalEdges(pixels, width, height)
        val vEdges = countVerticalEdges(pixels, width, height)
        val edgeBalance = if (hEdges + vEdges > 0) {
            1f - abs(hEdges - vEdges).toFloat() / (hEdges + vEdges)
        } else 0f

        score += edgeBalance * 0.3f

        // Square-ish aspect ratio
        val aspectRatio = width.toFloat() / height.toFloat()
        if (aspectRatio in 0.7f..1.3f) score += 0.2f

        return score.coerceIn(0f, 1f)
    }

    private fun analyzeCode128Likeness(pixels: IntArray, width: Int, height: Int): Float {
        var score = 0f

        // Horizontal line pattern density
        val lineTransitions = countHorizontalTransitions(pixels, width, height)
        val expectedTransitions = width * 0.1f
        if (lineTransitions > expectedTransitions * 0.3f) {
            score += 0.3f
        }

        // Strong horizontal edges (bars are horizontal patterns)
        val hEdges = countHorizontalEdges(pixels, width, height)
        val vEdges = countVerticalEdges(pixels, width, height)
        if (hEdges > vEdges * 1.5f) {
            score += 0.3f
        }

        // Wide aspect ratio (Code128 is typically wider than tall)
        val aspectRatio = width.toFloat() / height.toFloat()
        if (aspectRatio > 2f) score += 0.25f
        else if (aspectRatio > 1.5f) score += 0.15f

        // Quiet zone presence (white borders)
        if (hasQuietZone(pixels, width, height)) {
            score += 0.15f
        }

        return score.coerceIn(0f, 1f)
    }

    private fun hasFinderPattern(pixels: IntArray, startX: Int, startY: Int, imageWidth: Int): Boolean {
        val size = minOf(21, 7)
        var darkCount = 0
        var totalCount = 0

        for (y in startY until startY + size * 3) {
            for (x in startX until startX + size * 3) {
                if (x < imageWidth && y < pixels.size / imageWidth) {
                    val brightness = Color.red(pixels[y * imageWidth + x])
                    totalCount++
                    if (brightness < 128) darkCount++
                }
            }
        }

        val ratio = if (totalCount > 0) darkCount.toFloat() / totalCount else 0f
        return ratio in 0.3f..0.7f
    }

    private fun countHorizontalEdges(pixels: IntArray, width: Int, height: Int): Int {
        var count = 0
        for (y in 1 until height) {
            for (x in 1 until width) {
                val current = Color.red(pixels[y * width + x])
                val above = Color.red(pixels[(y - 1) * width + x])
                if (abs(current - above) > 50) count++
            }
        }
        return count
    }

    private fun countVerticalEdges(pixels: IntArray, width: Int, height: Int): Int {
        var count = 0
        for (y in 1 until height) {
            for (x in 1 until width) {
                val current = Color.red(pixels[y * width + x])
                val left = Color.red(pixels[y * width + (x - 1)])
                if (abs(current - left) > 50) count++
            }
        }
        return count
    }

    private fun countHorizontalTransitions(pixels: IntArray, width: Int, height: Int): Int {
        var transitions = 0
        val midY = height / 2

        for (x in 1 until width) {
            val current = Color.red(pixels[midY * width + x]) > 128
            val prev = Color.red(pixels[midY * width + (x - 1)]) > 128
            if (current != prev) transitions++
        }
        return transitions
    }

    private fun hasQuietZone(pixels: IntArray, width: Int, height: Int): Boolean {
        val margin = 5
        var whiteCount = 0
        var totalCount = 0

        // Check left margin
        for (y in 0 until height) {
            for (x in 0 until minOf(margin, width)) {
                totalCount++
                if (Color.red(pixels[y * width + x]) > 180) whiteCount++
            }
        }

        return if (totalCount > 0) whiteCount.toFloat() / totalCount > 0.8f else false
    }
}
