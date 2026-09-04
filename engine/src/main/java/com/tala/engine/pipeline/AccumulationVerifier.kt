package com.tala.engine.pipeline

import com.tala.engine.model.BarcodeResult

// ════════════════════════════════════════════════════════════════
// BarcodeAccumulationConfiguration
// مشابه Scanbot: انباشت فریم‌ها
// ════════════════════════════════════════════════════════════════

data class BarcodeAccumulationConfiguration(
    val accumulationTime: Long = 2000L,             // زمان انباشت (ms)
    val method: AccumulationMethod = AccumulationMethod.LAST_VISIBLE,
    val removeUnconnectedResults: Boolean = false
)

enum class AccumulationMethod {
    LAST_VISIBLE,              // فقط آخرین فریم قابل مشاهده
    INTERPOLATE_BY_CAMERA      // بر اساس حرکت دوربین
}

// ════════════════════════════════════════════════════════════════
// AccumulatedResultsVerifierConfiguration
// مشابه Scanbot: تأیید نتایج انباشت شده
// ════════════════════════════════════════════════════════════════

data class AccumulatedResultsVerifierConfiguration(
    val maxAccumulatedFrames: Int = 10,
    val minRequiredFramesWithEqualResult: Int = 3
)

// ════════════════════════════════════════════════════════════════
// ResultAccumulationConfiguration
// مشابه Scanbot: تأیید نهایی
// ════════════════════════════════════════════════════════════════

data class ResultAccumulationConfiguration(
    val confirmationMethod: ConfirmationMethod = ConfirmationMethod.INTERPOLATE,
    val minConfirmations: Int = 3,
    val minConfidenceForStableField: Float = 0.8f,
    val autoClearThreshold: Int = 4,
    val accumulationWindowMs: Long = 2000L
)

enum class ConfirmationMethod {
    EXACT,          // نتیجه دقیقاً یکسان
    INTERPOLATE     // نتیجه تقریباً یکسان
}

// ════════════════════════════════════════════════════════════════
// AccumulatedResultsVerifier
// ════════════════════════════════════════════════════════════════

class AccumulatedResultsVerifier(
    private val config: AccumulatedResultsVerifierConfiguration = AccumulatedResultsVerifierConfiguration()
) {
    private val accumulatedFrames = mutableListOf<List<BarcodeResult>>()

    fun addFrame(results: List<BarcodeResult>) {
        accumulatedFrames.add(results)
        while (accumulatedFrames.size > config.maxAccumulatedFrames) {
            accumulatedFrames.removeAt(0)
        }
    }

    fun isVerified(value: String): Boolean {
        var matchCount = 0
        for (frame in accumulatedFrames) {
            if (frame.any { it.value == value }) {
                matchCount++
            }
        }
        return matchCount >= config.minRequiredFramesWithEqualResult
    }

    fun getMatchCount(value: String): Int {
        return accumulatedFrames.count { frame ->
            frame.any { it.value == value }
        }
    }

    fun reset() {
        accumulatedFrames.clear()
    }

    fun getFrameCount(): Int = accumulatedFrames.size
}
