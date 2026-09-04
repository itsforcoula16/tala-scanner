package com.tala.engine.pipeline

/**
 * حالت تأیید بارکد یک‌بعدی (Code128, Code39, etc.)
 * مشابه OneDConfirmationMode در Scanbot
 *
 * هرچی سطح بالاتر، چند فریم متوالی بررسی می‌شه.
 */
enum class OneDConfirmationMode(
    val minFrames: Int,
    val description: String
) {
    /**
     * بدون تأیید - سریع‌ترین
     * فقط یک فریم کافیه
     */
    NONE(1, "بدون تأیید - سریع‌ترین"),

    /**
     * حداقل تأیید
     * ۲ فریم باید نتیجه یکسان بده
     */
    MINIMAL(2, "حداقل تأیید - ۲ فریم"),

    /**
     * تأیید متوسط
     * ۳ فریم باید نتیجه یکسان بده
     */
    MODERATE(3, "تأیید متوسط - ۳ فریم"),

    /**
     * تأیید کامل - دقیق‌ترین
     * ۴-۵ فریم باید نتیجه یکسان بده
     */
    THOROUGH(5, "تأیید کامل - ۵ فریم");

    /**
     * بررسی می‌کنه آیا تأیید کافیه
     * @param consecutiveMatches تعداد فریم‌های متوالی با نتیجه یکسان
     * @return true اگه تأیید کافیه
     */
    fun isConfirmed(consecutiveMatches: Int): Boolean {
        return consecutiveMatches >= minFrames
    }
}

/**
 * کنترل‌کننده تأیید بارکد یک‌بعدی
 * ردیابی تعداد فریم‌های متوالی با نتیجه یکسان
 */
class OneDConfirmationTracker {
    private var currentMode = OneDConfirmationMode.MODERATE
    private var lastValue: String? = null
    private var consecutiveCount = 0

    fun setMode(mode: OneDConfirmationMode) {
        currentMode = mode
        reset()
    }

    /**
     * ثبت فریم جدید
     * @param value مقدار بارکد تشخیص داده شده
     * @return true اگه تأیید کافیه
     */
    fun trackFrame(value: String?): Boolean {
        if (value == null || value.isEmpty()) {
            consecutiveCount = 0
            lastValue = null
            return false
        }

        if (value == lastValue) {
            consecutiveCount++
        } else {
            lastValue = value
            consecutiveCount = 1
        }

        return currentMode.isConfirmed(consecutiveCount)
    }

    fun reset() {
        lastValue = null
        consecutiveCount = 0
    }

    fun getConsecutiveCount(): Int = consecutiveCount
    fun getCurrentMode(): OneDConfirmationMode = currentMode
}
