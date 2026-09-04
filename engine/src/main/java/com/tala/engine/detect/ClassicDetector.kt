package com.tala.engine.detect

import android.graphics.Bitmap
import android.graphics.Color
import com.tala.engine.model.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ClassicDetector(
    private val minBarcodeWidth: Int = 50,
    private val minBarcodeHeight: Int = 20,
    private val edgeThreshold: Int = 30
) : Detector {

    override suspend fun detect(bitmap: Bitmap): List<DetectionResult> = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = toGrayscale(pixels, width, height)
        val edges = sobelEdgeDetect(gray, width, height)
        val regions = findBarcodeRegions(edges, gray, width, height)

        regions.map { region ->
            DetectionResult(
                boundingBox = region,
                detectionConfidence = calculateRegionConfidence(edges, region, width),
                detectorType = DetectorType.CLASSIC_GRADIENT
            )
        }
    }

    private fun toGrayscale(pixels: IntArray, width: Int, height: Int): IntArray {
        return IntArray(width * height) { i ->
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
    }

    private fun sobelEdgeDetect(gray: IntArray, width: Int, height: Int): IntArray {
        val edges = IntArray(width * height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val gx = -gray[(y - 1) * width + (x - 1)] + gray[(y - 1) * width + (x + 1)] +
                        -2 * gray[y * width + (x - 1)] + 2 * gray[y * width + (x + 1)] +
                        -gray[(y + 1) * width + (x - 1)] + gray[(y + 1) * width + (x + 1)]

                val gy = -gray[(y - 1) * width + (x - 1)] - 2 * gray[(y - 1) * width + x] - gray[(y - 1) * width + (x + 1)] +
                        gray[(y + 1) * width + (x - 1)] + 2 * gray[(y + 1) * width + x] + gray[(y + 1) * width + (x + 1)]

                edges[y * width + x] = min(255, abs(gx) + abs(gy))
            }
        }

        return edges
    }

    private fun findBarcodeRegions(edges: IntArray, gray: IntArray, width: Int, height: Int): List<BoundingBox> {
        val regions = mutableListOf<BoundingBox>()
        val visited = BooleanArray(width * height)
        val minEdgePixelCount = minBarcodeWidth * 3

        // Horizontal scan for 1D barcodes (Code128)
        for (y in 0 until height step 4) {
            var edgeRunStart = -1
            var edgeCount = 0

            for (x in 0 until width) {
                val idx = y * width + x
                if (edges[idx] > edgeThreshold) {
                    if (edgeRunStart == -1) edgeRunStart = x
                    edgeCount++
                } else {
                    if (edgeCount > minEdgePixelCount) {
                        val bbox = findVerticalExtent(edges, gray, edgeRunStart, y, edgeCount, width, height)
                        if (bbox != null && !regions.any { it.overlaps(bbox, 0.5f) }) {
                            regions.add(bbox)
                        }
                    }
                    edgeRunStart = -1
                    edgeCount = 0
                }
            }
        }

        // Vertical scan for QR codes (finder patterns)
        for (x in 0 until width step 4) {
            var edgeRunStart = -1
            var edgeCount = 0

            for (y in 0 until height) {
                val idx = y * width + x
                if (edges[idx] > edgeThreshold) {
                    if (edgeRunStart == -1) edgeRunStart = y
                    edgeCount++
                } else {
                    if (edgeCount > minBarcodeHeight * 3) {
                        val bbox = findHorizontalExtent(edges, gray, x, edgeRunStart, edgeCount, width, height)
                        if (bbox != null && !regions.any { it.overlaps(bbox, 0.5f) }) {
                            regions.add(bbox)
                        }
                    }
                    edgeRunStart = -1
                    edgeCount = 0
                }
            }
        }

        return regions
    }

    private fun findVerticalExtent(
        edges: IntArray, gray: IntArray,
        startX: Int, centerY: Int, edgeCount: Int,
        width: Int, height: Int
    ): BoundingBox? {
        var top = centerY
        var bottom = centerY
        val threshold = edgeThreshold / 2

        while (top > 0 && hasHorizontalEdges(edges, top - 1, startX, width, threshold)) top--
        while (bottom < height - 1 && hasHorizontalEdges(edges, bottom + 1, startX, width, threshold)) bottom++

        val h = bottom - top
        val w = edgeCount

        if (w < minBarcodeWidth || h < minBarcodeHeight) return null
        if (w.toFloat() / h > 15f || h.toFloat() / w > 15f) return null

        return BoundingBox(
            left = (startX - w * 0.1f).coerceAtLeast(0f),
            top = (top - h * 0.1f).coerceAtLeast(0f),
            width = w * 1.2f,
            height = h * 1.2f
        )
    }

    private fun findHorizontalExtent(
        edges: IntArray, gray: IntArray,
        centerX: Int, startY: Int, edgeCount: Int,
        width: Int, height: Int
    ): BoundingBox? {
        var left = centerX
        var right = centerX
        val threshold = edgeThreshold / 2

        while (left > 0 && hasVerticalEdges(edges, left - 1, startY, width, height, threshold)) left--
        while (right < width - 1 && hasVerticalEdges(edges, right + 1, startY, width, height, threshold)) right++

        val w = right - left
        val h = edgeCount

        if (w < minBarcodeWidth || h < minBarcodeHeight) return null
        if (abs(w.toFloat() / h - 1f) > 0.5f) return null

        return BoundingBox(
            left = (left - w * 0.1f).coerceAtLeast(0f),
            top = (startY - h * 0.1f).coerceAtLeast(0f),
            width = w * 1.2f,
            height = h * 1.2f
        )
    }

    private fun hasHorizontalEdges(edges: IntArray, y: Int, startX: Int, width: Int, threshold: Int): Boolean {
        var count = 0
        val searchRange = 20
        for (x in max(0, startX - searchRange) until min(width, startX + searchRange)) {
            if (edges[y * width + x] > threshold) count++
        }
        return count > searchRange / 3
    }

    private fun hasVerticalEdges(edges: IntArray, x: Int, startY: Int, imgWidth: Int, imgHeight: Int, threshold: Int): Boolean {
        var count = 0
        val searchRange = 20
        for (y in max(0, startY - searchRange) until min(imgHeight, startY + searchRange)) {
            if (edges[y * imgWidth + x] > threshold) count++
        }
        return count > searchRange / 3
    }

    private fun calculateRegionConfidence(edges: IntArray, region: BoundingBox, imageWidth: Int): Float {
        var edgePixels = 0
        var totalPixels = 0

        val left = region.left.toInt().coerceAtLeast(0)
        val top = region.top.toInt().coerceAtLeast(0)
        val right = (region.right).toInt().coerceAtMost(imageWidth)
        val bottom = region.bottom.toInt()

        for (y in top until bottom) {
            for (x in left until right) {
                totalPixels++
                if (edges[y * imageWidth + x] > edgeThreshold) edgePixels++
            }
        }

        return if (totalPixels > 0) (edgePixels.toFloat() / totalPixels).coerceIn(0f, 1f) else 0f
    }

    override fun release() {}
}
