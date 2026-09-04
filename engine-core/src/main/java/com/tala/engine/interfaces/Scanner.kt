package com.tala.engine.interfaces

import com.tala.engine.model.BarcodeResult
import com.tala.engine.model.ScanConfiguration

interface Scanner {
    suspend fun start(configuration: ScanConfiguration)
    suspend fun stop()
    fun pause()
    fun resume()
    fun getResults(): List<BarcodeResult>
    fun reset()
    fun isRunning(): Boolean
}
