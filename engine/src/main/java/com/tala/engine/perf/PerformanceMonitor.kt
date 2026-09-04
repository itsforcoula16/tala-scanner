package com.tala.engine.perf

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PerformanceMonitor {
    private val frameCount = AtomicInteger(0)
    private val totalDecodeTimeMs = AtomicLong(0)
    private val totalDetectionTimeMs = AtomicLong(0)
    private val totalValidationTimeMs = AtomicLong(0)
    private var startTimeMs = 0L
    private var lastFpsCalcTime = 0L
    private var lastFrameCount = 0
    var currentFps: Float = 0f
        private set

    var lastFrameLatencyMs: Long = 0
        private set

    fun start() {
        startTimeMs = System.currentTimeMillis()
        lastFpsCalcTime = startTimeMs
        frameCount.set(0)
        totalDecodeTimeMs.set(0)
        totalDetectionTimeMs.set(0)
        totalValidationTimeMs.set(0)
    }

    fun onFrameComplete(detectionMs: Long, decodeMs: Long, validationMs: Long) {
        frameCount.incrementAndGet()
        totalDetectionTimeMs.addAndGet(detectionMs)
        totalDecodeTimeMs.addAndGet(decodeMs)
        totalValidationTimeMs.addAndGet(validationMs)
        lastFrameLatencyMs = detectionMs + decodeMs + validationMs

        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsCalcTime
        if (elapsed >= 1000) {
            val framesSince = frameCount.get() - lastFrameCount
            currentFps = framesSince * 1000f / elapsed
            lastFpsCalcTime = now
            lastFrameCount = frameCount.get()
        }
    }

    fun getStats(): Stats {
        val totalFrames = frameCount.get()
        val elapsed = System.currentTimeMillis() - startTimeMs
        return Stats(
            totalFrames = totalFrames,
            averageFps = if (elapsed > 0) totalFrames * 1000f / elapsed else 0f,
            currentFps = currentFps,
            averageDetectionMs = if (totalFrames > 0) totalDetectionTimeMs.get() / totalFrames else 0,
            averageDecodeMs = if (totalFrames > 0) totalDecodeTimeMs.get() / totalFrames else 0,
            averageValidationMs = if (totalFrames > 0) totalValidationTimeMs.get() / totalFrames else 0,
            totalElapsedMs = elapsed
        )
    }

    fun logStats() {
        val stats = getStats()
        Log.d(TAG, "=== Performance Stats ===")
        Log.d(TAG, "Frames: ${stats.totalFrames}, FPS: ${"%.1f".format(stats.currentFps)}")
        Log.d(TAG, "Avg Detection: ${stats.averageDetectionMs}ms, Decode: ${stats.averageDecodeMs}ms")
        Log.d(TAG, "Total Elapsed: ${stats.totalElapsedMs}ms")
    }

    companion object {
        private const val TAG = "TalaPerf"

        data class Stats(
            val totalFrames: Int,
            val averageFps: Float,
            val currentFps: Float,
            val averageDetectionMs: Long,
            val averageDecodeMs: Long,
            val averageValidationMs: Long,
            val totalElapsedMs: Long
        )
    }
}
