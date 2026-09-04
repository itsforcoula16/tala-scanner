package com.tala.engine.pipeline

/**
 * جلوگیری از تکرار اسکن بعد از اسکن موفق
 * مشابه SuccessFrameDebouncer در Scanbot
 *
 * API:
 * - setInterval(J) → فاصله بین اسکن‌ها (ms)
 * - shouldSkip() → آیا این فریم رو رد کنه؟
 * - activate() → فعال‌سازی debounce
 * - reset() → ریست
 */
class SuccessFrameDebouncer {

    private var intervalMs: Long = 1500L
    private var active = false
    private var activatedAt = 0L
    private var lastSuccessValue: String? = null

    /**
     * بررسی می‌کنه آیا این فریم باید رد بشه
     * @return true اگه باید رد بشه (تکراریه)
     */
    fun shouldSkip(barcodeValue: String?, currentTimeMs: Long): Boolean {
        if (!active) return false

        val elapsed = currentTimeMs - activatedAt

        if (elapsed > intervalMs) {
            active = false
            return false
        }

        // اگه همون بارکد موفق قبلی باشه → رد کن
        if (barcodeValue != null && barcodeValue == lastSuccessValue) {
            return true
        }

        return false
    }

    /**
     * فعال‌سازی debounce بعد از اسکن موفق
     */
    fun activate(successValue: String, currentTimeMs: Long) {
        active = true
        activatedAt = currentTimeMs
        lastSuccessValue = successValue
    }

    fun setInterval(intervalMs: Long) {
        this.intervalMs = intervalMs
    }

    fun reset() {
        active = false
        activatedAt = 0L
        lastSuccessValue = null
    }
}
