package com.camraw.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.camraw.core.camera.api.CameraCaptureRequest
import com.camraw.core.camera.api.CameraConnectionType
import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CameraError
import com.camraw.core.camera.api.CameraErrorType
import com.camraw.core.camera.api.CameraSession
import com.camraw.core.camera.api.CaptureFormat
import com.camraw.core.camera.api.CaptureResult
import com.camraw.core.camera.runtime.CameraProviderRegistry
import com.camraw.core.logging.CamrawLog
import com.camraw.core.logging.LogCategory
import com.camraw.core.storage.DefaultCameraStorageController
import com.camraw.providers.fake.FakeCameraProvider
import com.camraw.providers.internalcamera.AndroidInternalCameraProvider
import com.camraw.providers.libgphoto.LibGPhotoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraAppController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storageController = DefaultCameraStorageController(appContext)
    private val libGPhotoProvider = LibGPhotoProvider(appContext, storageController)
    private val registry = CameraProviderRegistry(
        providers = listOf(
            AndroidInternalCameraProvider(appContext, storageController),
            libGPhotoProvider,
            FakeCameraProvider(storageController),
        ),
    )

    private val _busy = MutableStateFlow(false)
    private val _status = MutableStateFlow("Ready")
    private val _lastError = MutableStateFlow<CameraError?>(null)
    private val _lastCapture = MutableStateFlow<CaptureResult?>(null)

    val devices = registry.devices
    val activeSession: StateFlow<CameraSession?> = registry.activeSession
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val status: StateFlow<String> = _status.asStateFlow()
    val lastError: StateFlow<CameraError?> = _lastError.asStateFlow()
    val lastCapture: StateFlow<CaptureResult?> = _lastCapture.asStateFlow()

    init {
        scope.launch {
            registry.errors.collect { error ->
                _lastError.value = error
                _status.value = error.userMessageZh
            }
        }
    }

    suspend fun refreshDevices() {
        _busy.value = true
        _status.value = "Scanning"
        runCatching { registry.refreshDevices() }
            .onSuccess { _status.value = "Found ${it.size} devices" }
            .onFailure { throwable ->
                _lastError.value = appError("设备扫描失败", throwable)
                _status.value = "Scan failed"
            }
        _busy.value = false
    }

    suspend fun connectDefault(hasCameraPermission: Boolean) {
        val allDevices = devices.value.ifEmpty {
            runCatching { registry.refreshDevices() }.getOrDefault(emptyList())
        }
        val internal = allDevices
            .filter { it.connectionType == CameraConnectionType.Internal }
            .minByOrNull { device ->
                when (device.debugInfo["lensFacing"]) {
                    "back" -> 0
                    "external" -> 1
                    "front" -> 2
                    else -> 3
                }
            }
        val current = activeSession.value

        if (internal != null && !hasCameraPermission) {
            if (current?.deviceInfo?.connectionType == CameraConnectionType.Internal) {
                registry.closeActiveSession()
            }
            if (current == null || current.deviceInfo.connectionType == CameraConnectionType.Internal) {
                _status.value = "需要相机权限"
                _lastError.value = CameraError(
                    type = CameraErrorType.PermissionError,
                    userMessageZh = "需要相机权限才能调用手机原生摄像头",
                    debugMessage = "Internal camera present, CAMERA permission not granted",
                    fallbackSuggestionZh = "请授予相机权限后重新连接内置相机。",
                )
            }
            return
        }

        val preferred = internal?.takeIf { hasCameraPermission || !it.requiresPermission }
            ?: allDevices.firstOrNull { it.connectionType == CameraConnectionType.Virtual }
        if (preferred == null) return
        if (current?.deviceInfo?.providerId == preferred.providerId && current.deviceInfo.id == preferred.id) return
        connect(preferred)
    }

    fun connectAsync(device: CameraDeviceInfo) {
        scope.launch { connect(device) }
    }

    suspend fun connect(device: CameraDeviceInfo) {
        if (device.connectionType == CameraConnectionType.Internal && !hasCameraPermission()) {
            val error = CameraError(
                type = CameraErrorType.PermissionError,
                userMessageZh = "需要相机权限才能调用手机原生摄像头",
                debugMessage = "Attempted to connect internal camera without CAMERA permission",
                fallbackSuggestionZh = "请授予相机权限后重试。",
            )
            _lastError.value = error
            _status.value = error.userMessageZh
            return
        }
        if (device.connectionType == CameraConnectionType.UsbGPhoto && device.requiresPermission) {
            val requested = libGPhotoProvider.requestPermission(device)
            val error = CameraError(
                type = CameraErrorType.PermissionError,
                userMessageZh = if (requested) "已请求 USB 权限，请允许后重新点击设备" else "无法请求 USB 权限",
                debugMessage = "UsbGPhoto requiresPermission=${device.requiresPermission} requested=$requested device=${device.debugInfo}",
                fallbackSuggestionZh = "授权弹窗出现后请选择允许；如果没有弹窗，请重新插拔 USB 相机后刷新设备列表。",
                causeCode = if (requested) "USB_PERMISSION_REQUESTED" else "USB_PERMISSION_REQUEST_FAILED",
            )
            _lastError.value = error
            _status.value = error.userMessageZh
            return
        }
        _busy.value = true
        _status.value = "Connecting ${device.displayName}"
        runCatching { registry.connect(device) }
            .onSuccess {
                _status.value = "Connected ${device.displayName}"
                _lastError.value = null
            }
            .onFailure { throwable ->
                val error = appError("${device.displayName} 连接失败", throwable)
                _lastError.value = error
                _status.value = error.userMessageZh
                CamrawLog.error(LogCategory.App, "Connect failed", throwable)
            }
        _busy.value = false
    }

    fun captureAsync(format: CaptureFormat = CaptureFormat.Jpeg) {
        scope.launch { capture(format) }
    }

    suspend fun capture(format: CaptureFormat = CaptureFormat.Jpeg) {
        val session = activeSession.value ?: return
        val capture = session.capture ?: return
        _busy.value = true
        _status.value = "Capturing"
        val result = capture.capture(CameraCaptureRequest(format = format))
        val error = result.error
        _lastCapture.value = result
        val primaryFileCount = result.files.count { it.kind.name != "Sidecar" }
        val effectiveError = error ?: if (primaryFileCount == 0) {
            CameraError(
                type = CameraErrorType.StorageFailed,
                userMessageZh = "拍摄完成但没有文件保存到相册",
                debugMessage = "Capture returned success without any non-sidecar StoredCameraFile. format=$format provider=${session.deviceInfo.providerId} device=${session.deviceInfo.id}",
                fallbackSuggestionZh = "请切换为 JPEG 重试；如果仍失败，请把设备页底部诊断信息发给我。",
                causeCode = "CAPTURE_RETURNED_NO_GALLERY_FILE",
            )
        } else {
            null
        }
        _lastError.value = effectiveError
        _status.value = if (effectiveError == null) {
            "Saved $primaryFileCount file(s)"
        } else {
            effectiveError.userMessageZh
        }
        _busy.value = false
    }

    fun closeSessionAsync() {
        scope.launch {
            registry.closeActiveSession()
            _status.value = "Disconnected"
        }
    }

    fun reportRuntimeError(error: CameraError) {
        _lastError.value = error
        _status.value = error.userMessageZh
    }

    private fun appError(messageZh: String, throwable: Throwable): CameraError {
        return CameraError(
            type = CameraErrorType.UnknownError,
            userMessageZh = messageZh,
            debugMessage = throwable.stackTraceToString(),
            fallbackSuggestionZh = "请稍后重试或打开 Debug 查看详情。",
        )
    }

    private fun hasCameraPermission(): Boolean {
        return appContext.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}
