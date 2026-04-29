package com.camraw.providers.fake

import com.camraw.core.camera.api.CameraCapabilities
import com.camraw.core.camera.api.CameraCapability
import com.camraw.core.camera.api.CameraConnectionType
import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CameraDeviceProvider
import com.camraw.core.camera.api.CameraEvent
import com.camraw.core.camera.api.CameraEventType
import com.camraw.core.camera.api.CameraObjectKind
import com.camraw.core.camera.api.CameraSession
import com.camraw.core.camera.api.CameraSettingDescriptor
import com.camraw.core.camera.api.CameraSettingsController
import com.camraw.core.camera.api.CameraStorageController
import com.camraw.core.camera.api.CapabilityInfo
import com.camraw.core.camera.api.CapabilityState
import com.camraw.core.camera.api.CaptureController
import com.camraw.core.camera.api.CaptureFormat
import com.camraw.core.camera.api.CaptureResult
import com.camraw.core.camera.api.CaptureState
import com.camraw.core.camera.api.ConnectionState
import com.camraw.core.camera.api.FocusCapabilityLevel
import com.camraw.core.camera.api.FocusController
import com.camraw.core.camera.api.FocusPoint
import com.camraw.core.camera.api.FocusResult
import com.camraw.core.camera.api.FocusState
import com.camraw.core.camera.api.FocusStatus
import com.camraw.core.camera.api.MetadataController
import com.camraw.core.camera.api.PreviewController
import com.camraw.core.camera.api.PreviewFrame
import com.camraw.core.camera.api.PreviewState
import com.camraw.core.camera.api.PreviewSurface
import com.camraw.core.camera.api.SettingCategory
import com.camraw.core.camera.api.SettingValue
import com.camraw.core.camera.api.SettingValueType
import com.camraw.core.camera.api.SettingWriteResult
import com.camraw.core.camera.api.StoredCameraFile
import com.camraw.core.camera.api.CameraCaptureRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import kotlin.math.sin

class FakeCameraProvider : CameraDeviceProvider {
    override val providerId: String = "fake-camera"
    override val providerName: String = "Fake Camera"
    override val priority: Int = 10

    private val device = CameraDeviceInfo(
        id = "fake-camera-1",
        displayName = "CAMRAW Virtual Camera",
        providerId = providerId,
        providerName = providerName,
        connectionType = CameraConnectionType.Virtual,
        model = "Virtual Sensor",
        manufacturer = "CAMRAW",
        capabilitySummary = listOf("Preview", "JPEG", "Manual controls", "Debug"),
        debugInfo = mapOf("source" to "fake-provider"),
    )
    private val devices = MutableStateFlow(listOf(device))
    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    override suspend fun discoverDevices(): List<CameraDeviceInfo> {
        return devices.value
    }

    override suspend fun connect(device: CameraDeviceInfo): CameraSession {
        connectionState.value = ConnectionState.Connecting
        val session = FakeCameraSession(device)
        connectionState.value = ConnectionState.Connected(session.sessionId)
        return session
    }

    override suspend fun disconnect(deviceId: String) {
        connectionState.value = ConnectionState.Disconnected
    }

    override fun observeDevices(): Flow<List<CameraDeviceInfo>> = devices.asStateFlow()

    override fun observeConnectionState(deviceId: String): Flow<ConnectionState> = connectionState.asStateFlow()
}

class FakeCameraSession(
    override val deviceInfo: CameraDeviceInfo,
) : CameraSession {
    override val sessionId: String = UUID.randomUUID().toString()
    private val eventFlow = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 32)
    private val capabilityFlow = MutableStateFlow(fakeCapabilities(deviceInfo))

    override val capabilities: StateFlow<CameraCapabilities> = capabilityFlow.asStateFlow()
    override val events: Flow<CameraEvent> = eventFlow.asSharedFlow()
    override val preview: PreviewController = FakePreviewController(eventFlow)
    override val capture: CaptureController = FakeCaptureController(eventFlow)
    override val settings: CameraSettingsController = FakeSettingsController(capabilityFlow.value.settings)
    override val focus: FocusController = FakeFocusController()
    override val storage: CameraStorageController? = null
    override val metadata: MetadataController = MetadataController {
        mapOf("provider" to "fake-camera", "sessionId" to sessionId)
    }

    override suspend fun refreshCapabilities(): CameraCapabilities {
        capabilityFlow.value = fakeCapabilities(deviceInfo)
        eventFlow.tryEmit(CameraEvent(type = CameraEventType.CapabilityChanged, message = "Fake capabilities refreshed"))
        return capabilityFlow.value
    }

    override suspend fun close() {
        preview.unbind()
        eventFlow.tryEmit(CameraEvent(type = CameraEventType.Disconnected, message = "Fake camera closed"))
    }
}

private class FakePreviewController(
    private val events: MutableSharedFlow<CameraEvent>,
) : PreviewController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow(PreviewState())
    private val frameFlow = MutableSharedFlow<PreviewFrame>(extraBufferCapacity = 8)
    private var job: Job? = null

    override val previewState: StateFlow<PreviewState> = state.asStateFlow()
    override val frames: Flow<PreviewFrame> = frameFlow.asSharedFlow()

    override suspend fun bind(surface: PreviewSurface) {
        job?.cancel()
        state.value = PreviewState(running = true, fps = 30.0)
        events.tryEmit(CameraEvent(type = CameraEventType.PreviewStarted, message = "Fake preview started"))
        job = scope.launch {
            var frameId = 0L
            while (isActive) {
                val luminance = (0.5f + (sin(frameId / 18.0) * 0.25f)).toFloat()
                frameFlow.emit(
                    PreviewFrame(
                        id = frameId++,
                        timestamp = Instant.now(),
                        width = 64,
                        height = 36,
                        luminance = luminance.coerceIn(0f, 1f),
                    ),
                )
                delay(33)
            }
        }
    }

    override suspend fun unbind() {
        job?.cancel()
        job = null
        state.value = PreviewState(running = false)
        events.tryEmit(CameraEvent(type = CameraEventType.PreviewStopped, message = "Fake preview stopped"))
    }
}

private class FakeCaptureController(
    private val events: MutableSharedFlow<CameraEvent>,
) : CaptureController {
    private val state = MutableStateFlow<CaptureState>(CaptureState.Idle)

    override val captureState: StateFlow<CaptureState> = state.asStateFlow()

    override suspend fun capture(request: CameraCaptureRequest): CaptureResult {
        state.value = CaptureState.Capturing
        events.tryEmit(CameraEvent(type = CameraEventType.CaptureStarted, message = "Fake capture started"))
        delay(180)
        state.value = CaptureState.Writing
        delay(90)
        val result = CaptureResult(
            format = request.format,
            files = listOf(
                StoredCameraFile(
                    uri = "memory://fake/${UUID.randomUUID()}.jpg",
                    displayName = "Fake_${Instant.now().toEpochMilli()}.jpg",
                    mimeType = "image/jpeg",
                    kind = CameraObjectKind.Jpeg,
                    bytes = 256L,
                ),
            ),
            metadata = mapOf("simulated" to "true"),
        )
        state.value = CaptureState.Completed(result)
        events.tryEmit(CameraEvent(type = CameraEventType.CaptureCompleted, message = "Fake capture completed"))
        delay(250)
        state.value = CaptureState.Idle
        return result
    }
}

private class FakeSettingsController(
    initialSettings: List<CameraSettingDescriptor>,
) : CameraSettingsController {
    private val state = MutableStateFlow(initialSettings)

    override val settings: StateFlow<List<CameraSettingDescriptor>> = state.asStateFlow()

    override suspend fun refreshSettings(): List<CameraSettingDescriptor> = state.value

    override suspend fun writeSetting(settingId: String, value: SettingValue): SettingWriteResult {
        state.value = state.value.map { setting ->
            if (setting.id == settingId) setting.copy(currentValue = value) else setting
        }
        return SettingWriteResult(
            settingId = settingId,
            requested = value,
            confirmed = value,
            success = true,
        )
    }
}

private class FakeFocusController : FocusController {
    private val state = MutableStateFlow(FocusState(level = FocusCapabilityLevel.AfPoint))

    override val focusState: StateFlow<FocusState> = state.asStateFlow()

    override suspend fun focusAt(point: FocusPoint): FocusResult {
        state.value = state.value.copy(point = point, status = FocusStatus.Running)
        delay(120)
        state.value = state.value.copy(point = point, status = FocusStatus.Locked)
        return FocusResult(success = true, state = state.value)
    }

    override suspend fun lockFocus(): FocusResult {
        state.value = state.value.copy(locked = true, status = FocusStatus.Locked)
        return FocusResult(success = true, state = state.value)
    }

    override suspend fun unlockFocus(): FocusResult {
        state.value = state.value.copy(locked = false, status = FocusStatus.Idle)
        return FocusResult(success = true, state = state.value)
    }
}

private fun fakeCapabilities(device: CameraDeviceInfo): CameraCapabilities {
    val available = listOf(
        CameraCapability.Preview,
        CameraCapability.PreviewFrameAnalysis,
        CameraCapability.CaptureJpeg,
        CameraCapability.ManualIso,
        CameraCapability.ManualShutter,
        CameraCapability.ExposureCompensation,
        CameraCapability.WhiteBalance,
        CameraCapability.TouchFocus,
        CameraCapability.Zebra,
        CameraCapability.FocusPeaking,
        CameraCapability.Histogram,
        CameraCapability.Grid,
        CameraCapability.SidecarMetadata,
        CameraCapability.DebugDump,
    )
    val capabilityMap = CameraCapability.entries.associateWith { capability ->
        CapabilityInfo(
            state = if (capability in available) CapabilityState.Available else CapabilityState.Unavailable,
            displayName = capability.name,
            userReadableReason = if (capability in available) null else "虚拟相机未模拟该能力",
        )
    }
    return CameraCapabilities(
        providerId = device.providerId,
        deviceId = device.id,
        capabilities = capabilityMap,
        settings = listOf(
            CameraSettingDescriptor(
                id = "iso",
                displayName = "ISO",
                category = SettingCategory.Exposure,
                valueType = SettingValueType.Choice,
                currentValue = SettingValue.Choice("200", "200"),
                availableValues = listOf("100", "200", "400", "800", "1600").map { SettingValue.Choice(it, it) },
                writable = true,
                state = CapabilityState.Available,
            ),
            CameraSettingDescriptor(
                id = "shutter",
                displayName = "Shutter",
                category = SettingCategory.Exposure,
                valueType = SettingValueType.Choice,
                currentValue = SettingValue.Choice("1/125", "1/125"),
                availableValues = listOf("1/30", "1/60", "1/125", "1/250", "1/500").map {
                    SettingValue.Choice(it, it)
                },
                writable = true,
                state = CapabilityState.Available,
            ),
            CameraSettingDescriptor(
                id = "wb",
                displayName = "WB",
                category = SettingCategory.WhiteBalance,
                valueType = SettingValueType.Choice,
                currentValue = SettingValue.Choice("auto", "Auto"),
                availableValues = listOf(
                    SettingValue.Choice("auto", "Auto"),
                    SettingValue.Choice("daylight", "Daylight"),
                    SettingValue.Choice("tungsten", "Tungsten"),
                ),
                writable = true,
                state = CapabilityState.Available,
            ),
        ),
        rawDebugInfo = mapOf("virtual" to true),
    )
}
