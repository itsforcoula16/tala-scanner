package com.tala.engine.accumulate

import com.tala.engine.model.AccumulationConfig
import com.tala.engine.model.BarcodeResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FrameAccumulator(
    private val config: AccumulationConfig
) {
    private val trackers = mutableMapOf<String, ResultTracker>()
    private val spatialTracker = SpatialTracker(config.positionTolerancePx)
    private var frameIndex = 0L

    private val _confirmedFlow = MutableSharedFlow<BarcodeResult>(extraBufferCapacity = 64)
    val confirmedFlow: SharedFlow<BarcodeResult> = _confirmedFlow.asSharedFlow()

    private val _updatedFlow = MutableSharedFlow<BarcodeResult>(extraBufferCapacity = 64)
    val updatedFlow: SharedFlow<BarcodeResult> = _updatedFlow.asSharedFlow()

    private val _lostFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val lostFlow: SharedFlow<String> = _lostFlow.asSharedFlow()

    fun processFrame(results: List<BarcodeResult>) {
        frameIndex++
        val now = System.currentTimeMillis()

        // Update trackers with new results
        val seenFingerprints = mutableSetOf<String>()

        for (result in results) {
            val fp = result.fingerprint
            seenFingerprints.add(fp)

            val tracker = trackers.getOrPut(fp) {
                ResultTracker(fp, result.value, result.format)
            }

            tracker.update(result.copy(frameIndex = frameIndex))
            spatialTracker.update(fp, result.boundingBox)

            // Check if newly confirmed
            if (!tracker.isConfirmed && tracker.confirmationCount >= config.minConfirmations) {
                tracker.confirm()
                _confirmedFlow.tryEmit(tracker.toResult())
            } else if (tracker.isConfirmed) {
                // Update already-confirmed results
                _updatedFlow.tryEmit(tracker.toResult())
            }
        }

        // Check for lost barcodes
        val lostFingerprints = mutableListOf<String>()

        for ((fp, tracker) in trackers) {
            if (fp !in seenFingerprints) {
                val framesSinceLastSeen = (now - tracker.lastSeenTimestamp) / 33 // ~30fps

                if (framesSinceLastSeen >= config.autoClearThreshold) {
                    lostFingerprints.add(fp)
                }
            }
        }

        // Remove lost barcodes
        for (fp in lostFingerprints) {
            val tracker = trackers.remove(fp)
            if (tracker != null) {
                _lostFlow.tryEmit(fp)
            }
        }

        // Force-confirm barcodes that have been in the accumulation window too long
        if (config.forceConfirmAfterWindow) {
            for ((fp, tracker) in trackers) {
                if (!tracker.isConfirmed &&
                    now - tracker.lastSeenTimestamp <= config.accumulationWindowMs &&
                    tracker.confirmationCount > 0 &&
                    tracker.timeSinceLastSeen() > config.accumulationWindowMs / 2
                ) {
                    tracker.confirm()
                    _confirmedFlow.tryEmit(tracker.toResult())
                }
            }
        }

        spatialTracker.cleanup()
    }

    fun getAllConfirmed(): List<BarcodeResult> {
        return trackers.values
            .filter { it.isConfirmed }
            .map { it.toResult() }
    }

    fun getTrackerCount(): Int = trackers.size

    fun reset() {
        trackers.clear()
        frameIndex = 0L
    }
}
