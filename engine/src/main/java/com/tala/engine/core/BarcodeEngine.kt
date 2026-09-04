package com.tala.engine.core

import android.content.Context
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.tala.engine.camera.CameraManager
import com.tala.engine.interfaces.ScanCallback
import com.tala.engine.interfaces.ScanError
import com.tala.engine.model.BarcodeResult
import com.tala.engine.pipeline.FrameProcessor
import com.tala.engine.pipeline.ScanPipeline
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BarcodeEngine(
    private val context: Context,
    private val config: BarcodeEngineConfig = BarcodeEngineConfig()
) {
    private var state: EngineState = EngineState.IDLE
    private var pipeline: ScanPipeline? = null
    private var cameraManager: CameraManager? = null
    private var callback: ScanCallback? = null

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Public result flows
    private val _barcodeFlow = MutableSharedFlow<BarcodeResult>(extraBufferCapacity = 128)
    val barcodeFlow: SharedFlow<BarcodeResult> = _barcodeFlow.asSharedFlow()

    private val _sessionResults = MutableStateFlow<List<BarcodeResult>>(emptyList())
    val sessionResults: StateFlow<List<BarcodeResult>> = _sessionResults.asStateFlow()

    fun setCallback(callback: ScanCallback) {
        this.callback = callback
    }

    suspend fun initialize() {
        if (state != EngineState.IDLE && state != EngineState.PAUSED) {
            Log.w(TAG, "Engine already initialized or running, state=$state")
            return
        }

        state = EngineState.INITIALIZING

        try {
            pipeline = ScanPipeline(context, config)
            pipeline!!.initialize()

            cameraManager = CameraManager(context)
            cameraManager!!.initialize()

            // Collect results from pipeline
            engineScope.launch {
                pipeline!!.resultsFlow.collect { result ->
                    _barcodeFlow.emit(result)
                    _sessionResults.value = pipeline!!.getConfirmedResults()
                    callback?.onBarcodeConfirmed(result)
                }
            }

            state = EngineState.READY
            Log.d(TAG, "Engine initialized successfully")

        } catch (e: Exception) {
            state = EngineState.ERROR
            callback?.onError(ScanError.PipelineError("init", e))
            Log.e(TAG, "Engine initialization failed", e)
        }
    }

    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView? = null
    ) {
        if (!state.canStart()) {
            callback?.onError(ScanError.EngineNotInitialized)
            return
        }

        try {
            val frameProcessor = FrameProcessor { bitmap ->
                pipeline?.processFrame(bitmap)
            }

            cameraManager!!.bindAnalysis(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                resolution = config.scanConfiguration.performanceConfig.resolution,
                imageAnalyzer = frameProcessor
            )

            state = EngineState.SCANNING
            Log.d(TAG, "Scanning started")

        } catch (e: Exception) {
            state = EngineState.ERROR
            callback?.onError(ScanError.CameraInitFailed)
            Log.e(TAG, "Failed to start scanning", e)
        }
    }

    fun pause() {
        if (state == EngineState.SCANNING) {
            state = EngineState.PAUSED
            Log.d(TAG, "Scanning paused")
        }
    }

    fun resume() {
        if (state == EngineState.PAUSED) {
            state = EngineState.SCANNING
            Log.d(TAG, "Scanning resumed")
        }
    }

    fun resetSession() {
        pipeline?.reset()
        _sessionResults.value = emptyList()
        Log.d(TAG, "Session reset")
    }

    fun getSessionResults(): List<BarcodeResult> {
        return pipeline?.getConfirmedResults() ?: emptyList()
    }

    fun getPerformanceStats(): String {
        return pipeline?.getPerformanceStats()?.toString() ?: "No stats available"
    }

    fun getState(): EngineState = state

    fun shutdown() {
        state = EngineState.IDLE
        engineScope.cancel()
        pipeline?.shutdown()
        cameraManager?.shutdown()
        pipeline = null
        cameraManager = null
        Log.d(TAG, "Engine shut down")
    }

    companion object {
        private const val TAG = "TalaEngine"
    }
}
