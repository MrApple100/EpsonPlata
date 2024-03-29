package ru.`object`.epsoncamera.detection

import android.graphics.RectF

data class DetectionResult(
        val id: Int,
        val title: String,
        val confidence: Float,
        val location: RectF
) {
    val text: String by lazy {
        "$title"
    }
}