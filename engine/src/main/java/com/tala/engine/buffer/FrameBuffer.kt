package com.tala.engine.buffer

import android.graphics.Bitmap

class FrameBuffer(private val capacity: Int = 5) {

    data class BufferedFrame(
        val bitmap: Bitmap,
        val timestamp: Long = System.currentTimeMillis(),
        val frameIndex: Long
    )

    private val buffer = ArrayDeque<BufferedFrame>(capacity)
    private var frameCounter = 0L

    @Synchronized
    fun add(bitmap: Bitmap) {
        if (buffer.size >= capacity) {
            val oldest = buffer.removeFirst()
            if (oldest.bitmap != bitmap) {
                oldest.bitmap.recycle()
            }
        }
        // Store a reference; caller should NOT recycle
        buffer.addLast(BufferedFrame(bitmap, System.currentTimeMillis(), frameCounter++))
    }

    @Synchronized
    fun getRecent(count: Int = capacity): List<BufferedFrame> {
        return buffer.toList().takeLast(count.coerceAtMost(buffer.size))
    }

    @Synchronized
    fun getFrameAt(index: Long): BufferedFrame? {
        return buffer.find { it.frameIndex == index }
    }

    @Synchronized
    fun getLatest(): BufferedFrame? = buffer.lastOrNull()

    @Synchronized
    fun size(): Int = buffer.size

    @Synchronized
    fun clear() {
        buffer.clear()
        frameCounter = 0L
    }

    fun getFrameCounter(): Long = frameCounter
}
