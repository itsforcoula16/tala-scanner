package com.tala.engine.core

import com.tala.engine.model.ScanConfiguration

data class BarcodeEngineConfig(
    val scanConfiguration: ScanConfiguration = ScanConfiguration(),
    val enableNativeLogs: Boolean = false,
    val maxConcurrentDecoders: Int = 4,
    val frameBufferCapacity: Int = 5,
    val yoloModelPath: String = "yolov8n_barcode.onnx",
    val enableDetection: Boolean = true,
    val enableClassicFallback: Boolean = true,
    val enableFrameReprocessing: Boolean = true
) {
    companion object {
        /**
         * بهینه برای اسکن سریع انبار طلا
         *
         * سناریو:
         * - ۱۰۰۰+ قطعه طلا در یک کادر
         * - فاصله دوربین: ۵ تا ۵۰+ سانتی‌متر
         * - لیبل بارکد: ۴۰mm به بالا
         * - هدف: سریع‌ترین و دقیق‌ترین اسکن همزمان
         * - بدون تکرار، بدون خطا
         *
         * نکات مهم:
         * - در ۵۰cm: بارکد 40mm ≈ 40px → نیاز به upscale 4x
         * - در ۲۰cm: بارکد 40mm ≈ 100px → upscale 2x کافیه
         * - در ۵cm:  بارکد 40mm ≈ 400px → بدون upscale هم خونده می‌شه
         */
        fun forSmallBarcodes(): BarcodeEngineConfig {
            return BarcodeEngineConfig(
                scanConfiguration = ScanConfiguration(
                    minConfidence = 0.50f,      // پایین برای بارکد دور
                    maxBarcodesPerFrame = 200,   // ★ تا ۲۰۰ بارکد همزمان
                    accumulationConfig = com.tala.engine.model.AccumulationConfig(
                        minConfirmations = 2,    // ★ ۲ فریم کافیه (سرعت)
                        accumulationWindowMs = 800L, // ★ سریع confirm بشه
                        autoClearThreshold = 2,  // ★ سریع پاک بشه
                        positionTolerancePx = 40f,
                        forceConfirmAfterWindow = true
                    ),
                    preprocessingConfig = com.tala.engine.model.PreprocessingConfig(
                        enableAdaptiveThreshold = true,
                        enableDeSkew = true,
                        enableClahe = true,
                        enableMultiPassDecode = true,
                        upscaleFactor = 3f,      // ★ 3x برای بارکد کوچیک/دور
                        angleSweepRange = 8,     // ±8 درجه
                        angleSweepStep = 2
                    ),
                    performanceConfig = com.tala.engine.model.PerformanceConfig(
                        resolution = com.tala.engine.model.Resolution.FHD_1080P, // ★ 1080p
                        enableRoiCaching = true,
                        roiCacheFrameSkip = 3    // هر 3 فریم full scan
                    )
                ),
                maxConcurrentDecoders = 12,     // ★ 12 تا موازی (سرعت)
                frameBufferCapacity = 5,
                enableClassicFallback = true,
                enableFrameReprocessing = true
            )
        }

        /**
         * بهینه برای انبارگردانی سریع طلا
         * (اسکن پشت سر هم چندین قطعه)
         */
        fun forGoldInventory(): BarcodeEngineConfig {
            return BarcodeEngineConfig(
                scanConfiguration = ScanConfiguration(
                    minConfidence = 0.60f,
                    maxBarcodesPerFrame = 100,
                    accumulationConfig = com.tala.engine.model.AccumulationConfig(
                        minConfirmations = 2,
                        accumulationWindowMs = 1000L,
                        autoClearThreshold = 3,
                        forceConfirmAfterWindow = true
                    ),
                    preprocessingConfig = com.tala.engine.model.PreprocessingConfig(
                        enableAdaptiveThreshold = true,
                        enableDeSkew = true,
                        enableClahe = true,
                        enableMultiPassDecode = true,
                        upscaleFactor = 2.5f,       // 2.5x برای سایز متوسط
                        angleSweepRange = 8,
                        angleSweepStep = 2
                    ),
                    performanceConfig = com.tala.engine.model.PerformanceConfig(
                        resolution = com.tala.engine.model.Resolution.HD_720P,
                        enableRoiCaching = true
                    )
                ),
                maxConcurrentDecoders = 8,
                frameBufferCapacity = 5,
                enableClassicFallback = true,
                enableFrameReprocessing = true
            )
        }

        /**
         * بهینه برای فاصله ۵۰+ سانتیمتر
         *
         * سناریو: کاربر گوشی رو دورتر نگه می‌داره
         * - لیبل 20mm در 50cm → ~110px → upscale 4x → ~440px ✓
         * - لیبل 25mm در 50cm → ~137px → upscale 3x → ~411px ✓
         * - لیبل 29mm در 50cm → ~158px → upscale 3x → ~474px ✓
         *
         * نکته: 4K رزولوشن کلیدی‌ترین فاکتور برای فاصله زیاده
         */
        fun forLongRange(): BarcodeEngineConfig {
            return BarcodeEngineConfig(
                scanConfiguration = ScanConfiguration(
                    minConfidence = 0.45f,       // پایین‌تر چون بارکد ریزتره
                    maxBarcodesPerFrame = 100,
                    accumulationConfig = com.tala.engine.model.AccumulationConfig(
                        minConfirmations = 3,     // ۳ فریم برای تأیید
                        accumulationWindowMs = 2000L,
                        autoClearThreshold = 4,
                        positionTolerancePx = 60f, // حرکت آزادتر
                        forceConfirmAfterWindow = true
                    ),
                    preprocessingConfig = com.tala.engine.model.PreprocessingConfig(
                        enableAdaptiveThreshold = true,
                        enableDeSkew = true,
                        enableClahe = true,
                        enableMultiPassDecode = true,
                        upscaleFactor = 4f,        // ★ 4x بزرگنمایی
                        angleSweepRange = 12,      // ±12 درجه
                        angleSweepStep = 2
                    ),
                    performanceConfig = com.tala.engine.model.PerformanceConfig(
                        resolution = com.tala.engine.model.Resolution.UHD_4K, // ★ 4K
                        enableRoiCaching = true,
                        roiCacheFrameSkip = 4,
                        enableAdaptiveResolution = true
                    )
                ),
                maxConcurrentDecoders = 10,
                frameBufferCapacity = 8,
                enableClassicFallback = true,
                enableFrameReprocessing = true
            )
        }

        /**
         * بهینه برای فاصله ۱۰۰+ سانتیمتر (حداکثر برد)
         *
         * سناریو: اسکن از فاصله دور
         * - لیبل 29mm در 100cm → ~79px → upscale 5x → ~395px ✓
         * - لیبل 25mm در 100cm → ~68px → upscale 5x → ~340px ⚠️
         * - لیبل 20mm در 100cm → ~55px → upscale 5x → ~275px ⚠️
         *
         * محدودیت فیزیکی: زیر 50px عملاً غیرممکنه
         */
        fun forVeryLongRange(): BarcodeEngineConfig {
            return BarcodeEngineConfig(
                scanConfiguration = ScanConfiguration(
                    minConfidence = 0.40f,       // خیلی پایین
                    maxBarcodesPerFrame = 50,
                    accumulationConfig = com.tala.engine.model.AccumulationConfig(
                        minConfirmations = 4,     // ★ ۴ فریم برای اطمینان
                        accumulationWindowMs = 3000L,
                        autoClearThreshold = 5,
                        positionTolerancePx = 80f,
                        forceConfirmAfterWindow = true
                    ),
                    preprocessingConfig = com.tala.engine.model.PreprocessingConfig(
                        enableAdaptiveThreshold = true,
                        enableDeSkew = true,
                        enableClahe = true,
                        enableMultiPassDecode = true,
                        upscaleFactor = 5f,        // ★★★ 5x بزرگنمایی
                        angleSweepRange = 15,      // ±15 درجه
                        angleSweepStep = 1         // هر 1 درجه
                    ),
                    performanceConfig = com.tala.engine.model.PerformanceConfig(
                        resolution = com.tala.engine.model.Resolution.UHD_4K, // ★ 4K اجباری
                        enableRoiCaching = true,
                        roiCacheFrameSkip = 3,
                        enableAdaptiveResolution = true,
                        maxNativeMemoryMb = 300
                    )
                ),
                maxConcurrentDecoders = 8,
                frameBufferCapacity = 10,        // بافر بزرگ برای فرصت بیشتر
                enableClassicFallback = true,
                enableFrameReprocessing = true
            )
        }

        fun forMaximumAccuracy(): BarcodeEngineConfig {
            return BarcodeEngineConfig(
                scanConfiguration = ScanConfiguration(
                    minConfidence = 0.75f,
                    accumulationConfig = com.tala.engine.model.AccumulationConfig(
                        minConfirmations = 4,
                        accumulationWindowMs = 3000L,
                        autoClearThreshold = 5
                    ),
                    preprocessingConfig = com.tala.engine.model.PreprocessingConfig(
                        enableMultiPassDecode = true,
                        upscaleFactor = 3f,
                        angleSweepRange = 7,
                        angleSweepStep = 1
                    )
                ),
                maxConcurrentDecoders = 4,
                enableFrameReprocessing = true
            )
        }

        fun forMaximumSpeed(): BarcodeEngineConfig {
            return BarcodeEngineConfig(
                scanConfiguration = ScanConfiguration(
                    minConfidence = 0.60f,
                    accumulationConfig = com.tala.engine.model.AccumulationConfig(
                        minConfirmations = 2,
                        accumulationWindowMs = 800L,
                        autoClearThreshold = 2
                    ),
                    preprocessingConfig = com.tala.engine.model.PreprocessingConfig(
                        enableDeSkew = false,
                        enableClahe = false,
                        enableMultiPassDecode = false
                    ),
                    performanceConfig = com.tala.engine.model.PerformanceConfig(
                        resolution = com.tala.engine.model.Resolution.SD_480P,
                        enableRoiCaching = true
                    )
                ),
                maxConcurrentDecoders = 8,
                enableFrameReprocessing = false
            )
        }
    }
}
