package com.camraw.app

import android.content.Context
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
import com.camraw.providers.fake.FakeCameraProvider
import com.camraw.providers.internalcamera.AndroidInternalCameraProvider
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
    private val registry = CameraProviderRegistry(
        providers = listOf(
            AndroidInternalCameraProvider(appContext),
            FakeCameraProvider(),
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
        if (activeSession.value != null) return
        val allDevices = devices.value.ifEmpty {
            runCatching { registry.refreshDevices() }.getOrDefault(emptyList())
        }
        val preferred = allDevices.firstOrNull {
            it.connectionType == CameraConnectionType.Internal && (hasCameraPermission || !it.requiresPermission)
        } ?: allDevices.firstOrNull { it.connectionType == CameraConnectionType.Virtual }
        preferred?.let { connect(it) }
    }

    fun connectAsync(device: CameraDeviceInfo) {
        scope.launch { connect(device) }
    }

    suspend fun connect(device: CameraDeviceInfo) {
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
        _lastError.value = error
        _status.value = if (error == null) {
            "Saved ${result.files.count { it.kind.name != "Sidecar" }} file(s)"
        } else {
            error.userMessageZh
        }
        _busy.value = false
    }

    fun closeSessionAsync() {
        scope.launch {
            registry.closeActiveSession()
            _status.value = "Disconnected"
        }
    }

    private fun appError(messageZh: String, throwable: Throwable): CameraError {
        return CameraError(
            type = CameraErrorType.UnknownError,
            userMessageZh = messageZh,
            debugMessage = throwable.stackTraceToString(),
            fallbackSuggestionZh = "请稍后重试或打开 Debug 查看详情。",
        )
    }
}
