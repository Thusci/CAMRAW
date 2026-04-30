package com.camraw.providers.libgphoto

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import com.camraw.core.camera.api.CameraCapabilities
import com.camraw.core.camera.api.CameraCaptureRequest
import com.camraw.core.camera.api.CameraConnectionType
import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CameraDeviceProvider
import com.camraw.core.camera.api.CameraError
import com.camraw.core.camera.api.CameraErrorType
import com.camraw.core.camera.api.CameraEvent
import com.camraw.core.camera.api.CameraEventType
import com.camraw.core.camera.api.CameraImportBrowser
import com.camraw.core.camera.api.CameraObject
import com.camraw.core.camera.api.CameraSession
import com.camraw.core.camera.api.CameraSettingDescriptor
import com.camraw.core.camera.api.CameraSettingsController
import com.camraw.core.camera.api.CameraStorageController
import com.camraw.core.camera.api.CameraStorageVolume
import com.camraw.core.camera.api.CapabilityState
import com.camraw.core.camera.api.CaptureController
import com.camraw.core.camera.api.CaptureResult
import com.camraw.core.camera.api.CaptureState
import com.camraw.core.camera.api.ConnectionState
import com.camraw.core.camera.api.FocusController
import com.camraw.core.camera.api.MetadataController
import com.camraw.core.camera.api.PreviewController
import com.camraw.core.camera.api.SettingValue
import com.camraw.core.camera.api.SettingWriteResult
import com.camraw.core.camera.api.StorageWriteRequest
import com.camraw.core.camera.api.StorageWriteResult
import com.camraw.core.camera.api.TetherCaptureController
import com.camraw.core.camera.api.TetherDownloadItem
import com.camraw.core.camera.api.TetherDownloadQueueState
import com.camraw.core.camera.api.TetherDownloadStatus
import com.camraw.core.camera.api.TetherImportPolicy
import com.camraw.core.camera.api.TetherIncomingObject
import com.camraw.core.camera.api.TetherSession
import com.camraw.core.camera.api.unsupportedError
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
import java.util.UUID

class LibGPhotoProvider(
    private val context: Context,
    private val storageController: CameraStorageController? = null,
) : CameraDeviceProvider {
    override val providerId: String = "libgphoto"
    override val providerName: String = "libgphoto USB"
    override val priority: Int = 80

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actionUsbPermission = "${appContext.packageName}.CAMRAW_USB_PERMISSION"
    private val devices = MutableStateFlow<List<CameraDeviceInfo>>(emptyList())
    private val connectionStates = mutableMapOf<String, MutableStateFlow<ConnectionState>>()
    private val sessions = mutableMapOf<String, LibGPhotoCameraSession>()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != actionUsbPermission) return
            scope.launch { discoverDevices() }
        }
    }

    init {
        val filter = IntentFilter(actionUsbPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(permissionReceiver, filter)
        }
    }

    override suspend fun discoverDevices(): List<CameraDeviceInfo> = withContext(Dispatchers.Default) {
        val discovered = usbManager.deviceList.values.mapNotNull { usbDevice ->
            val descriptor = LibGPhotoUsbDeviceMatcher.fromDevice(usbDevice)
            if (!LibGPhotoUsbDeviceMatcher.isLikelyStillCamera(descriptor)) return@mapNotNull null
            val hasPermission = usbManager.hasPermission(usbDevice)
            val displayName = LibGPhotoUsbDeviceMatcher.displayName(
                descriptor = descriptor,
                productName = runCatching { usbDevice.productName }.getOrNull(),
                manufacturerName = runCatching { usbDevice.manufacturerName }.getOrNull(),
            )
            val deviceId = stableDeviceId(usbDevice)
            CameraDeviceInfo(
                id = deviceId,
                displayName = displayName,
                providerId = providerId,
                providerName = providerName,
                connectionType = CameraConnectionType.UsbGPhoto,
                model = runCatching { usbDevice.productName }.getOrNull() ?: "USB-${usbDevice.productId}",
                manufacturer = runCatching { usbDevice.manufacturerName }.getOrNull(),
                requiresPermission = !hasPermission,
                setupHint = if (hasPermission) {
                    "USB 已授权，点击连接 libgphoto bridge"
                } else {
                    "点击后系统会请求 USB 权限；授权后重新点击连接"
                },
                capabilitySummary = if (hasPermission) {
                    listOf("USB", "fd bridge", "libgphoto")
                } else {
                    listOf("USB permission required")
                },
                debugInfo = LibGPhotoUsbDeviceMatcher.debugInfo(descriptor) + mapOf(
                    "usbDeviceName" to usbDevice.deviceName,
                    "usbDeviceId" to usbDevice.deviceId.toString(),
                    "usbPermission" to hasPermission.toString(),
                    "nativeBackend" to LibGPhotoNative.backendVersion(),
                ),
            )
        }.sortedWith(compareBy<CameraDeviceInfo> { it.requiresPermission }.thenBy { it.displayName })

        devices.value = discovered
        discovered.forEach { device ->
            connectionStates.getOrPut(device.id) { MutableStateFlow(ConnectionState.Disconnected) }
        }
        discovered
    }

    override suspend fun connect(device: CameraDeviceInfo): CameraSession {
        val usbDevice = findUsbDevice(device)
            ?: throw LibGPhotoException(deviceNotFound(device))
        val state = connectionStates.getOrPut(device.id) { MutableStateFlow(ConnectionState.Disconnected) }
        if (!usbManager.hasPermission(usbDevice)) {
            requestPermission(device)
            val error = LibGPhotoErrorMapper.permissionRequired(usbDevice.deviceName)
            state.value = ConnectionState.Error(error)
            throw LibGPhotoException(error)
        }

        state.value = ConnectionState.Connecting
        val connection = usbManager.openDevice(usbDevice)
            ?: throw LibGPhotoException(openFailed(device, usbDevice))
        val openResult = LibGPhotoNative.openFromFd(connection.fileDescriptor, usbDevice.vendorId, usbDevice.productId)
        val handle = openResult.value
        if (handle == null) {
            connection.close()
            val error = openResult.error ?: openFailed(device, usbDevice)
            state.value = ConnectionState.Error(error)
            throw LibGPhotoException(error)
        }

        val authorizedDevice = device.copy(
            requiresPermission = false,
            setupHint = "USB fd bridge 已打开；nativeBackend=${LibGPhotoNative.backendVersion()}",
            debugInfo = device.debugInfo + mapOf(
                "fdBridge" to "open",
                "nativeHandle" to handle.toString(),
                "nativeBackend" to LibGPhotoNative.backendVersion(),
            ),
        )
        val session = LibGPhotoCameraSession(
            deviceInfo = authorizedDevice,
            usbConnection = connection,
            nativeHandle = handle,
            storage = storageController,
        )
        sessions[device.id] = session
        state.value = ConnectionState.Connected(session.sessionId)
        return session
    }

    override suspend fun disconnect(deviceId: String) {
        sessions.remove(deviceId)?.close()
        connectionStates[deviceId]?.value = ConnectionState.Disconnected
    }

    override fun observeDevices(): Flow<List<CameraDeviceInfo>> = devices.asStateFlow()

    override fun observeConnectionState(deviceId: String): Flow<ConnectionState> {
        return connectionStates.getOrPut(deviceId) { MutableStateFlow(ConnectionState.Disconnected) }.asStateFlow()
    }

    fun requestPermission(device: CameraDeviceInfo): Boolean {
        val usbDevice = findUsbDevice(device) ?: return false
        if (usbManager.hasPermission(usbDevice)) return true
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val intent = PendingIntent.getBroadcast(
            appContext,
            usbDevice.deviceId,
            Intent(actionUsbPermission).setPackage(appContext.packageName),
            flags,
        )
        usbManager.requestPermission(usbDevice, intent)
        return true
    }

    private fun findUsbDevice(device: CameraDeviceInfo): UsbDevice? {
        val expectedName = device.debugInfo["usbDeviceName"]
        val expectedUsbId = device.debugInfo["usbDeviceId"]
        return usbManager.deviceList.values.firstOrNull { usbDevice ->
            usbDevice.deviceName == expectedName ||
                usbDevice.deviceId.toString() == expectedUsbId ||
                stableDeviceId(usbDevice) == device.id
        }
    }

    private fun stableDeviceId(device: UsbDevice): String {
        return "usb-${device.vendorId}-${device.productId}-${device.deviceId}"
    }

    private fun deviceNotFound(device: CameraDeviceInfo): CameraError {
        return CameraError(
            type = CameraErrorType.DeviceNotFound,
            userMessageZh = "USB 相机已断开",
            debugMessage = "Could not find USB device for ${device.id}",
            fallbackSuggestionZh = "请重新插拔 USB 设备，然后刷新设备列表。",
            causeCode = "USB_DEVICE_NOT_FOUND",
        )
    }

    private fun openFailed(device: CameraDeviceInfo, usbDevice: UsbDevice): CameraError {
        return CameraError(
            type = CameraErrorType.ConnectionFailed,
            userMessageZh = "${device.displayName} USB 打开失败",
            debugMessage = "openDevice returned null or fd invalid for ${usbDevice.deviceName}",
            fallbackSuggestionZh = "请确认 USB 权限已授予，设备没有被系统相机或其他应用占用。",
            causeCode = "USB_OPEN_FAILED",
        )
    }
}

private class LibGPhotoCameraSession(
    override val deviceInfo: CameraDeviceInfo,
    private val usbConnection: UsbDeviceConnection,
    private val nativeHandle: Long,
    override val storage: CameraStorageController?,
) : CameraSession {
    override val sessionId: String = UUID.randomUUID().toString()
    private val eventFlow = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 64)
    private val capabilityFlow = MutableStateFlow(loadCapabilities())
    private val tetherController = LibGPhotoTetherController(nativeHandle, eventFlow)
    private val importController = LibGPhotoImportBrowser(nativeHandle, deviceInfo, storage)

    override val capabilities: StateFlow<CameraCapabilities> = capabilityFlow.asStateFlow()
    override val events: Flow<CameraEvent> = eventFlow.asSharedFlow()
    override val preview: PreviewController? = null
    override val capture: CaptureController = LibGPhotoCaptureController(nativeHandle, eventFlow)
    override val settings: CameraSettingsController = LibGPhotoSettingsController(nativeHandle)
    override val focus: FocusController? = null
    override val tether: TetherCaptureController = tetherController
    override val importBrowser: CameraImportBrowser = importController
    override val metadata: MetadataController = MetadataController {
        mapOf(
            "provider" to deviceInfo.providerId,
            "sessionId" to sessionId,
            "nativeBackend" to LibGPhotoNative.backendVersion(),
        )
    }

    override suspend fun refreshCapabilities(): CameraCapabilities {
        val refreshed = loadCapabilities()
        capabilityFlow.value = refreshed
        eventFlow.tryEmit(CameraEvent(type = CameraEventType.CapabilityChanged, message = "libgphoto capabilities refreshed"))
        return refreshed
    }

    override suspend fun close() {
        tetherController.close()
        LibGPhotoNative.cancelOperation(nativeHandle)
        LibGPhotoNative.close(nativeHandle)
        usbConnection.close()
        eventFlow.tryEmit(CameraEvent(type = CameraEventType.Disconnected, message = "libgphoto USB session closed"))
    }

    private fun loadCapabilities(): CameraCapabilities {
        val nativeJson = LibGPhotoNative.getCapabilities(nativeHandle).value
            ?: "{\"backend\":\"error\",\"error\":\"capability_read_failed\"}"
        return LibGPhotoCapabilityMapper.fromNativeJson(deviceInfo, nativeJson)
    }
}

private class LibGPhotoCaptureController(
    private val nativeHandle: Long,
    private val events: MutableSharedFlow<CameraEvent>,
) : CaptureController {
    private val state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    override val captureState: StateFlow<CaptureState> = state.asStateFlow()

    override suspend fun capture(request: CameraCaptureRequest): CaptureResult {
        state.value = CaptureState.Capturing
        events.tryEmit(CameraEvent(type = CameraEventType.CaptureStarted, message = "libgphoto capture requested"))
        val result = LibGPhotoNative.capture(nativeHandle)
        val error = result.error ?: CameraError(
            type = CameraErrorType.CapabilityUnsupported,
            userMessageZh = "当前 libgphoto 后端暂不支持手机控制外接相机拍摄",
            debugMessage = result.value ?: "capture returned no payload",
            fallbackSuggestionZh = "stub backend 只能验证 USB fd/JNI 链路；接入 libgphoto2 后会启用 capture。",
            causeCode = "GPHOTO_CAPTURE_UNSUPPORTED",
        )
        state.value = CaptureState.Failed(error)
        events.tryEmit(CameraEvent(type = CameraEventType.Error, message = error.userMessageZh))
        state.value = CaptureState.Idle
        return CaptureResult(format = request.format, files = emptyList(), error = error)
    }
}

private class LibGPhotoSettingsController(
    private val nativeHandle: Long,
) : CameraSettingsController {
    private val state = MutableStateFlow<List<CameraSettingDescriptor>>(emptyList())
    override val settings: StateFlow<List<CameraSettingDescriptor>> = state.asStateFlow()

    override suspend fun refreshSettings(): List<CameraSettingDescriptor> {
        LibGPhotoNative.getConfigJson(nativeHandle)
        state.value = emptyList()
        return state.value
    }

    override suspend fun writeSetting(settingId: String, value: SettingValue): SettingWriteResult {
        val error = LibGPhotoNative.setConfigValue(nativeHandle, settingId, value.debugValue)
            ?: unsupportedError("外接相机设置写入")
        return SettingWriteResult(
            settingId = settingId,
            requested = value,
            confirmed = null,
            success = false,
            error = error,
        )
    }
}

private class LibGPhotoImportBrowser(
    private val nativeHandle: Long,
    private val deviceInfo: CameraDeviceInfo,
    private val storage: CameraStorageController?,
) : CameraImportBrowser {
    override suspend fun listStorages(): List<CameraStorageVolume> {
        return listOf(
            CameraStorageVolume(
                id = "camera",
                displayName = "Camera Storage",
                rootPath = "/",
                providerMetadata = mapOf("nativeBackend" to LibGPhotoNative.backendVersion()),
            ),
        )
    }

    override suspend fun listObjects(folder: String): List<CameraObject> {
        val result = LibGPhotoNative.listFiles(nativeHandle, folder)
        result.error?.let { throw LibGPhotoException(it) }
        return emptyList()
    }

    override suspend fun downloadObject(
        cameraObject: CameraObject,
        request: StorageWriteRequest?,
    ): StorageWriteResult {
        val targetRequest = request ?: StorageWriteRequest(
            providerId = deviceInfo.providerId,
            providerName = deviceInfo.providerName,
            deviceId = deviceInfo.id,
            deviceName = deviceInfo.displayName,
            kind = cameraObject.kind,
            extension = cameraObject.fileName.substringAfterLast('.', "bin"),
            mimeType = "application/octet-stream",
            originalFileName = cameraObject.fileName,
            objectHandle = cameraObject.objectId,
        )
        val controller = storage
        val error = CameraError(
            type = CameraErrorType.DownloadFailed,
            userMessageZh = "当前 libgphoto 后端暂不支持文件下载",
            debugMessage = "downloadObject=${cameraObject.fileName} request=$targetRequest backend=${LibGPhotoNative.backendVersion()}",
            fallbackSuggestionZh = "接入 libgphoto2 download backend 后会写入 MediaStore。",
            causeCode = "GPHOTO_DOWNLOAD_UNSUPPORTED",
        )
        return StorageWriteResult(file = null, sidecar = null, success = false, error = if (controller == null) error else error)
    }
}

private class LibGPhotoTetherController(
    private val nativeHandle: Long,
    private val events: MutableSharedFlow<CameraEvent>,
) : TetherCaptureController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incomingObjects = MutableSharedFlow<TetherIncomingObject>(extraBufferCapacity = 64)
    private val queueState = MutableStateFlow(TetherDownloadQueueState(sessionId = "idle", running = false, items = emptyList()))
    private var activeSession: TetherSession? = null
    private var watchJob: Job? = null

    override suspend fun startWatching(projectId: String, policy: TetherImportPolicy): TetherSession {
        val session = TetherSession(projectId = projectId, policy = policy)
        activeSession = session
        queueState.value = TetherDownloadQueueState(sessionId = session.sessionId, running = true, items = emptyList())
        events.tryEmit(CameraEvent(type = CameraEventType.TetherStarted, message = "libgphoto tether watch started"))
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                val event = LibGPhotoNative.waitForEvent(nativeHandle, 1000)
                event.error?.let { error ->
                    events.tryEmit(CameraEvent(type = CameraEventType.Error, message = error.userMessageZh))
                    delay(1000)
                    return@let
                }
                val eventJson = event.value
                if (eventJson?.contains("\"ObjectAdded\"") == true) {
                    events.tryEmit(CameraEvent(type = CameraEventType.Debug, message = "libgphoto ObjectAdded: $eventJson"))
                }
                delay(250)
            }
        }
        return session
    }

    override suspend fun stopWatching(sessionId: String) {
        if (activeSession?.sessionId != sessionId) return
        watchJob?.cancel()
        watchJob = null
        activeSession = null
        queueState.value = TetherDownloadQueueState(sessionId = sessionId, running = false, items = emptyList())
        events.tryEmit(CameraEvent(type = CameraEventType.TetherStopped, message = "libgphoto tether watch stopped"))
    }

    override fun observeIncomingObjects(sessionId: String): Flow<TetherIncomingObject> = incomingObjects.asSharedFlow()

    override fun observeDownloadQueue(sessionId: String): Flow<TetherDownloadQueueState> = queueState.asStateFlow()

    fun enqueueUnsupported(cameraObject: CameraObject, error: CameraError) {
        val session = activeSession ?: return
        queueState.value = TetherDownloadQueueState(
            sessionId = session.sessionId,
            running = true,
            items = listOf(
                TetherDownloadItem(
                    cameraObject = cameraObject,
                    status = TetherDownloadStatus.Failed,
                    attempts = 1,
                    error = error,
                ),
            ),
        )
    }

    fun close() {
        watchJob?.cancel()
        activeSession = null
    }
}
