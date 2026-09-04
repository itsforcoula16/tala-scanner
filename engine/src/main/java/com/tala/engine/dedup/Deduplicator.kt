package com.tala.engine.dedup

import com.tala.engine.model.BarcodeResult

class Deduplicator {
    private val sessionResults = mutableSetOf<String>() // fingerprints
    private val recentEmissions = mutableMapOf<String, Long>() // fingerprint -> timestamp
    private val antiBounceWindowMs = 3000L

    fun processConfirmed(result: BarcodeResult): BarcodeResult? {
        val fp = result.fingerprint

        // Already emitted in this session
        if (fp in sessionResults) {
            return null
        }

        // Anti-bounce: was recently emitted then lost
        val lastEmission = recentEmissions[fp]
        if (lastEmission != null) {
            val timeSinceEmission = System.currentTimeMillis() - lastEmission
            if (timeSinceEmission < antiBounceWindowMs) {
                return null
            }
        }

        // New barcode - emit it
        sessionResults.add(fp)
        recentEmissions[fp] = System.currentTimeMillis()

        return result
    }

    fun isDuplicate(result: BarcodeResult): Boolean {
        return result.fingerprint in sessionResults
    }

    fun getSessionResults(): List<String> = sessionResults.toList()

    fun getSessionCount(): Int = sessionResults.size

    fun reset() {
        sessionResults.clear()
        recentEmissions.clear()
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        recentEmissions.entries.removeAll { now - it.value > antiBounceWindowMs * 2 }
    }
}
