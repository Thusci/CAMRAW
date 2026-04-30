package com.camraw.providers.fake

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.camraw.core.camera.api.CameraCapabilities
import com.camraw.core.camera.api.CameraCapability
import com.camraw.core.camera.api.CameraCaptureRequest
import com.camraw.core.camera.api.CameraConnectionType
import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CameraDeviceProvider
import com.camraw.core.camera.api.CameraError
import com.camraw.core.camera.api.CameraErrorType
import com.camraw.core.camera.api.CameraEvent
import com.camraw.core.camera.api.CameraEventType
import com.camraw.core.camera.api.CameraImportBrowser
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
import com.camraw.core.camera.api.StorageWriteRequest
import com.camraw.core.camera.api.TetherCaptureController
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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import kotlin.math.sin

class FakeCameraProvider(
    private val storageController: CameraStorageController? = null,
) : CameraDeviceProvider {
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
        val session = FakeCameraSession(device, storageController)
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
    override val storage: CameraStorageController?,
) : CameraSession {
    override val sessionId: String = UUID.randomUUID().toString()
    private val eventFlow = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 32)
    private val capabilityFlow = MutableStateFlow(fakeCapabilities(deviceInfo))

    override val capabilities: StateFlow<CameraCapabilities> = capabilityFlow.asStateFlow()
    override val events: Flow<CameraEvent> = eventFlow.asSharedFlow()
    override val preview: PreviewController = FakePreviewController(eventFlow)
    override val capture: CaptureController = FakeCaptureController(eventFlow, deviceInfo, storage)
    override val settings: CameraSettingsController = FakeSettingsController(capabilityFlow.value.settings)
    override val focus: FocusController = FakeFocusController()
    override val tether: TetherCaptureController? = null
    override val importBrowser: CameraImportBrowser? = null
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
    private val deviceInfo: CameraDeviceInfo,
    private val storage: CameraStorageController?,
) : CaptureController {
    private val state = MutableStateFlow<CaptureState>(CaptureState.Idle)

    override val captureState: StateFlow<CaptureState> = state.asStateFlow()

    override suspend fun capture(request: CameraCaptureRequest): CaptureResult {
        state.value = CaptureState.Capturing
        events.tryEmit(CameraEvent(type = CameraEventType.CaptureStarted, message = "Fake capture started"))
        delay(180)
        state.value = CaptureState.Writing

        val metadata = request.metadata + mapOf(
            "simulated" to "true",
            "provider" to deviceInfo.providerId,
            "requestedFormat" to request.format.name,
        )
        val storageController = storage
        val result = if (storageController == null) {
            val error = CameraError(
                type = CameraErrorType.StorageFailed,
                userMessageZh = "虚拟相机没有可用的相册存储",
                debugMessage = "FakeCameraProvider was created without a CameraStorageController",
                fallbackSuggestionZh = "请使用应用内置入口启动虚拟相机，或切换到手机原生摄像头。",
                causeCode = "FAKE_STORAGE_MISSING",
            )
            CaptureResult(format = request.format, files = emptyList(), metadata = metadata, error = error)
        } else {
            val capturedAt = Instant.now()
            val bytes = createFakeJpeg(capturedAt)
            val writeResult = storageController.write(
                StorageWriteRequest(
                    providerId = deviceInfo.providerId,
                    providerName = deviceInfo.providerName,
                    deviceId = deviceInfo.id,
                    deviceName = deviceInfo.displayName,
                    kind = CameraObjectKind.Jpeg,
                    extension = "jpg",
                    mimeType = "image/jpeg",
                    capturedAt = capturedAt,
                    metadata = metadata,
                ),
            ) { output -> output.write(bytes) }
            val files = buildList {
                writeResult.file?.let(::add)
                writeResult.sidecar?.let(::add)
            }
            CaptureResult(
                format = request.format,
                files = files,
                metadata = metadata,
                error = if (writeResult.success && writeResult.file != null) null else writeResult.error ?: fakeStorageError(),
            )
        }

        val error = result.error
        if (error == null) {
            state.value = CaptureState.Completed(result)
            events.tryEmit(CameraEvent(type = CameraEventType.CaptureCompleted, message = "Fake capture completed"))
        } else {
            state.value = CaptureState.Failed(error)
            events.tryEmit(CameraEvent(type = CameraEventType.Error, message = "Fake capture storage failed"))
        }
        delay(250)
        state.value = CaptureState.Idle
        return result
    }
}

private suspend fun createFakeJpeg(capturedAt: Instant): ByteArray = withContext(Dispatchers.Default) {
    val width = 1280
    val height = 720
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        intArrayOf(Color.rgb(16, 22, 30), Color.rgb(20, 94, 112), Color.rgb(226, 236, 224)),
        floatArrayOf(0f, 0.58f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    paint.shader = null
    paint.color = Color.argb(180, 255, 255, 255)
    paint.strokeWidth = 2f
    for (x in width / 4 until width step width / 4) {
        canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
    }
    for (y in height / 3 until height step height / 3) {
        canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
    }

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 8f
    paint.color = Color.argb(210, 185, 236, 255)
    canvas.drawCircle(width / 2f, height / 2f, 96f, paint)
    paint.style = Paint.Style.FILL
    paint.textSize = 42f
    paint.color = Color.WHITE
    canvas.drawText("CAMRAW Virtual Capture", 56f, 92f, paint)
    paint.textSize = 28f
    paint.color = Color.argb(210, 255, 255, 255)
    canvas.drawText(capturedAt.toString(), 56f, 136f, paint)

    try {
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "Bitmap JPEG compression failed" }
            output.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}

private fun fakeStorageError(): CameraError {
    return CameraError(
        type = CameraErrorType.StorageFailed,
        userMessageZh = "虚拟相机照片保存失败",
        debugMessage = "CameraStorageController returned success=false without an error",
        fallbackSuggestionZh = "请确认系统相册可写、存储空间充足后重试。",
        causeCode = "FAKE_STORAGE_FAILED",
    )
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
        CameraCapability.MediaStoreSave,
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
