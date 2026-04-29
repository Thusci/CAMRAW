package com.camraw.core.camera.api

import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapabilitiesTest {
    @Test
    fun debugJsonIncludesCapabilitiesAndSettings() {
        val capabilities = CameraCapabilities(
            providerId = "fake-camera",
            deviceId = "fake-1",
            capabilities = mapOf(
                CameraCapability.Preview to CapabilityInfo(
                    state = CapabilityState.Available,
                    displayName = "Preview",
                ),
            ),
            settings = listOf(
                CameraSettingDescriptor(
                    id = "iso",
                    displayName = "ISO",
                    category = SettingCategory.Exposure,
                    valueType = SettingValueType.Choice,
                    currentValue = SettingValue.Choice("100", "100"),
                    availableValues = listOf(SettingValue.Choice("100", "100")),
                    writable = true,
                    state = CapabilityState.Available,
                ),
            ),
        )

        val json = capabilities.toDebugJson()

        assertTrue(json.contains("\"providerId\": \"fake-camera\""))
        assertTrue(json.contains("\"Preview\""))
        assertTrue(json.contains("\"iso\""))
    }
}
