package com.tala.engine.detect

import android.graphics.Bitmap

interface Detector {
    suspend fun detect(bitmap: Bitmap): List<DetectionResult>
    fun release()
}
