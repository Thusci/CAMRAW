package com.camraw.providers.libgphoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibGPhotoUsbDeviceMatcherTest {
    @Test
    fun acceptsStillImageClass() {
        assertTrue(
            LibGPhotoUsbDeviceMatcher.isLikelyStillCamera(
                UsbDescriptor(
                    vendorId = 0x054c,
                    productId = 0x0abc,
                    deviceClass = 6,
                    deviceSubclass = 1,
                    deviceProtocol = 1,
                    interfaceClasses = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun rejectsHubClassWithoutCameraInterface() {
        assertFalse(
            LibGPhotoUsbDeviceMatcher.isLikelyStillCamera(
                UsbDescriptor(
                    vendorId = 0x1d6b,
                    productId = 0x0002,
                    deviceClass = 9,
                    deviceSubclass = 0,
                    deviceProtocol = 0,
                    interfaceClasses = listOf(9),
                ),
            ),
        )
    }
}
