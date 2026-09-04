package com.tala.engine.interfaces

import com.tala.engine.model.BarcodeResult

interface ScanCallback {
    fun onFrameProcessed(
        detectedBarcodes: List<BarcodeResult>,
        frameProcessingTimeMs: Long
    )
    fun onBarcodeConfirmed(result: BarcodeResult)
    fun onError(error: ScanError)
}

sealed class ScanError(val message: String) {
    object CameraPermissionDenied : ScanError("Camera permission denied")
    object CameraInitFailed : ScanError("Failed to initialize camera")
    object EngineNotInitialized : ScanError("Engine not initialized. Call start() first")
    data class NativeError(val code: Int, val detail: String) :
        ScanError("Native error $code: $detail")
    data class PipelineError(val stage: String, val cause: Throwable) :
        ScanError("Pipeline error in $stage: ${cause.message}")
}
