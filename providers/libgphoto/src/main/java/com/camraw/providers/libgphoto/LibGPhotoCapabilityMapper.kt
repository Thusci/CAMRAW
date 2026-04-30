package com.camraw.providers.libgphoto

import com.camraw.core.camera.api.CameraCapabilities
import com.camraw.core.camera.api.CameraCapability
import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CapabilityInfo
import com.camraw.core.camera.api.CapabilityState

object LibGPhotoCapabilityMapper {
    fun fromNativeJson(deviceInfo: CameraDeviceInfo, nativeJson: String): CameraCapabilities {
        val canCapture = nativeJson.booleanValue("can_capture")
        val canTether = nativeJson.booleanValue("can_tether")
        val canListFiles = nativeJson.booleanValue("can_list_files")
        val canDownloadFiles = nativeJson.booleanValue("can_download_files")
        val canReadConfig = nativeJson.booleanValue("can_read_config")
        val canWriteConfig = nativeJson.booleanValue("can_write_config")
        val backend = nativeJson.stringValue("backend") ?: "unknown"

        val available = buildSet {
            add(CameraCapability.DebugDump)
            add(CameraCapability.SidecarMetadata)
            if (canCapture) add(CameraCapability.CaptureJpeg)
            if (canCapture) add(CameraCapability.TetherCapture)
            if (canTether) add(CameraCapability.BodyShutterDetection)
            if (canListFiles) add(CameraCapability.CameraFileBrowser)
            if (canListFiles) add(CameraCapability.FileImport)
            if (canDownloadFiles) add(CameraCapability.RawDownload)
            if (canDownloadFiles) add(CameraCapability.JpegDownload)
            if (canReadConfig || canWriteConfig) add(CameraCapability.BasicExternalSettingsControl)
        }

        return CameraCapabilities(
            providerId = deviceInfo.providerId,
            deviceId = deviceInfo.id,
            capabilities = CameraCapability.entries.associateWith { capability ->
                val supported = capability in available
                CapabilityInfo(
                    state = if (supported) CapabilityState.Available else if (backend == "stub") {
                        CapabilityState.Degraded
                    } else {
                        CapabilityState.Unavailable
                    },
                    displayName = capability.name,
                    userReadableReason = when {
                        supported -> null
                        backend == "stub" -> "native fd bridge 可用，但尚未接入 libusb/libgphoto2 后端"
                        else -> "libgphoto 未报告该能力"
                    },
                    providerMetadata = mapOf("backend" to backend),
                )
            },
            settings = emptyList(),
            rawDebugInfo = mapOf(
                "nativeBackend" to backend,
                "capabilityJson" to nativeJson,
            ),
        )
    }

    private fun String.booleanValue(key: String): Boolean {
        return contains("\"$key\":true")
    }

    private fun String.stringValue(key: String): String? {
        val marker = "\"$key\":\""
        val start = indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        val valueEnd = indexOf('"', valueStart)
        return if (valueEnd > valueStart) substring(valueStart, valueEnd) else null
    }
}
