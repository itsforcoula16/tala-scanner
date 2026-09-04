package com.tala.engine.model

data class ScanConfiguration(
    val supportedFormats: Set<BarcodeFormat> = setOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128),
    val minConfidence: Float = 0.70f,
    val maxBarcodesPerFrame: Int = 50,
    val accumulationConfig: AccumulationConfig = AccumulationConfig(),
    val preprocessingConfig: PreprocessingConfig = PreprocessingConfig(),
    val performanceConfig: PerformanceConfig = PerformanceConfig()
)

data class AccumulationConfig(
    val minConfirmations: Int = 3,
    val accumulationWindowMs: Long = 2000L,
    val autoClearThreshold: Int = 4,
    val positionTolerancePx: Float = 50f,
    val forceConfirmAfterWindow: Boolean = true
)

data class PreprocessingConfig(
    val enableAdaptiveThreshold: Boolean = true,
    val enableDeSkew: Boolean = true,
    val enableClahe: Boolean = true,
    val enableMultiPassDecode: Boolean = true,
    val upscaleFactor: Float = 2f,
    val angleSweepRange: Int = 5,
    val angleSweepStep: Int = 1
)

data class PerformanceConfig(
    val targetFps: Int = 30,
    val resolution: Resolution = Resolution.HD_720P,
    val enableRoiCaching: Boolean = true,
    val roiCacheFrameSkip: Int = 10,
    val enableAdaptiveResolution: Boolean = true,
    val maxNativeMemoryMb: Int = 200
)

enum class Resolution(val width: Int, val height: Int) {
    SD_480P(640, 480),
    HD_720P(1280, 720),
    FHD_1080P(1920, 1080),
    UHD_4K(3840, 2160);

    val megapixels: Float get() = (width * height) / 1_000_000f
}
