package com.camraw.core.camera.runtime

import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CameraDeviceProvider
import com.camraw.core.camera.api.CameraError
import com.camraw.core.camera.api.CameraErrorType
import com.camraw.core.camera.api.CameraEvent
import com.camraw.core.camera.api.CameraEventType
import com.camraw.core.camera.api.CameraSession
import com.camraw.core.camera.api.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CameraProviderRegistry(
    providers: List<CameraDeviceProvider> = emptyList(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val providerMutex = Mutex()
    private val providerMap = providers.associateBy { it.providerId }.toMutableMap()
    private val _devices = MutableStateFlow<List<CameraDeviceInfo>>(emptyList())
    private val _activeSession = MutableStateFlow<CameraSession?>(null)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _events = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 64)
    private val _errors = MutableSharedFlow<CameraError>(extraBufferCapacity = 32)

    val devices: StateFlow<List<CameraDeviceInfo>> = _devices.asStateFlow()
    val activeSession: StateFlow<CameraSession?> = _activeSession.asStateFlow()
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    val events: Flow<CameraEvent> = _events.asSharedFlow()
    val errors: Flow<CameraError> = _errors.asSharedFlow()

    suspend fun register(provider: CameraDeviceProvider) {
        providerMutex.withLock {
            providerMap[provider.providerId] = provider
        }
    }

    suspend fun providers(): List<CameraDeviceProvider> {
        return providerMutex.withLock {
            providerMap.values.sortedByDescending { it.priority }
        }
    }

    suspend fun refreshDevices(): List<CameraDeviceInfo> {
        _connectionState.value = ConnectionState.Discovering
        val discovered = providers()
            .map { provider ->
                scope.async {
                    runCatching { provider.discoverDevices() }
                        .onFailure { throwable ->
                            val error = CameraError(
                                type = CameraErrorType.ConnectionFailed,
                                userMessageZh = "${provider.providerName} 发现设备失败",
                                debugMessage = throwable.stackTraceToString(),
                                fallbackSuggestionZh = "请检查权限、设备连接或稍后重试。",
                            )
                            _errors.tryEmit(error)
                            _events.tryEmit(
                                CameraEvent(
                                    type = CameraEventType.Error,
                                    message = error.userMessageZh,
                                    metadata = mapOf("providerId" to provider.providerId),
                                ),
                            )
                        }
                        .getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .sortedWith(
                compareByDescending<CameraDeviceInfo> { providerMap[it.providerId]?.priority ?: 0 }
                    .thenBy { it.displayName },
            )

        _devices.value = discovered
        _connectionState.value = ConnectionState.Disconnected
        discovered.forEach { device ->
            _events.tryEmit(
                CameraEvent(
                    type = CameraEventType.DeviceDiscovered,
                    message = "Discovered ${device.displayName}",
                    metadata = mapOf("providerId" to device.providerId, "deviceId" to device.id),
                ),
            )
        }
        return discovered
    }

    suspend fun connect(device: CameraDeviceInfo): CameraSession {
        val provider = providers().firstOrNull { it.providerId == device.providerId }
            ?: throw IllegalArgumentException("Provider not registered: ${device.providerId}")

        _connectionState.value = ConnectionState.Connecting
        closeActiveSession()
        return runCatching { provider.connect(device) }
            .onSuccess { session ->
                _activeSession.value = session
                _connectionState.value = ConnectionState.Connected(session.sessionId)
                _events.tryEmit(
                    CameraEvent(
                        type = CameraEventType.Connected,
                        message = "Connected ${device.displayName}",
                        metadata = mapOf("providerId" to device.providerId, "deviceId" to device.id),
                    ),
                )
                scope.launch {
                    session.events.collect { event -> _events.emit(event) }
                }
            }
            .onFailure { throwable ->
                val error = CameraError(
                    type = CameraErrorType.ConnectionFailed,
                    userMessageZh = "${device.displayName} 连接失败",
                    debugMessage = throwable.stackTraceToString(),
                    fallbackSuggestionZh = "请检查权限和设备状态后重试。",
                )
                _connectionState.value = ConnectionState.Error(error)
                _errors.tryEmit(error)
            }
            .getOrThrow()
    }

    suspend fun connectBestAvailable(): CameraSession {
        val allDevices = if (devices.value.isEmpty()) refreshDevices() else devices.value
        val best = allDevices.firstOrNull()
            ?: throw IllegalStateException("No camera devices were discovered")
        return connect(best)
    }

    suspend fun closeActiveSession() {
        val session = _activeSession.value ?: return
        _connectionState.value = ConnectionState.Disconnecting
        runCatching { session.close() }
        _activeSession.value = null
        _connectionState.value = ConnectionState.Disconnected
        _events.tryEmit(
            CameraEvent(
                type = CameraEventType.Disconnected,
                message = "Session closed",
                metadata = mapOf("sessionId" to session.sessionId),
            ),
        )
    }
}
