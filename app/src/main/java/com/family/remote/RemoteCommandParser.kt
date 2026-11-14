package com.family.remote

data class ScreenMetrics(val widthPixels: Int, val heightPixels: Int, val densityDpi: Int)

object RemoteCommandParser {
    data class ParsedCommand(
        val type: String,
        val x: Float? = null,
        val y: Float? = null,
        val startX: Float? = null,
        val startY: Float? = null,
        val endX: Float? = null,
        val endY: Float? = null,
        val duration: Long = 300L
    )

    fun calculateCaptureResolution(
        width: Int,
        height: Int,
        scale: Float = 0.5f,
        alignment: Int = 16
    ): Pair<Int, Int> {
        val targetWidth = ((width * scale).toInt() / alignment) * alignment
        val targetHeight = ((height * scale).toInt() / alignment) * alignment
        return Pair(targetWidth.coerceAtLeast(alignment), targetHeight.coerceAtLeast(alignment))
    }

    fun parseNormalizedTap(normX: Double, normY: Double, realWidth: Float, realHeight: Float): Pair<Float, Float> {
        val clampedX = normX.coerceIn(0.0, 1.0).toFloat()
        val clampedY = normY.coerceIn(0.0, 1.0).toFloat()
        return Pair(clampedX * realWidth, clampedY * realHeight)
    }

    fun parseNormalizedSwipe(
        normStartX: Double,
        normStartY: Double,
        normEndX: Double,
        normEndY: Double,
        realWidth: Float,
        realHeight: Float
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val startX = normStartX.coerceIn(0.0, 1.0).toFloat() * realWidth
        val startY = normStartY.coerceIn(0.0, 1.0).toFloat() * realHeight
        val endX = normEndX.coerceIn(0.0, 1.0).toFloat() * realWidth
        val endY = normEndY.coerceIn(0.0, 1.0).toFloat() * realHeight
        return Pair(Pair(startX, startY), Pair(endX, endY))
    }
}
