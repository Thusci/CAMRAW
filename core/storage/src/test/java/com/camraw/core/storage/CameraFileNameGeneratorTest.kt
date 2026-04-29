package com.camraw.core.storage

import com.camraw.core.camera.api.CameraObjectKind
import com.camraw.core.camera.api.StorageWriteRequest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CameraFileNameGeneratorTest {
    @Test
    fun generatedNameContainsProviderDeviceTimestampAndSequence() {
        val request = StorageWriteRequest(
            providerId = "Internal",
            providerName = "Internal Camera",
            deviceId = "0",
            deviceName = "GT7 Pro",
            kind = CameraObjectKind.Jpeg,
            extension = "jpg",
            mimeType = "image/jpeg",
            capturedAt = Instant.parse("2026-04-29T12:30:10.123Z"),
        )

        val name = CameraFileNameGenerator().generate(request)

        assertTrue(name.startsWith("Internal_GT7_Pro_"))
        assertTrue(name.endsWith("_0001.jpg"))
    }
}
