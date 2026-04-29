package com.camraw.providers.internalcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
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
import com.camraw.core.camera.api.StoredCameraFile
import com.camraw.core.camera.api.unsupportedError
import com.camraw.core.storage.DefaultCameraStorageController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AndroidInternalCameraProvider(
    private val context: Context,
    private val storageController: CameraStorageController = DefaultCameraStorageController(context),
) : CameraDeviceProvider {
    override val providerId: String = "internal-camera"
    override val providerName: String = "Internal Camera"
    override val priority: Int = 100

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val devices = MutableStateFlow<List<CameraDeviceInfo>>(emptyList())
    private val connectionStates = mutableMapOf<String, MutableStateFlow<ConnectionState>>()

    override suspend fun discoverDevices(): List<CameraDeviceInfo> = withContext(Dispatchers.Default) {
        val discovered = cameraManager.cameraIdList.mapNotNull { cameraId ->
            runCatching {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val facingName = when (lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Camera"
                }
                val caps = buildCapabilities(providerId, cameraId, characteristics)
                CameraDeviceInfo(
                    id = cameraId,
                    displayName = "Internal $facingName Camera",
                    providerId = providerId,
                    providerName = providerName,
                    connectionType = CameraConnectionType.Internal,
                    model = "Camera2-$cameraId",
                    manufacturer = android.os.Build.MANUFACTURER,
                    requiresPermission = context.checkSelfPermission(Manifest.permission.CAMERA) !=
                        PackageManager.PERMISSION_GRANTED,
                    capabilitySummary = caps.capabilities
                        .filter { it.value.state == CapabilityState.Available }
                        .keys
                        .take(4)
                        .map { it.name },
                    debugInfo = mapOf(
                        "cameraId" to cameraId,
                        "hardwareLevel" to hardwareLevelName(characteristics),
                    ),
                )
            }.getOrNull()
        }
        devices.value = discovered
        discovered.forEach { device ->
            connectionStates.getOrPut(device.id) { MutableStateFlow(ConnectionState.Disconnected) }
        }
        discovered
    }

    override suspend fun connect(device: CameraDeviceInfo): CameraSession {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("CAMERA permission is required before connecting ${device.displayName}")
        }
        val state = connectionStates.getOrPut(device.id) { MutableStateFlow(ConnectionState.Disconnected) }
        state.value = ConnectionState.Connecting
        val characteristics = cameraManager.getCameraCharacteristics(device.id)
        val session = AndroidInternalCameraSession(
            context = context,
            cameraManager = cameraManager,
            deviceInfo = device.copy(requiresPermission = false),
            characteristics = characteristics,
            storage = storageController,
        )
        state.value = ConnectionState.Connected(session.sessionId)
        return session
    }

    override suspend fun disconnect(deviceId: String) {
        connectionStates[deviceId]?.value = ConnectionState.Disconnected
    }

    override fun observeDevices(): Flow<List<CameraDeviceInfo>> = devices.asStateFlow()

    override fun observeConnectionState(deviceId: String): Flow<ConnectionState> {
        return connectionStates.getOrPut(deviceId) { MutableStateFlow(ConnectionState.Disconnected) }.asStateFlow()
    }
}

private class AndroidInternalCameraSession(
    private val context: Context,
    private val cameraManager: CameraManager,
    override val deviceInfo: CameraDeviceInfo,
    private val characteristics: CameraCharacteristics,
    override val storage: CameraStorageController,
) : CameraSession {
    override val sessionId: String = UUID.randomUUID().toString()

    private val thread = HandlerThread("CAMRAW-${deviceInfo.id}").apply { start() }
    private val handler = Handler(thread.looper)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventFlow = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 64)
    private val capabilityFlow = MutableStateFlow(buildCapabilities(deviceInfo.providerId, deviceInfo.id, characteristics))
    private val captureMutex = Mutex()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var analysisReader: ImageReader? = null
    private var nextJpegImage: CompletableDeferred<Image>? = null
    private var nextRawImage: CompletableDeferred<Image>? = null

    private val requestState = MutableRequestState(characteristics)
    private val previewController = AndroidInternalPreviewController(this)
    private val captureController = AndroidInternalCaptureController(this)
    private val settingsController = AndroidInternalSettingsController(this)
    private val focusController = AndroidInternalFocusController(this)

    override val capabilities: StateFlow<CameraCapabilities> = capabilityFlow.asStateFlow()
    override val events: Flow<CameraEvent> = eventFlow.asSharedFlow()
    override val preview: PreviewController = previewController
    override val capture: CaptureController = captureController
    override val settings: CameraSettingsController = settingsController
    override val focus: FocusController = focusController
    override val metadata: MetadataController = MetadataController {
        mapOf(
            "providerId" to deviceInfo.providerId,
            "deviceId" to deviceInfo.id,
            "hardwareLevel" to hardwareLevelName(characteristics),
        )
    }

    override suspend fun refreshCapabilities(): CameraCapabilities {
        val refreshed = buildCapabilities(deviceInfo.providerId, deviceInfo.id, characteristics)
        capabilityFlow.value = refreshed
        settingsController.refreshSettings()
        eventFlow.tryEmit(CameraEvent(type = CameraEventType.CapabilityChanged, message = "Internal capabilities refreshed"))
        return refreshed
    }

    override suspend fun close() {
        runCatching { previewController.unbind() }
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        jpegReader?.close()
        rawReader?.close()
        analysisReader?.close()
        thread.quitSafely()
        eventFlow.tryEmit(CameraEvent(type = CameraEventType.Disconnected, message = "Internal camera closed"))
    }

    suspend fun bindPreview(surface: Surface, width: Int, height: Int) {
        ensureCameraOpen()
        previewSurface = surface
        configureSession(width, height)
        updateRepeating()
        eventFlow.tryEmit(CameraEvent(type = CameraEventType.PreviewStarted, message = "Internal preview started"))
    }

    suspend fun unbindPreview() {
        captureSession?.stopRepeating()
        captureSession?.close()
        captureSession = null
        previewSurface = null
        previewController.setState(PreviewState(running = false))
        eventFlow.tryEmit(CameraEvent(type = CameraEventType.PreviewStopped, message = "Internal preview stopped"))
    }

    suspend fun captureStill(request: CameraCaptureRequest): CaptureResult = captureMutex.withLock {
        val activeSession = captureSession
            ?: return captureFailure(request.format, "Preview must be running before capture")
        val device = cameraDevice
            ?: return captureFailure(request.format, "CameraDevice is not open")

        val wantsJpeg = request.format == CaptureFormat.Jpeg || request.format == CaptureFormat.RawAndJpeg
        val wantsRaw = request.format == CaptureFormat.Raw || request.format == CaptureFormat.RawAndJpeg
        if (wantsJpeg && jpegReader == null) {
            return captureFailure(request.format, "JPEG ImageReader is not available in the active capture session")
        }
        if (wantsRaw && rawReader == null) {
            return CaptureResult(
                format = request.format,
                files = emptyList(),
                error = unsupportedError("RAW/DNG"),
            )
        }

        captureController.setState(CaptureState.Capturing)
        eventFlow.tryEmit(CameraEvent(type = CameraEventType.CaptureStarted, message = "Internal capture started"))

        val jpegDeferred = if (wantsJpeg) CompletableDeferred<Image>().also { nextJpegImage = it } else null
        val rawDeferred = if (wantsRaw) CompletableDeferred<Image>().also { nextRawImage = it } else null
        val resultDeferred = CompletableDeferred<TotalCaptureResult>()

        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            jpegReader?.surface?.takeIf { wantsJpeg }?.let { addTarget(it) }
            rawReader?.surface?.takeIf { wantsRaw }?.let { addTarget(it) }
            applyRequestState(this)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        activeSession.capture(
            builder.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    resultDeferred.complete(result)
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: android.hardware.camera2.CaptureFailure,
                ) {
                    resultDeferred.completeExceptionally(RuntimeException(failure.reason.toString()))
                }
            },
            handler,
        )

        return runCatching {
            val totalResult = resultDeferred.await()
            captureController.setState(CaptureState.Writing)
            val files = mutableListOf<StoredCameraFile>()
            jpegDeferred?.await()?.use { image ->
                val bytes = image.planes.first().buffer.readBytes()
                val writeResult = storage.write(
                    StorageWriteRequest(
                        providerId = deviceInfo.providerId,
                        providerName = deviceInfo.providerName,
                        deviceId = deviceInfo.id,
                        deviceName = deviceInfo.displayName,
                        kind = CameraObjectKind.Jpeg,
                        extension = "jpg",
                        mimeType = "image/jpeg",
                        capturedAt = Instant.now(),
                        metadata = request.metadata + currentCaptureMetadata(),
                    ),
                ) { output -> output.write(bytes) }
                writeResult.file?.let(files::add)
                writeResult.sidecar?.let(files::add)
            }
            rawDeferred?.await()?.use { image ->
                val writeResult = storage.write(
                    StorageWriteRequest(
                        providerId = deviceInfo.providerId,
                        providerName = deviceInfo.providerName,
                        deviceId = deviceInfo.id,
                        deviceName = deviceInfo.displayName,
                        kind = CameraObjectKind.Raw,
                        extension = "dng",
                        mimeType = "image/x-adobe-dng",
                        capturedAt = Instant.now(),
                        metadata = request.metadata + currentCaptureMetadata(),
                    ),
                ) { output ->
                    val creator = DngCreator(characteristics, totalResult)
                    try {
                        creator.writeImage(output, image)
                    } finally {
                        creator.close()
                    }
                }
                writeResult.file?.let(files::add)
                writeResult.sidecar?.let(files::add)
            }
            val captureResult = CaptureResult(
                format = request.format,
                files = files,
                metadata = currentCaptureMetadata(),
            )
            captureController.setState(CaptureState.Completed(captureResult))
            eventFlow.tryEmit(CameraEvent(type = CameraEventType.CaptureCompleted, message = "Internal capture completed"))
            captureController.setState(CaptureState.Idle)
            captureResult
        }.getOrElse { throwable ->
            val error = CameraError(
                type = CameraErrorType.CaptureFailed,
                userMessageZh = "拍摄失败",
                debugMessage = throwable.stackTraceToString(),
                fallbackSuggestionZh = "请保持预览开启，并确认相机权限和存储状态。",
            )
            val result = CaptureResult(format = request.format, files = emptyList(), error = error)
            captureController.setState(CaptureState.Failed(error))
            result
        }
    }

    suspend fun refreshSettings(): List<CameraSettingDescriptor> {
        val refreshed = requestState.descriptors()
        settingsController.setSettings(refreshed)
        return refreshed
    }

    suspend fun writeSetting(settingId: String, value: SettingValue): SettingWriteResult {
        val result = requestState.write(settingId, value)
        if (result.success) {
            updateRepeating()
            refreshSettings()
        }
        return result
    }

    suspend fun focusAt(point: FocusPoint): FocusResult {
        val activeSession = captureSession ?: return FocusResult(
            success = false,
            state = focusController.currentState().copy(status = FocusStatus.Failed),
            error = CameraError(
                type = CameraErrorType.PreviewFailed,
                userMessageZh = "需要先开启预览才能对焦",
                debugMessage = "focusAt called without active capture session",
            ),
        )
        val device = cameraDevice ?: return FocusResult(false, focusController.currentState())
        val rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?: Rect(0, 0, 1000, 1000)
        val metering = point.toMeteringRectangle(rect)
        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            previewSurface?.let { addTarget(it) }
            analysisReader?.surface?.let { addTarget(it) }
            applyRequestState(this)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(metering))
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        }
        focusController.setState(
            focusController.currentState().copy(
                point = point,
                status = FocusStatus.Running,
                level = FocusCapabilityLevel.AfRegion,
            ),
        )
        activeSession.capture(builder.build(), null, handler)
        val newState = focusController.currentState().copy(
            point = point,
            status = FocusStatus.Locked,
            level = FocusCapabilityLevel.AfRegion,
        )
        focusController.setState(newState)
        return FocusResult(success = true, state = newState)
    }

    suspend fun updateRepeating() {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return
        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            analysisReader?.surface?.let { addTarget(it) }
            applyRequestState(this)
        }
        session.setRepeatingRequest(builder.build(), null, handler)
        previewController.setState(PreviewState(running = true, fps = 30.0))
    }

    private fun applyRequestState(builder: CaptureRequest.Builder) {
        requestState.applyTo(builder)
    }

    private fun currentCaptureMetadata(): Map<String, String> {
        return mapOf(
            "iso" to (requestState.iso?.toString() ?: "auto"),
            "shutterNs" to (requestState.exposureTimeNs?.toString() ?: "auto"),
            "ev" to requestState.ev.toString(),
            "whiteBalance" to requestState.whiteBalanceLabel,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureCameraOpen(): CameraDevice {
        cameraDevice?.let { return it }
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("CAMERA permission is required")
        }
        return suspendCancellableCoroutine { continuation ->
            cameraManager.openCamera(
                deviceInfo.id,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        continuation.resume(camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("Camera disconnected"))
                        }
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(RuntimeException("Camera error $error"))
                        }
                    }
                },
                handler,
            )
        }
    }

    private suspend fun configureSession(width: Int, height: Int) {
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return
        captureSession?.close()
        captureSession = null
        createReaders(width, height, enableRaw = true, enableAnalysis = true)
        captureSession = runCatching {
            createCameraSession(device, sessionSurfaces(surface))
        }.recoverCatching { firstFailure ->
            closeRawReader()
            createReaders(width, height, enableRaw = false, enableAnalysis = true)
            createCameraSession(device, sessionSurfaces(surface)).also {
                eventFlow.tryEmit(
                    CameraEvent(
                        type = CameraEventType.Debug,
                        message = "Preview session degraded: RAW surface disabled",
                        metadata = mapOf("reason" to firstFailure.message.orEmpty()),
                    ),
                )
            }
        }.recoverCatching { secondFailure ->
            closeAnalysisReader()
            createReaders(width, height, enableRaw = false, enableAnalysis = false)
            createCameraSession(device, sessionSurfaces(surface)).also {
                eventFlow.tryEmit(
                    CameraEvent(
                        type = CameraEventType.Debug,
                        message = "Preview session degraded: analysis surface disabled",
                        metadata = mapOf("reason" to secondFailure.message.orEmpty()),
                    ),
                )
            }
        }.getOrElse { throwable ->
            throw RuntimeException("Unable to configure internal camera preview session", throwable)
        }
    }

    private suspend fun createCameraSession(
        device: CameraDevice,
        surfaces: List<Surface>,
    ): CameraCaptureSession = suspendCancellableCoroutine { continuation ->
        device.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (continuation.isActive) continuation.resume(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    session.close()
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            RuntimeException("Camera session configure failed for ${surfaces.size} surfaces"),
                        )
                    }
                }
            },
            handler,
        )
    }

    private fun sessionSurfaces(preview: Surface): List<Surface> {
        return buildList {
            add(preview)
            jpegReader?.surface?.let(::add)
            rawReader?.surface?.let(::add)
            analysisReader?.surface?.let(::add)
        }
    }

    private fun createReaders(width: Int, height: Int, enableRaw: Boolean, enableAnalysis: Boolean) {
        val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSize = streamMap?.getOutputSizes(ImageFormat.JPEG)?.largest()
            ?: Size(max(1, width), max(1, height))
        val rawSize = streamMap?.getOutputSizes(ImageFormat.RAW_SENSOR)?.largest()
        val yuvSize = streamMap?.getOutputSizes(ImageFormat.YUV_420_888)?.closestTo(Size(320, 180))
            ?: Size(320, 180)

        if (jpegReader == null) {
            jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage()
                    val target = nextJpegImage
                    nextJpegImage = null
                    if (target != null) target.complete(image) else image.close()
                }, handler)
            }
        }
        if (enableRaw && rawReader == null && capabilityFlow.value.isSupported(CameraCapability.CaptureRaw) && rawSize != null) {
            rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage()
                    val target = nextRawImage
                    nextRawImage = null
                    if (target != null) target.complete(image) else image.close()
                }, handler)
            }
        }
        if (enableAnalysis && analysisReader == null) {
            analysisReader = ImageReader.newInstance(yuvSize.width, yuvSize.height, ImageFormat.YUV_420_888, 3).apply {
                setOnImageAvailableListener({ reader ->
                    reader.acquireLatestImage()?.use { image ->
                        val luminance = image.averageLuminance()
                        scope.launch {
                            previewController.emitFrame(
                                PreviewFrame(
                                    id = System.nanoTime(),
                                    timestamp = Instant.now(),
                                    width = image.width,
                                    height = image.height,
                                    luminance = luminance,
                                ),
                            )
                        }
                    }
                }, handler)
            }
        }
    }

    private fun closeRawReader() {
        rawReader?.close()
        rawReader = null
    }

    private fun closeAnalysisReader() {
        analysisReader?.close()
        analysisReader = null
    }

    private fun captureFailure(format: CaptureFormat, debug: String): CaptureResult {
        val error = CameraError(
            type = CameraErrorType.CaptureFailed,
            userMessageZh = "拍摄失败",
            debugMessage = debug,
            fallbackSuggestionZh = "请先进入预览并保持相机连接。",
        )
        return CaptureResult(format = format, files = emptyList(), error = error)
    }
}

private class AndroidInternalPreviewController(
    private val session: AndroidInternalCameraSession,
) : PreviewController {
    private val state = MutableStateFlow(PreviewState())
    private val frameFlow = MutableSharedFlow<PreviewFrame>(extraBufferCapacity = 8)

    override val previewState: StateFlow<PreviewState> = state.asStateFlow()
    override val frames: Flow<PreviewFrame> = frameFlow.asSharedFlow()

    override suspend fun bind(surface: PreviewSurface) {
        val native = surface.nativeSurface as? Surface
            ?: throw IllegalArgumentException("Internal camera requires android.view.Surface")
        runCatching {
            session.bindPreview(native, surface.width, surface.height)
        }.onFailure { throwable ->
            state.value = PreviewState(
                running = false,
                error = CameraError(
                    type = CameraErrorType.PreviewFailed,
                    userMessageZh = "手机原生摄像头预览启动失败",
                    debugMessage = throwable.stackTraceToString(),
                    fallbackSuggestionZh = "请确认相机权限已授予，且没有其他应用正在占用摄像头。",
                ),
            )
        }.getOrThrow()
    }

    override suspend fun unbind() {
        session.unbindPreview()
    }

    fun setState(newState: PreviewState) {
        state.value = newState
    }

    suspend fun emitFrame(frame: PreviewFrame) {
        frameFlow.emit(frame)
    }
}

private class AndroidInternalCaptureController(
    private val session: AndroidInternalCameraSession,
) : CaptureController {
    private val state = MutableStateFlow<CaptureState>(CaptureState.Idle)

    override val captureState: StateFlow<CaptureState> = state.asStateFlow()

    override suspend fun capture(request: CameraCaptureRequest): CaptureResult {
        return session.captureStill(request)
    }

    fun setState(newState: CaptureState) {
        state.value = newState
    }
}

private class AndroidInternalSettingsController(
    private val session: AndroidInternalCameraSession,
) : CameraSettingsController {
    private val state = MutableStateFlow<List<CameraSettingDescriptor>>(emptyList())

    override val settings: StateFlow<List<CameraSettingDescriptor>> = state.asStateFlow()

    init {
        state.value = session.capabilities.value.settings
    }

    override suspend fun refreshSettings(): List<CameraSettingDescriptor> {
        return session.refreshSettings()
    }

    override suspend fun writeSetting(settingId: String, value: SettingValue): SettingWriteResult {
        return session.writeSetting(settingId, value)
    }

    fun setSettings(settings: List<CameraSettingDescriptor>) {
        state.value = settings
    }
}

private class AndroidInternalFocusController(
    private val session: AndroidInternalCameraSession,
) : FocusController {
    private val state = MutableStateFlow(FocusState(level = FocusCapabilityLevel.AfRegion))

    override val focusState: StateFlow<FocusState> = state.asStateFlow()

    override suspend fun focusAt(point: FocusPoint): FocusResult {
        return session.focusAt(point)
    }

    override suspend fun lockFocus(): FocusResult {
        val next = state.value.copy(locked = true, status = FocusStatus.Locked)
        state.value = next
        return FocusResult(success = true, state = next)
    }

    override suspend fun unlockFocus(): FocusResult {
        val next = state.value.copy(locked = false, status = FocusStatus.Idle)
        state.value = next
        return FocusResult(success = true, state = next)
    }

    fun currentState(): FocusState = state.value

    fun setState(next: FocusState) {
        state.value = next
    }
}

private class MutableRequestState(
    private val characteristics: CameraCharacteristics,
) {
    var iso: Int? = null
        private set
    var exposureTimeNs: Long? = null
        private set
    var ev: Int = 0
        private set
    private var whiteBalanceMode: Int = CaptureRequest.CONTROL_AWB_MODE_AUTO
    val whiteBalanceLabel: String
        get() = when (whiteBalanceMode) {
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "daylight"
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "cloudy"
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "tungsten"
            else -> "auto"
        }

    fun descriptors(): List<CameraSettingDescriptor> {
        val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val evRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val awbModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toSet().orEmpty()
        return listOf(
            CameraSettingDescriptor(
                id = "iso",
                displayName = "ISO",
                category = SettingCategory.Exposure,
                valueType = SettingValueType.Choice,
                currentValue = iso?.let { SettingValue.Choice(it.toString(), it.toString()) }
                    ?: SettingValue.Choice("auto", "Auto"),
                availableValues = buildIsoChoices(isoRange),
                writable = isoRange != null,
                state = if (isoRange != null) CapabilityState.Available else CapabilityState.Unavailable,
                userReadableReason = if (isoRange == null) "设备未开放 ISO 手动控制" else null,
            ),
            CameraSettingDescriptor(
                id = "shutter",
                displayName = "Shutter",
                category = SettingCategory.Exposure,
                valueType = SettingValueType.Choice,
                currentValue = exposureTimeNs?.let { SettingValue.Choice(it.toString(), formatShutter(it)) }
                    ?: SettingValue.Choice("auto", "Auto"),
                availableValues = buildShutterChoices(exposureRange),
                writable = exposureRange != null,
                state = if (exposureRange != null) CapabilityState.Available else CapabilityState.Unavailable,
                userReadableReason = if (exposureRange == null) "设备未开放快门手动控制" else null,
            ),
            CameraSettingDescriptor(
                id = "ev",
                displayName = "EV",
                category = SettingCategory.Exposure,
                valueType = SettingValueType.Choice,
                currentValue = SettingValue.Choice(ev.toString(), ev.signedLabel()),
                availableValues = buildEvChoices(evRange),
                writable = evRange != null,
                state = if (evRange != null) CapabilityState.Available else CapabilityState.Unavailable,
            ),
            CameraSettingDescriptor(
                id = "wb",
                displayName = "WB",
                category = SettingCategory.WhiteBalance,
                valueType = SettingValueType.Choice,
                currentValue = SettingValue.Choice(whiteBalanceMode.toString(), whiteBalanceLabel),
                availableValues = buildWbChoices(awbModes),
                writable = awbModes.isNotEmpty(),
                state = if (awbModes.isNotEmpty()) CapabilityState.Available else CapabilityState.Unavailable,
            ),
        )
    }

    fun write(settingId: String, value: SettingValue): SettingWriteResult {
        val choiceId = when (value) {
            is SettingValue.Choice -> value.id
            else -> value.debugValue
        }
        return when (settingId) {
            "iso" -> {
                iso = choiceId.toIntOrNull()
                SettingWriteResult(settingId, value, value, true)
            }
            "shutter" -> {
                exposureTimeNs = choiceId.toLongOrNull()
                SettingWriteResult(settingId, value, value, true)
            }
            "ev" -> {
                ev = choiceId.toIntOrNull() ?: 0
                SettingWriteResult(settingId, value, value, true)
            }
            "wb" -> {
                whiteBalanceMode = choiceId.toIntOrNull() ?: CaptureRequest.CONTROL_AWB_MODE_AUTO
                SettingWriteResult(settingId, value, value, true)
            }
            else -> SettingWriteResult(settingId, value, null, false, unsupportedError(settingId))
        }
    }

    fun applyTo(builder: CaptureRequest.Builder) {
        val currentIso = iso
        val currentExposureTimeNs = exposureTimeNs
        val manualExposure = currentIso != null && currentExposureTimeNs != null
        if (manualExposure) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureTimeNs)
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev)
        }
        builder.set(CaptureRequest.CONTROL_AWB_MODE, whiteBalanceMode)
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
    }
}

private fun buildCapabilities(
    providerId: String,
    deviceId: String,
    characteristics: CameraCharacteristics,
): CameraCapabilities {
    val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()
    val raw = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities
    val manualSensor = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities
    val hasEv = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) != null
    val hasWb = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.isNotEmpty() == true
    val maxAfRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0

    val available = buildSet {
        add(CameraCapability.Preview)
        add(CameraCapability.PreviewFrameAnalysis)
        add(CameraCapability.CaptureJpeg)
        add(CameraCapability.Grid)
        add(CameraCapability.Zebra)
        add(CameraCapability.FocusPeaking)
        add(CameraCapability.Histogram)
        add(CameraCapability.MediaStoreSave)
        add(CameraCapability.SidecarMetadata)
        add(CameraCapability.DebugDump)
        if (raw) {
            add(CameraCapability.CaptureRaw)
            add(CameraCapability.CaptureRawJpeg)
        }
        if (manualSensor) {
            add(CameraCapability.ManualIso)
            add(CameraCapability.ManualShutter)
        }
        if (hasEv) add(CameraCapability.ExposureCompensation)
        if (hasWb) add(CameraCapability.WhiteBalance)
        if (maxAfRegions > 0) add(CameraCapability.TouchFocus)
    }
    return CameraCapabilities(
        providerId = providerId,
        deviceId = deviceId,
        capabilities = CameraCapability.entries.associateWith { capability ->
            CapabilityInfo(
                state = if (capability in available) CapabilityState.Available else CapabilityState.Unavailable,
                displayName = capability.name,
                userReadableReason = if (capability in available) null else "Camera2 characteristics 未报告该能力",
            )
        },
        settings = MutableRequestState(characteristics).descriptors(),
        rawDebugInfo = mapOf(
            "hardwareLevel" to hardwareLevelName(characteristics),
            "raw" to raw,
            "manualSensor" to manualSensor,
            "maxAfRegions" to maxAfRegions,
        ),
    )
}

private fun hardwareLevelName(characteristics: CameraCharacteristics): String {
    return when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }
}

private fun buildIsoChoices(range: Range<Int>?): List<SettingValue> {
    if (range == null) return emptyList()
    val values = listOf(50, 64, 80, 100, 125, 200, 400, 800, 1600, 3200, 6400)
        .filter { it in range.lower..range.upper }
        .ifEmpty { listOf(range.lower, range.upper).distinct() }
    return listOf(SettingValue.Choice("auto", "Auto")) + values.map { SettingValue.Choice(it.toString(), it.toString()) }
}

private fun buildShutterChoices(range: Range<Long>?): List<SettingValue> {
    if (range == null) return emptyList()
    val values = listOf(
        1_000_000_000L / 8000,
        1_000_000_000L / 4000,
        1_000_000_000L / 1000,
        1_000_000_000L / 500,
        1_000_000_000L / 250,
        1_000_000_000L / 125,
        1_000_000_000L / 60,
        1_000_000_000L / 30,
        1_000_000_000L / 15,
        1_000_000_000L,
    ).filter { it in range.lower..range.upper }
    return listOf(SettingValue.Choice("auto", "Auto")) + values.map { SettingValue.Choice(it.toString(), formatShutter(it)) }
}

private fun buildEvChoices(range: Range<Int>?): List<SettingValue> {
    if (range == null) return emptyList()
    return (range.lower..range.upper).map { SettingValue.Choice(it.toString(), it.signedLabel()) }
}

private fun buildWbChoices(modes: Set<Int>): List<SettingValue> {
    val labels = mapOf(
        CaptureRequest.CONTROL_AWB_MODE_AUTO to "Auto",
        CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT to "Daylight",
        CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "Cloudy",
        CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT to "Tungsten",
        CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT to "Fluorescent",
    )
    return modes.mapNotNull { mode -> labels[mode]?.let { SettingValue.Choice(mode.toString(), it) } }
}

private fun formatShutter(nanos: Long): String {
    val seconds = nanos / 1_000_000_000.0
    return if (seconds >= 1.0) {
        "${seconds.toInt()}s"
    } else {
        "1/${max(1, (1.0 / seconds).toInt())}"
    }
}

private fun Int.signedLabel(): String {
    return if (this > 0) "+$this" else toString()
}

private fun Array<Size>.largest(): Size {
    return maxBy { it.width.toLong() * it.height.toLong() }
}

private fun Array<Size>.closestTo(target: Size): Size {
    return minBy { abs((it.width * it.height) - (target.width * target.height)) }
}

private fun ByteBuffer.readBytes(): ByteArray {
    rewind()
    return ByteArray(remaining()).also { get(it) }
}

private fun Image.averageLuminance(): Float {
    val plane = planes.firstOrNull() ?: return 0.5f
    val buffer = plane.buffer
    val step = max(1, buffer.remaining() / 512)
    var index = 0
    var count = 0
    var sum = 0L
    while (index < buffer.remaining()) {
        sum += buffer.get(index).toInt() and 0xFF
        count += 1
        index += step
    }
    return (sum.toFloat() / max(1, count) / 255f).coerceIn(0f, 1f)
}

private fun FocusPoint.toMeteringRectangle(activeArray: Rect): MeteringRectangle {
    val size = min(activeArray.width(), activeArray.height()) / 8
    val centerX = activeArray.left + (normalizedX.coerceIn(0f, 1f) * activeArray.width()).toInt()
    val centerY = activeArray.top + (normalizedY.coerceIn(0f, 1f) * activeArray.height()).toInt()
    val left = (centerX - size / 2).coerceIn(activeArray.left, activeArray.right - size)
    val top = (centerY - size / 2).coerceIn(activeArray.top, activeArray.bottom - size)
    return MeteringRectangle(Rect(left, top, left + size, top + size), MeteringRectangle.METERING_WEIGHT_MAX)
}

private inline fun Image.use(block: (Image) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
