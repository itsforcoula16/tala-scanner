package com.tala.engine.interfaces

import com.tala.engine.model.BarcodeResult

interface ResultListener {
    fun onBarcodeConfirmed(result: BarcodeResult)
    fun onBarcodeUpdated(result: BarcodeResult)
    fun onBarcodeLost(fingerprint: String)
    fun onScanSessionComplete(results: List<BarcodeResult>)
}
