package com.camraw.core.metadata

import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CameraObject
import com.camraw.core.camera.api.CaptureFormat
import java.time.Instant

data class SidecarMetadata(
    val providerId: String,
    val providerName: String,
    val deviceId: String,
    val deviceName: String,
    val cameraModel: String?,
    val originalFileName: String?,
    val objectHandle: String?,
    val captureFormat: CaptureFormat?,
    val capturedAt: Instant?,
    val importedAt: Instant = Instant.now(),
    val extra: Map<String, String> = emptyMap(),
)

object SidecarMetadataBuilder {
    fun forImport(
        deviceInfo: CameraDeviceInfo,
        cameraObject: CameraObject,
        extra: Map<String, String> = emptyMap(),
    ): SidecarMetadata {
        return SidecarMetadata(
            providerId = deviceInfo.providerId,
            providerName = deviceInfo.providerName,
            deviceId = deviceInfo.id,
            deviceName = deviceInfo.displayName,
            cameraModel = deviceInfo.model,
            originalFileName = cameraObject.fileName,
            objectHandle = cameraObject.objectId,
            captureFormat = null,
            capturedAt = cameraObject.capturedAt,
            extra = cameraObject.providerMetadata + extra,
        )
    }
}
