package com.camraw.providers.libgphoto

import com.camraw.core.camera.api.CameraCapability
import com.camraw.core.camera.api.CameraConnectionType
import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CapabilityState
import org.junit.Assert.assertEquals
import org.junit.Test

class LibGPhotoCapabilityMapperTest {
    @Test
    fun mapsTetherAndImportCapabilities() {
        val capabilities = LibGPhotoCapabilityMapper.fromNativeJson(
            deviceInfo = CameraDeviceInfo(
                id = "usb",
                displayName = "USB Camera",
                providerId = "libgphoto",
                providerName = "libgphoto",
                connectionType = CameraConnectionType.UsbGPhoto,
            ),
            nativeJson = """
                {"backend":"libgphoto2","can_capture":true,"can_tether":true,"can_list_files":true,"can_download_files":true,"can_read_config":true,"can_write_config":false}
            """.trimIndent(),
        )

        assertEquals(CapabilityState.Available, capabilities.capabilities[CameraCapability.TetherCapture]?.state)
        assertEquals(CapabilityState.Available, capabilities.capabilities[CameraCapability.FileImport]?.state)
        assertEquals(CapabilityState.Available, capabilities.capabilities[CameraCapability.RawDownload]?.state)
    }
}
