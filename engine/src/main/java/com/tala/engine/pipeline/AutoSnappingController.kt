package com.tala.engine.pipeline

import com.tala.engine.model.BarcodeResult

/**
 * کنترل‌کننده اسکن خودکار سریع
 * مشابه BarcodeAutoSnappingController در Scanbot
 *
 * وقتی بارکد در فریم دوربین ثابت باشه، خودکار اسکن می‌کنه.
 * با حساسیت قابل تنظیم کار می‌کنه.
 *
 * API:
 * - setSensitivity(F) → حساسیت اسکن (0.0 تا 1.0)
 * - setEnabled(Z) → فعال/غیرفعال
 * - setAutoSnappingCallback → callback بعد از اسکن
 */
class AutoSnappingController {

    companion object {
        const val DEFAULT_CAPTURE_INTERVAL_MS = 1000L   // ۱ ثانیه
        const val MAXIMUM_CAPTURE_INTERVAL_MS = 3000L   // ۳ ثانیه
        private const val TAG = "AutoSnapController"
    }

    private var enabled = true
    private var sensitivity = 0.7f
    private var captureIntervalMs = DEFAULT_CAPTURE_INTERVAL_MS
    private var lastSnapTime = 0L
    private var lastDetectedBarcodes: List<BarcodeResult> = emptyList()
    private var stabilityCounter = 0
    private var callback: AutoSnappingCallback? = null

    /**
     * بررسی می‌کنه آیا باید اسکن خودکار انجام بشه
     */
    fun shouldSnap(currentBarcodes: List<BarcodeResult>, currentTimeMs: Long): Boolean {
        if (!enabled) return false
        if (currentBarcodes.isEmpty()) {
            stabilityCounter = 0
            return false
        }

        val timeSinceLastSnap = currentTimeMs - lastSnapTime
        if (timeSinceLastSnap < captureIntervalMs) return false

        val isStable = isBarcodeStable(currentBarcodes, lastDetectedBarcodes)

        if (isStable) {
            stabilityCounter++
        } else {
            stabilityCounter = 0
            lastDetectedBarcodes = currentBarcodes
            return false
        }

        // حساسیت → تعداد فریم مورد نیاز
        // sensitivity 1.0 → 1 فریم (سریع‌ترین)
        // sensitivity 0.1 → 10 فریم (آهسته‌ترین)
        val requiredStability = ((1.0f - sensitivity) * 10f).toInt().coerceIn(1, 10)

        if (stabilityCounter >= requiredStability) {
            lastSnapTime = currentTimeMs
            stabilityCounter = 0
            callback?.onAutoSnap(currentBarcodes)
            return true
        }

        return false
    }

    private fun isBarcodeStable(
        current: List<BarcodeResult>,
        previous: List<BarcodeResult>
    ): Boolean {
        if (previous.isEmpty()) return false

        val currentValues = current.map { it.value }.toSet()
        val previousValues = previous.map { it.value }.toSet()

        val common = currentValues.intersect(previousValues)
        val totalUnique = currentValues.union(previousValues).size

        if (totalUnique == 0) return false

        val stabilityRatio = common.size.toFloat() / totalUnique
        return stabilityRatio >= 0.8f
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0.0f, 1.0f)
    }

    fun setAutoSnappingCallback(callback: AutoSnappingCallback) {
        this.callback = callback
    }

    fun reset() {
        lastSnapTime = 0L
        stabilityCounter = 0
        lastDetectedBarcodes = emptyList()
    }
}

interface AutoSnappingCallback {
    fun onAutoSnap(barcodeResults: List<BarcodeResult>)
}
