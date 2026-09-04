package com.tala.engine.pipeline

import android.util.Log
import com.tala.engine.model.BarcodeResult
import com.tala.engine.model.BoundingBox
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * تجمع‌کننده نتایج نهایی
 * مشابه ResultAccumulationConfiguration در Scanbot
 *
 * هر بارکد رو در طول زمان ردیابی می‌کنه
 * و فقط وقتی به اندازه کافی تأیید شد، خروجی می‌ده.
 */
class ResultAccumulator(
    private val config: ResultAccumulationConfiguration = ResultAccumulationConfiguration()
) {
    private val trackedBarcodes = mutableMapOf<String, TrackedBarcode>()

    data class TrackedBarcode(
        val fingerprint: String,
        val currentValue: String,
        val format: com.tala.engine.model.BarcodeFormat,
        val firstSeenMs: Long,
        var lastSeenMs: Long,
        var confirmationCount: Int = 1,
        var lastConfidence: Float = 0f,
        var positions: MutableList<BoundingBox> = mutableListOf(),
        var isConfirmed: Boolean = false
    )

    /**
     * پردازش فریم جدید
     * @param barcodes بارکدهای تأیید شده این فریم
     * @param currentTimeMs زمان فعلی
     * @return بارکدهای تأیید شده نهایی (首次 emit)
     */
    fun processFrame(barcodes: List<BarcodeResult>, currentTimeMs: Long): List<BarcodeResult> {
        val newlyConfirmed = mutableListOf<BarcodeResult>()

        // ثبت بارکدهای جدید
        for (barcode in barcodes) {
            val key = barcode.fingerprint
            val tracked = trackedBarcodes[key]

            if (tracked == null) {
                // بارکد جدید
                trackedBarcodes[key] = TrackedBarcode(
                    fingerprint = key,
                    currentValue = barcode.value,
                    format = barcode.format,
                    firstSeenMs = currentTimeMs,
                    lastSeenMs = currentTimeMs,
                    confirmationCount = 1,
                    lastConfidence = barcode.confidence,
                    positions = mutableListOf(barcode.boundingBox)
                )
            } else {
                // بارکد تکراری - بروزرسانی
                tracked.lastSeenMs = currentTimeMs
                tracked.confirmationCount++
                tracked.lastConfidence = maxOf(tracked.lastConfidence, barcode.confidence)
                tracked.positions.add(barcode.boundingBox)

                // بررسی تأیید
                if (!tracked.isConfirmed && shouldConfirm(tracked)) {
                    tracked.isConfirmed = true
                    newlyConfirmed.add(toBarcodeResult(tracked))
                }
            }
        }

        // بررسی انقضا
        val expiredKeys = mutableListOf<String>()
        for ((key, tracked) in trackedBarcodes) {
            val age = currentTimeMs - tracked.lastSeenMs
            if (age > config.autoClearThreshold * 200L) { // تقریباً هر فریم ~100ms
                expiredKeys.add(key)
            }
        }
        for (key in expiredKeys) {
            trackedBarcodes.remove(key)
        }

        return newlyConfirmed
    }

    private fun shouldConfirm(tracked: TrackedBarcode): Boolean {
        // تعداد تأیید کافیه؟
        if (tracked.confirmationCount < config.minConfirmations) {
            return false
        }

        // اطمینان کافیه؟
        if (tracked.lastConfidence < config.minConfidenceForStableField) {
            return false
        }

        // بررسی بر اساس روش تأیید
        return when (config.confirmationMethod) {
            ConfirmationMethod.EXACT -> tracked.confirmationCount >= config.minConfirmations
            ConfirmationMethod.INTERPOLATE -> {
                // بررسی ثبات موقعیت
                if (tracked.positions.size < 2) return true
                val avgPosition = calculateAveragePosition(tracked.positions)
                val maxDeviation = tracked.positions.maxOfOrNull { pos ->
                    val dx = pos.centerX - avgPosition.first
                    val dy = pos.centerY - avgPosition.second
                    sqrt(dx * dx + dy * dy)
                } ?: 0f
                // اگه انحراف کمتر از آستانه باشه
                maxDeviation < 100f
            }
        }
    }

    private fun calculateAveragePosition(positions: List<BoundingBox>): Pair<Float, Float> {
        val avgX = positions.map { it.centerX }.average().toFloat()
        val avgY = positions.map { it.centerY }.average().toFloat()
        return Pair(avgX, avgY)
    }

    private fun toBarcodeResult(tracked: TrackedBarcode): BarcodeResult {
        val lastPosition = tracked.positions.lastOrNull()
            ?: BoundingBox(0f, 0f, 0f, 0f)

        return BarcodeResult(
            value = tracked.currentValue,
            format = tracked.format,
            confidence = tracked.lastConfidence,
            boundingBox = lastPosition,
            confirmationCount = tracked.confirmationCount,
            isConfirmed = true
        )
    }

    /**
     * آیا بارکد قبلاً تأیید شده؟
     */
    fun isAlreadyConfirmed(fingerprint: String): Boolean {
        return trackedBarcodes[fingerprint]?.isConfirmed == true
    }

    /**
     * تمام بارکدهای تأیید شده
     */
    fun getAllConfirmed(): List<BarcodeResult> {
        return trackedBarcodes.values
            .filter { it.isConfirmed }
            .map { toBarcodeResult(it) }
    }

    fun reset() {
        trackedBarcodes.clear()
    }

    fun getTrackedCount(): Int = trackedBarcodes.size
    fun getConfirmedCount(): Int = trackedBarcodes.values.count { it.isConfirmed }

    companion object {
        private const val TAG = "ResultAccumulator"
    }
}
