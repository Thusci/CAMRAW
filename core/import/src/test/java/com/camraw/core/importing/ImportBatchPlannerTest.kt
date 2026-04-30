package com.camraw.core.importing

import com.camraw.core.camera.api.CameraObject
import com.camraw.core.camera.api.CameraObjectKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ImportBatchPlannerTest {
    @Test
    fun filtersByRequestedFormats() {
        val plan = ImportBatchPlanner.plan(
            objects = listOf(
                objectOf("A.ARW", CameraObjectKind.Raw),
                objectOf("A.JPG", CameraObjectKind.Jpeg),
            ),
            includeRaw = false,
            includeJpeg = true,
        )

        assertEquals(1, plan.objects.size)
        assertEquals("A.JPG", plan.objects.first().fileName)
        assertEquals(0, plan.rawCount)
        assertEquals(1, plan.jpegCount)
    }

    private fun objectOf(fileName: String, kind: CameraObjectKind): CameraObject {
        return CameraObject(
            objectId = fileName,
            fileName = fileName,
            kind = kind,
            sizeBytes = 10L,
            capturedAt = Instant.parse("2026-04-30T08:00:00Z"),
        )
    }
}
