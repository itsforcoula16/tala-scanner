package com.tala.engine.pipeline

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

class FrameProcessor(
    private val onFrameReady: suspend (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                // Use runBlocking only for the suspend callback
                kotlinx.coroutines.runBlocking {
                    onFrameReady(bitmap)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Y plane
        yBuffer.get(nv21, 0, ySize)

        // VU plane (interleaved for NV21)
        val uvPixelStride = image.planes[1].pixelStride
        val uvRowStride = image.planes[1].rowStride
        val uvWidth = image.width / 2
        val uvHeight = image.height / 2

        var pos = ySize
        if (uvPixelStride == 2) {
            // Interleaved UV
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val vuIndex = row * uvRowStride + col * uvPixelStride
                    if (vuIndex + 1 < vBuffer.capacity()) {
                        nv21[pos++] = vBuffer.get(vuIndex)
                        nv21[pos++] = uBuffer.get(vuIndex)
                    }
                }
            }
        } else {
            // Planar UV
            vBuffer.position(0)
            uBuffer.position(0)
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val vuIndex = row * uvRowStride + col
                    if (vuIndex < vSize && vuIndex < uSize) {
                        nv21[pos++] = vBuffer.get(vuIndex)
                        nv21[pos++] = uBuffer.get(vuIndex)
                    }
                }
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
        val jpegBytes = out.toByteArray()

        val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        // Apply rotation
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    }
}
