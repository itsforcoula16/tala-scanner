package com.tala.engine.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.tala.engine.model.Resolution
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentAnalysis: ImageAnalysis? = null

    val analysisExecutorService: ExecutorService get() = analysisExecutor

    suspend fun initialize(): ProcessCameraProvider {
        return suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val provider = future.get()
                    cameraProvider = provider
                    cont.resume(provider)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    fun bindAnalysis(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?,
        resolution: Resolution,
        imageAnalyzer: ImageAnalysis.Analyzer
    ): ImageAnalysis {
        val provider = cameraProvider
            ?: throw IllegalStateException("CameraManager not initialized. Call initialize() first")

        val preview = Preview.Builder().build().also {
            previewView?.surfaceProvider = it.surfaceProvider
        }

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(resolution.width, resolution.height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(analysisExecutor, imageAnalyzer)
            }

        currentAnalysis = analysis

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)

        return analysis
    }

    fun setResolution(resolution: Resolution) {
        currentAnalysis?.targetResolution = Size(resolution.width, resolution.height)
    }

    fun shutdown() {
        currentAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
        cameraProvider = null
        currentAnalysis = null
    }
}
