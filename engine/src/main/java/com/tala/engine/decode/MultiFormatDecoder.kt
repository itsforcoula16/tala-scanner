package com.tala.engine.decode

import android.content.Context
import android.graphics.Bitmap
import com.tala.engine.classify.RegionClassifier
import com.tala.engine.detect.DetectionResult
import com.tala.engine.model.BarcodeFormat
import com.tala.engine.model.BoundingBox
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class MultiFormatDecoder(
    private val context: Context,
    private val maxConcurrentDecoders: Int = 4,
    private val preprocessingEnabled: Boolean = true
) {
    private val classifier = RegionClassifier()
    private val decodeDispatcher = Dispatchers.Default.limitedParallelism(maxConcurrentDecoders)
    private val decodeCounter = AtomicInteger(0)

    // Real ZXing decoder
    private val zxingDecoder = ZxingJavaDecoder()

    // Native C++ decoder (optional, for performance)
    private var nativeAvailable = false

    init {
        try {
            System.loadLibrary("tala_native")
            nativeAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            nativeAvailable = false
        }
    }

    private external fun nativeDecodeGray(
        grayData: ByteArray,
        width: Int,
        height: Int,
        formatHint: Int,
        enablePreprocessing: Boolean
    ): Array<String>?

    suspend fun decodeAll(
        bitmap: Bitmap,
        detections: List<DetectionResult>
    ): List<DecodeResult> = withContext(decodeDispatcher) {
        val deferreds = detections.map { detection ->
            async {
                decodeSingle(bitmap, detection)
            }
        }

        deferreds.awaitAll().flatten()
    }

    private suspend fun decodeSingle(
        bitmap: Bitmap,
        detection: DetectionResult
    ): List<DecodeResult> = withContext(decodeDispatcher) {
        val results = mutableListOf<DecodeResult>()
        val bbox = detection.boundingBox

        val left = bbox.left.toInt().coerceAtLeast(0)
        val top = bbox.top.toInt().coerceAtLeast(0)
        val width = bbox.width.toInt().coerceAtMost(bitmap.width - left)
        val height = bbox.height.toInt().coerceAtMost(bitmap.height - top)

        if (width <= 10 || height <= 10) return@withContext emptyList()

        val region = Bitmap.createBitmap(bitmap, left, top, width, height)
        val classification = classifier.classify(bitmap, detection)

        // Pass 1: Primary format with ZXing
        val primaryResults = tryZxingDecode(region, classification.primaryFormat, bbox, DecodeResult.DecodePass.PRIMARY)
        results.addAll(primaryResults)

        // Pass 2: Secondary format (if classifier is not confident)
        if (classification.tryBothFormats && results.isEmpty()) {
            val secondaryFormat = when (classification.primaryFormat) {
                BarcodeFormat.QR_CODE -> BarcodeFormat.CODE_128
                BarcodeFormat.CODE_128 -> BarcodeFormat.QR_CODE
            }
            val secondaryResults = tryZxingDecode(region, secondaryFormat, bbox, DecodeResult.DecodePass.SECONDARY_FORMAT)
            for (sr in secondaryResults) {
                if (results.none { it.value == sr.value && it.format == sr.format }) {
                    results.add(sr)
                }
            }
        }

        // Pass 3: Full-frame decode as last resort (for tightly-cropped regions)
        if (results.isEmpty()) {
            val fullResults = tryZxingFullFrame(region, bbox)
            for (fr in fullResults) {
                if (results.none { it.value == fr.value && it.format == fr.format }) {
                    results.add(fr)
                }
            }
        }

        // Pass 4: Upscaled decode
        if (results.isEmpty() && width > 0 && height > 0) {
            val scale = 2
            val upscaled = Bitmap.createScaledBitmap(region, width * scale, height * scale, true)
            val upscaledResults = tryZxingFullFrame(upscaled, bbox)
            for (ur in upscaledResults) {
                if (results.none { it.value == ur.value && it.format == ur.format }) {
                    results.add(ur.copy(decodePass = DecodeResult.DecodePass.UPSCALED))
                }
            }
            if (upscaled !== region) upscaled.recycle()
        }

        // Pass 5: Native C++ decode (if available, as additional attempt)
        if (results.isEmpty() && nativeAvailable) {
            val nativeResults = tryNativeDecode(region, classification.primaryFormat, bbox)
            for (nr in nativeResults) {
                if (results.none { it.value == nr.value && it.format == nr.format }) {
                    results.add(nr)
                }
            }
        }

        region.recycle()
        return@withContext results
    }

    private suspend fun tryZxingDecode(
        bitmap: Bitmap,
        format: BarcodeFormat,
        bbox: BoundingBox,
        pass: DecodeResult.DecodePass
    ): List<DecodeResult> = withContext(decodeDispatcher) {
        try {
            zxingDecoder.decodeRegion(bitmap, bbox, format).map {
                it.copy(decodePass = pass)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun tryZxingFullFrame(
        bitmap: Bitmap,
        bbox: BoundingBox
    ): List<DecodeResult> = withContext(decodeDispatcher) {
        try {
            zxingDecoder.decodeFullFrame(bitmap).map {
                it.copy(boundingBox = bbox, decodePass = DecodeResult.DecodePass.PRIMARY)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun tryNativeDecode(
        bitmap: Bitmap,
        format: BarcodeFormat,
        bbox: BoundingBox
    ): List<DecodeResult> {
        if (!nativeAvailable) return emptyList()

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val grayData = ByteArray(width * height)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            grayData[i] = ((0.299 * r + 0.587 * g + 0.114 * b).toInt()).toByte()
        }

        return try {
            val decoded = nativeDecodeGray(grayData, width, height, format.id, preprocessingEnabled)
            decoded?.map { value ->
                DecodeResult(
                    value = value.trim(),
                    format = format,
                    decodeConfidence = 0.85f,
                    boundingBox = bbox,
                    decodePass = DecodeResult.DecodePass.PRIMARY
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * دیکد کل فریم - همه بارکدهای قابل مشاهده رو پیدا می‌کنه
     * این روش سریع‌تره ولی ممکنه بعضی بارکدها رو از دست بده
     * برای تکمیل detection-based decode استفاده می‌شه
     */
    suspend fun decodeFullFrame(bitmap: Bitmap): List<DecodeResult> = withContext(decodeDispatcher) {
        try {
            zxingDecoder.decodeFullFrame(bitmap)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun reset() {
        zxingDecoder.reset()
    }
}
