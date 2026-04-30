package com.camraw.core.tether

import com.camraw.core.camera.api.CameraObject
import com.camraw.core.camera.api.CameraObjectKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TetherPairingStrategyTest {
    @Test
    fun pairsRawAndJpegByBaseName() {
        val capturedAt = Instant.parse("2026-04-30T08:00:00Z")
        val pairs = TetherPairingStrategy.pair(
            listOf(
                cameraObject("raw-1", "DSC0001.ARW", CameraObjectKind.Raw, capturedAt),
                cameraObject("jpg-1", "DSC0001.JPG", CameraObjectKind.Jpeg, capturedAt),
            ),
        )

        assertEquals(1, pairs.size)
        assertEquals(PairStatus.RawAndJpeg, pairs.first().status)
    }

    @Test
    fun keepsSingleFilesWhenPairIsMissing() {
        val pairs = TetherPairingStrategy.pair(
            listOf(
                cameraObject("raw-1", "A.CR3", CameraObjectKind.Raw, Instant.parse("2026-04-30T08:00:00Z")),
                cameraObject("jpg-1", "B.JPG", CameraObjectKind.Jpeg, Instant.parse("2026-04-30T08:00:10Z")),
            ),
        )

        assertEquals(listOf(PairStatus.RawOnly, PairStatus.JpegOnly), pairs.map { it.status })
    }

    private fun cameraObject(
        id: String,
        fileName: String,
        kind: CameraObjectKind,
        capturedAt: Instant = Instant.parse("2026-04-30T08:00:00Z"),
    ): CameraObject {
        return CameraObject(
            objectId = id,
            fileName = fileName,
            kind = kind,
            sizeBytes = 1024L,
            capturedAt = capturedAt,
        )
    }
}
