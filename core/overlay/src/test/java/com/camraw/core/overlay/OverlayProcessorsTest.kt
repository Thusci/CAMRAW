package com.camraw.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayProcessorsTest {
    @Test
    fun histogramReportsHighlightClipping() {
        val frame = LumaFrame(
            width = 4,
            height = 1,
            pixels = byteArrayOf(0, 10, 127, 255.toByte()),
        )

        val histogram = HistogramProcessor(binCount = 4).process(frame)

        assertEquals(4, histogram.bins.sum())
        assertTrue(histogram.clippedHighlights > 0f)
        assertTrue(histogram.clippedShadows > 0f)
    }
}
