package com.tala.engine.accumulate

import com.tala.engine.model.BoundingBox

class SpatialTracker(
    private val positionTolerancePx: Float = 50f
) {
    data class TrackedPosition(
        val centerX: Float,
        val centerY: Float,
        val timestamp: Long
    )

    private val positions = mutableMapOf<String, MutableList<TrackedPosition>>()

    fun update(fingerprint: String, bbox: BoundingBox) {
        val list = positions.getOrPut(fingerprint) { mutableListOf() }
        list.add(TrackedPosition(bbox.centerX, bbox.centerY, System.currentTimeMillis()))

        // Keep last 30 entries
        if (list.size > 30) {
            list.removeFirst()
        }
    }

    fun predictPosition(fingerprint: String): Pair<Float, Float>? {
        val list = positions[fingerprint] ?: return null
        if (list.size < 2) {
            val last = list.lastOrNull() ?: return null
            return last.centerX to last.centerY
        }

        // Simple linear extrapolation
        val last = list.last()
        val prev = list[list.size - 2]
        val dt = (last.timestamp - prev.timestamp).coerceAtLeast(1)

        val vx = (last.centerX - prev.centerX) / dt
        val vy = (last.centerY - prev.centerY) / dt

        // Predict next position (assuming ~33ms frame interval)
        val predictedX = last.centerX + vx * 33
        val predictedY = last.centerY + vy * 33

        return predictedX to predictedY
    }

    fun findNearestFingerprint(
        targetX: Float,
        targetY: Float,
        excludeFingerprints: Set<String> = emptySet()
    ): String? {
        var bestFingerprint: String? = null
        var bestDistance = Float.MAX_VALUE

        for ((fp, list) in positions) {
            if (fp in excludeFingerprints) continue
            val last = list.lastOrNull() ?: continue

            val distance = kotlin.math.sqrt(
                (last.centerX - targetX).toDouble().pow(2.0) +
                (last.centerY - targetY).toDouble().pow(2.0)
            ).toFloat()

            if (distance < positionTolerancePx && distance < bestDistance) {
                bestDistance = distance
                bestFingerprint = fp
            }
        }

        return bestFingerprint
    }

    fun cleanup(maxAgeMs: Long = 5000L) {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<String>()

        for ((fp, list) in positions) {
            list.removeAll { now - it.timestamp > maxAgeMs }
            if (list.isEmpty()) {
                keysToRemove.add(fp)
            }
        }

        keysToRemove.forEach { positions.remove(it) }
    }
}
