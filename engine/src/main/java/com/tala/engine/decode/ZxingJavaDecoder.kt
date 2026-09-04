package com.tala.engine.decode

import android.graphics.Bitmap
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.datamatrix.decoder.DecodedBitStreamParser
import com.tala.engine.model.BarcodeFormat
import com.tala.engine.model.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumMap

class ZxingJavaDecoder {

    private val qrReader = createReader(
        setOf(BarcodeFormat.QR_CODE).map { com.google.zxing.BarcodeFormat.QR_CODE }
    )

    private val code128Reader = createReader(
        setOf(BarcodeFormat.CODE_128).map { com.google.zxing.BarcodeFormat.CODE_128 }
    )

    private val multiReader = createReader(
        listOf(
            com.google.zxing.BarcodeFormat.QR_CODE,
            com.google.zxing.BarcodeFormat.CODE_128,
            com.google.zxing.BarcodeFormat.CODE_39,
            com.google.zxing.BarcodeFormat.CODE_93,
            com.google.zxing.BarcodeFormat.EAN_13,
            com.google.zxing.BarcodeFormat.EAN_8,
            com.google.zxing.BarcodeFormat.DATA_MATRIX,
            com.google.zxing.BarcodeFormat.ITF
        )
    )

    private fun createReader(formats: List<com.google.zxing.BarcodeFormat>): MultiFormatReader {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.POSSIBLE_FORMATS] = formats
        hints[DecodeHintType.TRY_HARDER] = true
        hints[DecodeHintType.PURE_BARCODE] = false

        val reader = MultiFormatReader()
        reader.setHints(hints)
        return reader
    }

    suspend fun decodeRegion(
        bitmap: Bitmap,
        region: BoundingBox,
        formatHint: com.tala.engine.model.BarcodeFormat?
    ): List<DecodeResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<DecodeResult>()

        val left = region.left.toInt().coerceAtLeast(0)
        val top = region.top.toInt().coerceAtLeast(0)
        val width = region.width.toInt().coerceAtMost(bitmap.width - left)
        val height = region.height.toInt().coerceAtMost(bitmap.height - top)

        if (width <= 10 || height <= 10) return@withContext emptyList()

        val regionBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)

        // Pass 1: Try with format hint
        val pass1Results = decodeWithReader(regionBitmap, formatHint)
        results.addAll(pass1Results)

        // Pass 2: If no result, try all formats
        if (results.isEmpty()) {
            val pass2Results = decodeWithReader(regionBitmap, null)
            results.addAll(pass2Results)
        }

        // Pass 3: If still no result, try with enhanced image
        if (results.isEmpty()) {
            val enhanced = enhanceForDecode(regionBitmap)
            if (enhanced != regionBitmap) {
                val pass3Results = decodeWithReader(enhanced, null)
                results.addAll(pass3Results)
                enhanced.recycle()
            }
        }

        regionBitmap.recycle()

        return@withContext results.map { it.copy(boundingBox = region) }
    }

    suspend fun decodeFullFrame(bitmap: Bitmap): List<DecodeResult> = withContext(Dispatchers.Default) {
        try {
            val source = RGBLuminanceSource(
                bitmap.width, bitmap.height,
                getPixels(bitmap)
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val results = mutableListOf<DecodeResult>()

            try {
                val rawResult = multiReader.decodeWithState(binaryBitmap)
                if (rawResult != null) {
                    results.add(rawResultToDecodeResult(rawResult, bitmap.width, bitmap.height))
                }
            } catch (_: NotFoundException) {}
            catch (_: FormatException) {}
            catch (_: ChecksumException) {}

            try {
                multiReader.reset()
            } catch (_: Exception) {}

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeWithReader(
        bitmap: Bitmap,
        formatHint: com.tala.engine.model.BarcodeFormat?
    ): List<DecodeResult> {
        return try {
            val pixels = getPixels(bitmap)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val reader = when (formatHint) {
                com.tala.engine.model.BarcodeFormat.QR_CODE -> qrReader
                com.tala.engine.model.BarcodeFormat.CODE_128 -> code128Reader
                else -> multiReader
            }

            val results = mutableListOf<DecodeResult>()

            try {
                val rawResult = reader.decodeWithState(binaryBitmap)
                if (rawResult != null) {
                    results.add(rawResultToDecodeResult(rawResult, bitmap.width, bitmap.height))
                }
            } catch (_: NotFoundException) {}
            catch (_: FormatException) {}
            catch (_: ChecksumException) {}

            try {
                reader.reset()
            } catch (_: Exception) {}

            // Try multi-barcode detection
            if (results.isEmpty() && formatHint == null) {
                try {
                    val multiResult = MultiFormatReader().decode(binaryBitmap)
                    if (multiResult != null) {
                        results.add(rawResultToDecodeResult(multiResult, bitmap.width, bitmap.height))
                    }
                } catch (_: Exception) {}
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun rawResultToDecodeResult(
        raw: com.google.zxing.Result,
        imageWidth: Int,
        imageHeight: Int
    ): DecodeResult {
        val format = when (raw.barcodeFormat) {
            com.google.zxing.BarcodeFormat.QR_CODE -> com.tala.engine.model.BarcodeFormat.QR_CODE
            com.google.zxing.BarcodeFormat.CODE_128 -> com.tala.engine.model.BarcodeFormat.CODE_128
            com.google.zxing.BarcodeFormat.CODE_39 -> com.tala.engine.model.BarcodeFormat.CODE_128
            com.google.zxing.BarcodeFormat.CODE_93 -> com.tala.engine.model.BarcodeFormat.CODE_128
            else -> com.tala.engine.model.BarcodeFormat.QR_CODE
        }

        val points = raw.resultPoints
        val bbox = if (points != null && points.size >= 2) {
            val xs = points.map { it.x }
            val ys = points.map { it.y }
            BoundingBox(
                left = (xs.minOrNull() ?: 0f).coerceAtLeast(0f),
                top = (ys.minOrNull() ?: 0f).coerceAtLeast(0f),
                width = ((xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)).coerceAtLeast(1f),
                height = ((ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)).coerceAtLeast(1f)
            )
        } else {
            BoundingBox(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())
        }

        return DecodeResult(
            value = raw.text ?: "",
            format = format,
            decodeConfidence = 0.90f,
            boundingBox = bbox,
            decodePass = DecodeResult.DecodePass.PRIMARY
        )
    }

    private fun getPixels(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    private fun enhanceForDecode(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to grayscale and apply adaptive threshold
        val gray = IntArray(width * height) { i ->
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // Simple adaptive threshold
        val blockSize = 15
        val half = blockSize / 2
        val output = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (dy in -half..half) {
                    for (dx in -half..half) {
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val nx = (x + dx).coerceIn(0, width - 1)
                        sum += gray[ny * width + nx]
                        count++
                    }
                }
                val mean = sum / count
                val value = if (gray[y * width + x] > mean - 10) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                output[y * width + x] = value
            }
        }

        enhanced.setPixels(output, 0, width, 0, 0, width, height)
        return enhanced
    }

    fun reset() {
        try { multiReader.reset() } catch (_: Exception) {}
        try { qrReader.reset() } catch (_: Exception) {}
        try { code128Reader.reset() } catch (_: Exception) {}
    }
}
