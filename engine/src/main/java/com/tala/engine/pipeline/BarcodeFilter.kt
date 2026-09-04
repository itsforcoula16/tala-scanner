package com.tala.engine.pipeline

import com.tala.engine.model.BarcodeResult

/**
 * فیلتر بارکدهای تکراری
 * مشابه BarcodeFilter در Scanbot
 *
 * API:
 * - acceptsBarcode(BarcodeItem) → آیا این بارکد قبول بشه؟
 * - shouldAdd(BarcodeItem, List<BarcodeItem>) → آیا به لیست اضافه بشه؟
 */
class BarcodeFilter {

    private val acceptedFingerprints = mutableSetOf<String>()
    private val acceptedBarcodes = mutableMapOf<String, BarcodeResult>()

    /**
     * آیا این بارکد قبول بشه؟
     */
    fun acceptsBarcode(barcode: BarcodeResult): Boolean {
        val fingerprint = barcode.fingerprint
        return !acceptedFingerprints.contains(fingerprint)
    }

    /**
     * آیا به لیست اضافه بشه؟
     */
    fun shouldAdd(barcode: BarcodeResult, existingList: List<BarcodeResult>): Boolean {
        val duplicate = existingList.find {
            it.value == barcode.value && it.format == barcode.format
        }

        if (duplicate != null) {
            return barcode.confidence > duplicate.confidence
        }

        return true
    }

    fun markAccepted(barcode: BarcodeResult) {
        acceptedFingerprints.add(barcode.fingerprint)
        acceptedBarcodes[barcode.fingerprint] = barcode
    }

    fun reset() {
        acceptedFingerprints.clear()
        acceptedBarcodes.clear()
    }

    fun getAcceptedCount(): Int = acceptedFingerprints.size
}
