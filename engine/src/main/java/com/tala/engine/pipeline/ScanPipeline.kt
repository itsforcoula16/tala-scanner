package com.tala.engine.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tala.engine.buffer.FrameBuffer
import com.tala.engine.core.BarcodeEngineConfig
import com.tala.engine.decode.DecodeResult
import com.tala.engine.decode.MultiFormatDecoder
import com.tala.engine.detect.ClassicDetector
import com.tala.engine.detect.DetectionResult
import com.tala.engine.detect.Detector
import com.tala.engine.detect.YoloV8Detector
import com.tala.engine.model.BarcodeFormat
import com.tala.engine.model.BarcodeResult
import com.tala.engine.perf.PerformanceMonitor
import com.tala.engine.validate.Code128Validator
import com.tala.engine.validate.ConfidenceScorer
import com.tala.engine.validate.QrValidator
import com.tala.engine.validate.Validator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Pipeline اسکن بارکد با ۷ لایه کنترل
 * مطابق معماری Scanbot اما اختصاصی Tala
 *
 * مسیر کامل:
 * ┌─────────────────────────────────────────────┐
 * │  فریم دوربین                                │
 * │      ↓                                      │
 * │  ① AutoSnappingController                   │
 * │      ↓                                      │
 * │  ② SuccessFrameDebouncer                    │
 * │      ↓                                      │
 * │  ③ BarcodeFilter                            │
 * │      ↓                                      │
 * │  ④ Detection + Decode + Validation          │
 * │      ↓                                      │
 * │  ⑤ OneDConfirmationTracker                  │
 * │      ↓                                      │
 * │  ⑥ AccumulatedResultsVerifier               │
 * │      ↓                                      │
 * │  ⑦ ResultAccumulator                        │
 * │      ↓                                      │
 * │  ✅ نتیجه نهایی (بدون تکرار + دقیق)        │
 * └─────────────────────────────────────────────┘
 */
class ScanPipeline(
    private val context: Context,
    private val config: BarcodeEngineConfig
) {
    // ─── Detection & Decode ───
    private var detector: Detector? = null
    private var classicDetector: ClassicDetector? = null
    private var decoder: MultiFormatDecoder? = null

    // ─── ۷ لایه کنترل ───
    private val autoSnapping = AutoSnappingController().apply {
        setSensitivity(0.8f)    // حساسیت بالا برای اسکن سریع
        setEnabled(true)
    }
    private val debouncer = SuccessFrameDebouncer().apply {
        setInterval(config.scanConfiguration.accumulationConfig.accumulationWindowMs)
    }
    private val barcodeFilter = BarcodeFilter()
    private val oneDTracker = OneDConfirmationTracker()
    private val verifier = AccumulatedResultsVerifier()
    private val resultAccumulator = ResultAccumulator(
        ResultAccumulationConfiguration(
            confirmationMethod = ConfirmationMethod.INTERPOLATE,
            minConfirmations = config.scanConfiguration.accumulationConfig.minConfirmations,
            minConfidenceForStableField = config.scanConfiguration.minConfidence,
            autoClearThreshold = config.scanConfiguration.accumulationConfig.autoClearThreshold,
            accumulationWindowMs = config.scanConfiguration.accumulationConfig.accumulationWindowMs
        )
    )

    // ─── Supporting ───
    private var frameBuffer: FrameBuffer? = null
    private var confidenceScorer: ConfidenceScorer? = null
    private var validators: List<Validator> = emptyList()
    private val performanceMonitor = PerformanceMonitor()

    // ─── Output Flows ───
    private val _resultsFlow = MutableSharedFlow<BarcodeResult>(extraBufferCapacity = 128)
    val resultsFlow: SharedFlow<BarcodeResult> = _resultsFlow.asSharedFlow()

    private val _allResultsFlow = MutableSharedFlow<List<BarcodeResult>>(extraBufferCapacity = 64)
    val allResultsFlow: SharedFlow<List<BarcodeResult>> = _allResultsFlow.asSharedFlow()

    private var frameCounter = 0L
    private val pipelineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend fun initialize() {
        Log.d(TAG, "Initializing scan pipeline with 7-layer control...")

        // Initialize detectors
        if (config.enableDetection) {
            val yoloDetector = YoloV8Detector(context, config.yoloModelPath)
            try {
                yoloDetector.load()
                detector = yoloDetector
                Log.d(TAG, "YOLO detector loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load YOLO, using classic only", e)
            }
        }

        if (config.enableClassicFallback || detector == null) {
            classicDetector = ClassicDetector()
        }

        // Initialize decoder
        decoder = MultiFormatDecoder(
            context = context,
            maxConcurrentDecoders = config.maxConcurrentDecoders,
            preprocessingEnabled = config.scanConfiguration.preprocessingConfig.enableAdaptiveThreshold
        )

        // Initialize frame buffer
        frameBuffer = FrameBuffer(config.frameBufferCapacity)

        // Initialize validators
        validators = listOf(Code128Validator(), QrValidator())

        // Initialize confidence scorer
        confidenceScorer = ConfidenceScorer()

        performanceMonitor.start()
        Log.d(TAG, "Pipeline initialized with 7-layer control system")
    }

    /**
     * پردازش فریم دوربین
     * از ۷ لایه کنترل عبور می‌کنه
     */
    suspend fun processFrame(bitmap: Bitmap) {
        frameCounter++
        val startTime = System.currentTimeMillis()
        val currentTimeMs = System.currentTimeMillis()

        try {
            // ════════════════════════════════════════════════════════
            // لایه ۱: AutoSnappingController
            // بررسی آیا فریم مناسب اسکن خودکار هست
            // ════════════════════════════════════════════════════════
            // (still need to detect first, so we check after detection)

            // ════════════════════════════════════════════════════════
            // Detection + Decode + Validation
            // ════════════════════════════════════════════════════════
            val detectionStart = System.currentTimeMillis()
            val detections = detectBarcodes(bitmap)
            val detectionTime = System.currentTimeMillis() - detectionStart

            val decodeStart = System.currentTimeMillis()
            val allDecoded = mutableListOf<DecodeResult>()

            if (detections.isNotEmpty()) {
                val regionDecoded = decoder?.decodeAll(bitmap, detections) ?: emptyList()
                allDecoded.addAll(regionDecoded)
            }

            // Full-frame decode هر N فریم
            if (frameCounter % config.scanConfiguration.performanceConfig.roiCacheFrameSkip == 0L || detections.isEmpty()) {
                val fullFrameDecoded = decoder?.decodeFullFrame(bitmap) ?: emptyList()
                for (ff in fullFrameDecoded) {
                    if (allDecoded.none { it.value == ff.value && it.format == ff.format }) {
                        allDecoded.add(ff)
                    }
                }
            }
            val decodeTime = System.currentTimeMillis() - decodeStart

            // Validation + Confidence Scoring
            val validated = validateAndScore(allDecoded, detections)

            // ════════════════════════════════════════════════════════
            // لایه ۱: AutoSnappingController
            // ════════════════════════════════════════════════════════
            val shouldAutoSnap = autoSnapping.shouldSnap(validated, currentTimeMs)

            // ════════════════════════════════════════════════════════
            // لایه ۲: SuccessFrameDebouncer
            // رد کردن تکرار بعد از اسکن موفق
            // ════════════════════════════════════════════════════════
            val afterDebounce = validated.filter { barcode ->
                !debouncer.shouldSkip(barcode.value, currentTimeMs)
            }

            // ════════════════════════════════════════════════════════
            // لایه ۳: BarcodeFilter
            // فیلتر بارکدهای تکراری
            // ════════════════════════════════════════════════════════
            val afterFilter = afterDebounce.filter { barcode ->
                barcodeFilter.acceptsBarcode(barcode)
            }

            // ════════════════════════════════════════════════════════
            // لایه ۵: OneDConfirmationTracker
            // تأیید بارکدهای یک‌بعدی
            // ════════════════════════════════════════════════════════
            val afterOneD = afterFilter.filter { barcode ->
                when (barcode.format) {
                    BarcodeFormat.CODE_128 -> {
                        oneDTracker.trackFrame(barcode.value)
                    }
                    else -> true // QR و فرمت‌های دیگر نیاز به تأیید یک‌بعدی ندارن
                }
            }

            // ════════════════════════════════════════════════════════
            // لایه ۶: AccumulatedResultsVerifier
            // تأیید نتایج انباشت شده
            // ════════════════════════════════════════════════════════
            verifier.addFrame(afterOneD)
            val afterVerify = afterOneD.filter { barcode ->
                verifier.isVerified(barcode.value) || frameCounter <= 5
            }

            // ════════════════════════════════════════════════════════
            // لایه ۷: ResultAccumulator
            // تجمع و تأیید نهایی
            // ════════════════════════════════════════════════════════
            val newlyConfirmed = resultAccumulator.processFrame(afterVerify, currentTimeMs)

            // ════════════════════════════════════════════════════════
            // خروجی نهایی
            // ════════════════════════════════════════════════════════
            for (result in newlyConfirmed) {
                // ثبت در فیلتر و debounce
                barcodeFilter.markAccepted(result)
                debouncer.activate(result.value, currentTimeMs)

                // ارسال نتیجه
                _resultsFlow.emit(result)
            }

            if (afterOneD.isNotEmpty()) {
                _allResultsFlow.emit(afterOneD)
            }

            // Log performance
            if (frameCounter % 100 == 0L) {
                performanceMonitor.logStats()
                Log.d(TAG, "Frame $frameCounter: " +
                        "Confirmed=${resultAccumulator.getConfirmedCount()}, " +
                        "Tracked=${resultAccumulator.getTrackedCount()}, " +
                        "Filter=${barcodeFilter.getAcceptedCount()}")
            }

            performanceMonitor.onFrameComplete(detectionTime, decodeTime, 0)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame $frameCounter", e)
        }
    }

    private suspend fun detectBarcodes(bitmap: Bitmap): List<DetectionResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<DetectionResult>()

        detector?.let {
            try {
                val mlResults = it.detect(bitmap)
                results.addAll(mlResults)
            } catch (e: Exception) {
                Log.e(TAG, "ML detection failed", e)
            }
        }

        classicDetector?.let {
            try {
                val classicResults = it.detect(bitmap)
                for (cr in classicResults) {
                    if (results.none { mr -> mr.boundingBox.overlaps(cr.boundingBox, 0.5f) }) {
                        results.add(cr)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Classic detection failed", e)
            }
        }

        results.sortedByDescending { it.detectionConfidence }
            .take(config.scanConfiguration.maxBarcodesPerFrame)
    }

    private suspend fun validateAndScore(
        decoded: List<DecodeResult>,
        detections: List<DetectionResult>
    ): List<BarcodeResult> = withContext(Dispatchers.Default) {
        decoded.mapNotNull { decodeResult ->
            val matchingDetection = detections.find {
                it.boundingBox.overlaps(decodeResult.boundingBox, 0.5f)
            }

            val validation = validateDecodeResult(decodeResult)

            val confidence = confidenceScorer?.calculateScore(
                decodeResult = decodeResult,
                detection = matchingDetection,
                isValid = validation.isValid,
                validationScore = validation.score,
                previousConfirmations = 0
            ) ?: 0.5f

            if (confidence >= config.scanConfiguration.minConfidence) {
                BarcodeResult(
                    value = decodeResult.value,
                    format = decodeResult.format,
                    confidence = confidence,
                    boundingBox = decodeResult.boundingBox,
                    frameIndex = frameCounter
                )
            } else {
                null
            }
        }
    }

    private fun validateDecodeResult(result: DecodeResult): com.tala.engine.validate.ValidationResult {
        for (validator in validators) {
            val vResult = validator.validate(result)
            if (vResult.isValid) return vResult
        }
        return com.tala.engine.validate.ValidationResult(false, 0.3f, "No validator matched")
    }

    fun getConfirmedResults(): List<BarcodeResult> = resultAccumulator.getAllConfirmed()
    fun getPerformanceStats() = performanceMonitor.getStats()

    fun reset() {
        autoSnapping.reset()
        debouncer.reset()
        barcodeFilter.reset()
        oneDTracker.reset()
        verifier.reset()
        resultAccumulator.reset()
        frameBuffer?.clear()
        frameCounter = 0L
        performanceMonitor.start()
    }

    fun shutdown() {
        pipelineScope.cancel()
        detector?.release()
        classicDetector?.release()
        frameBuffer?.clear()
        Log.d(TAG, "Scan pipeline shut down")
    }

    companion object {
        private const val TAG = "TalaPipeline"
    }
}
