package com.tala.engine.model

data class BoundingBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
    val area: Float get() = width * height

    fun overlaps(other: BoundingBox, tolerance: Float = 0.3f): Boolean {
        val overlapLeft = maxOf(left, other.left)
        val overlapTop = maxOf(top, other.top)
        val overlapRight = minOf(right, other.right)
        val overlapBottom = minOf(bottom, other.bottom)

        if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) return false

        val overlapArea = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
        val minArea = minOf(area, other.area)
        return minArea > 0 && (overlapArea / minArea) >= (1f - tolerance)
    }

    fun expand(factor: Float): BoundingBox {
        val expandW = width * (factor - 1f) / 2f
        val expandH = height * (factor - 1f) / 2f
        return BoundingBox(
            left = left - expandW,
            top = top - expandH,
            width = width + expandW * 2,
            height = height + expandH * 2,
            rotation = rotation
        )
    }

    fun clamp(imageWidth: Int, imageHeight: Int): BoundingBox {
        return BoundingBox(
            left = left.coerceIn(0f, imageWidth.toFloat()),
            top = top.coerceIn(0f, imageHeight.toFloat()),
            width = width.coerceIn(0f, imageWidth - left),
            height = height.coerceIn(0f, imageHeight - top),
            rotation = rotation
        )
    }
}
