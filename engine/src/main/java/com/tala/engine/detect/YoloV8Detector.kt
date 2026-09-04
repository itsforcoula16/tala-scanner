package com.tala.engine.detect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.tala.engine.model.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

class YoloV8Detector(
    private val context: Context,
    private val modelPath: String = "yolov8n_barcode.onnx",
    private val inputSize: Int = 640,
    private val confidenceThreshold: Float = 0.5f,
    private val nmsIouThreshold: Float = 0.45f
) : Detector {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isLoaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(modelPath).use { it.readBytes() }
            ortSession = ortEnv?.createSession(modelBytes)
            isLoaded = true
        } catch (e: Exception) {
            isLoaded = false
            e.printStackTrace()
        }
    }

    override suspend fun detect(bitmap: Bitmap): List<DetectionResult> = withContext(Dispatchers.Default) {
        if (!isLoaded) return@withContext emptyList()

        try {
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputTensor = bitmapToTensor(resized)
            val inputName = ortSession?.inputNames?.first() ?: return@withContext emptyList()

            val results = ortSession?.run(mapOf(inputName to inputTensor)) ?: return@withContext emptyList()
            val output = results[0].value as Array<Array<FloatArray>>

            resized.recycle()
            inputTensor.close()
            results.close()

            parseOutput(output, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun bitmapToTensor(bitmap: Bitmap): OnnxTensor {
        val buffer = FloatBuffer.allocate(1 * 3 * inputSize * inputSize)
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF) / 255.0f
            val g = ((pixels[i] shr 8) and 0xFF) / 255.0f
            val b = (pixels[i] and 0xFF) / 255.0f
            buffer.put(r)
            buffer.put(g)
            buffer.put(b)
        }

        buffer.rewind()
        return OnnxTensor.createTensor(ortEnv!!, buffer, longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
    }

    private fun parseOutput(
        output: Array<Array<FloatArray>>,
        imageWidth: Int,
        imageHeight: Int
    ): List<DetectionResult> {
        val candidates = mutableListOf<DetectionResult>()
        val predictions = output[0]

        for (i in predictions.indices) {
            val row = predictions[i]
            // YOLOv8 output: [cx, cy, w, h, confidence, ...class_scores]
            if (row.size < 5) continue

            val cx = row[0]
            val cy = row[1]
            val w = row[2]
            val h = row[3]
            val objConf = row[4]

            if (objConf < confidenceThreshold) continue

            // Scale back to image coordinates
            val scaleX = imageWidth.toFloat() / inputSize
            val scaleY = imageHeight.toFloat() / inputSize

            val bbox = BoundingBox(
                left = (cx - w / 2f) * scaleX,
                top = (cy - h / 2f) * scaleY,
                width = w * scaleX,
                height = h * scaleY
            ).clamp(imageWidth, imageHeight)

            candidates.add(
                DetectionResult(
                    boundingBox = bbox,
                    detectionConfidence = objConf,
                    detectorType = DetectorType.ML_YOLO
                )
            )
        }

        return nms(candidates)
    }

    private fun nms(candidates: List<DetectionResult>): List<DetectionResult> {
        if (candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedByDescending { it.detectionConfidence }
        val selected = mutableListOf<DetectionResult>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            selected.add(sorted[i])

            for (j in i + 1 until sorted.size) {
                if (!active[j]) continue
                if (sorted[i].boundingBox.overlaps(sorted[j].boundingBox)) {
                    val iou = calculateIoU(sorted[i].boundingBox, sorted[j].boundingBox)
                    if (iou > nmsIouThreshold) {
                        active[j] = false
                    }
                }
            }
        }

        return selected
    }

    private fun calculateIoU(a: com.tala.engine.model.BoundingBox, b: com.tala.engine.model.BoundingBox): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        if (interRight <= interLeft || interBottom <= interTop) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val unionArea = a.area + b.area - interArea

        return if (unionArea > 0) interArea / unionArea else 0f
    }

    override fun release() {
        ortSession?.close()
        ortEnv?.close()
        ortSession = null
        ortEnv = null
        isLoaded = false
    }
}
