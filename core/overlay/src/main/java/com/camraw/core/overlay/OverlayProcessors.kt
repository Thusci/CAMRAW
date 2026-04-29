package com.camraw.core.overlay

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LumaFrame(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
)

data class Histogram(
    val bins: IntArray,
    val clippedShadows: Float,
    val clippedHighlights: Float,
) {
    val peakBin: Int = bins.indices.maxByOrNull { bins[it] } ?: 0
}

class HistogramProcessor(
    private val binCount: Int = 64,
) {
    fun process(frame: LumaFrame): Histogram {
        val bins = IntArray(binCount)
        var shadows = 0
        var highlights = 0
        frame.pixels.forEach { byte ->
            val luma = byte.toInt() and 0xFF
            bins[min(binCount - 1, luma * binCount / 256)] += 1
            if (luma <= 4) shadows += 1
            if (luma >= 251) highlights += 1
        }
        val total = max(1, frame.pixels.size)
        return Histogram(
            bins = bins,
            clippedShadows = shadows.toFloat() / total,
            clippedHighlights = highlights.toFloat() / total,
        )
    }
}

data class ZebraMask(
    val width: Int,
    val height: Int,
    val mask: BooleanArray,
    val coverage: Float,
)

class ZebraProcessor(
    private val threshold: Int = 245,
    private val sampleStep: Int = 2,
) {
    fun process(frame: LumaFrame): ZebraMask {
        val width = max(1, frame.width / sampleStep)
        val height = max(1, frame.height / sampleStep)
        val mask = BooleanArray(width * height)
        var hot = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourceIndex = min(frame.pixels.lastIndex, (y * sampleStep * frame.width) + (x * sampleStep))
                val value = frame.pixels[sourceIndex].toInt() and 0xFF
                val active = value >= threshold
                mask[y * width + x] = active
                if (active) hot += 1
            }
        }
        return ZebraMask(width, height, mask, hot.toFloat() / max(1, mask.size))
    }
}

data class PeakingMask(
    val width: Int,
    val height: Int,
    val mask: BooleanArray,
    val edgeDensity: Float,
)

class FocusPeakingProcessor(
    private val threshold: Int = 32,
    private val sampleStep: Int = 2,
) {
    fun process(frame: LumaFrame): PeakingMask {
        val width = max(1, frame.width / sampleStep)
        val height = max(1, frame.height / sampleStep)
        val mask = BooleanArray(width * height)
        var edgeCount = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = lumaAt(frame, x, y)
                val dx = abs(center - lumaAt(frame, x + 1, y)) + abs(center - lumaAt(frame, x - 1, y))
                val dy = abs(center - lumaAt(frame, x, y + 1)) + abs(center - lumaAt(frame, x, y - 1))
                val active = dx + dy >= threshold
                mask[y * width + x] = active
                if (active) edgeCount += 1
            }
        }
        return PeakingMask(width, height, mask, edgeCount.toFloat() / max(1, mask.size))
    }

    private fun lumaAt(frame: LumaFrame, x: Int, y: Int): Int {
        val sourceX = min(frame.width - 1, x * sampleStep)
        val sourceY = min(frame.height - 1, y * sampleStep)
        return frame.pixels[sourceY * frame.width + sourceX].toInt() and 0xFF
    }
}

data class GridSpec(
    val thirds: Boolean = true,
    val nineCells: Boolean = true,
    val centerCross: Boolean = true,
    val safeFrame: Boolean = false,
)
