package com.camraw.core.camera.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream
import java.time.Instant
import java.util.UUID

interface CameraDeviceProvider {
    val providerId: String
    val providerName: String
    val priority: Int

    suspend fun discoverDevices(): List<CameraDeviceInfo>
    suspend fun connect(device: CameraDeviceInfo): CameraSession
    suspend fun disconnect(deviceId: String)
    fun observeDevices(): Flow<List<CameraDeviceInfo>>
    fun observeConnectionState(deviceId: String): Flow<ConnectionState>
}

interface CameraSession {
    val sessionId: String
    val deviceInfo: CameraDeviceInfo
    val capabilities: StateFlow<CameraCapabilities>
    val events: Flow<CameraEvent>

    val preview: PreviewController?
    val capture: CaptureController?
    val settings: CameraSettingsController?
    val focus: FocusController?
    val storage: CameraStorageController?
    val metadata: MetadataController?

    suspend fun refreshCapabilities(): CameraCapabilities
    suspend fun close()
}

data class CameraDeviceInfo(
    val id: String,
    val displayName: String,
    val providerId: String,
    val providerName: String,
    val connectionType: CameraConnectionType,
    val model: String? = null,
    val manufacturer: String? = null,
    val requiresPermission: Boolean = false,
    val setupHint: String? = null,
    val capabilitySummary: List<String> = emptyList(),
    val debugInfo: Map<String, String> = emptyMap(),
)

enum class CameraConnectionType {
    Internal,
    UsbPtp,
    UsbUvc,
    Virtual,
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Discovering : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val sessionId: String) : ConnectionState
    data object Previewing : ConnectionState
    data object Capturing : ConnectionState
    data object Downloading : ConnectionState
    data object Disconnecting : ConnectionState
    data class Error(val error: CameraError) : ConnectionState
}

data class CameraCapabilities(
    val providerId: String,
    val deviceId: String,
    val capabilities: Map<CameraCapability, CapabilityInfo>,
    val settings: List<CameraSettingDescriptor>,
    val rawDebugInfo: Map<String, Any?> = emptyMap(),
) {
    fun isSupported(capability: CameraCapability): Boolean {
        return capabilities[capability]?.state == CapabilityState.Available
    }

    fun toDebugJson(): String {
        return buildString {
            appendLine("{")
            appendLine("  \"providerId\": \"${providerId.escapeJson()}\",")
            appendLine("  \"deviceId\": \"${deviceId.escapeJson()}\",")
            appendLine("  \"capabilities\": {")
            capabilities.entries.forEachIndexed { index, entry ->
                append("    \"${entry.key.name}\": ${entry.value.toDebugJson()}")
                appendLine(if (index == capabilities.size - 1) "" else ",")
            }
            appendLine("  },")
            appendLine("  \"settings\": [")
            settings.forEachIndexed { index, setting ->
                append("    ${setting.toDebugJson()}")
                appendLine(if (index == settings.size - 1) "" else ",")
            }
            appendLine("  ],")
            appendLine("  \"rawDebugInfo\": ${rawDebugInfo.toJsonObject()}")
            append("}")
        }
    }
}

enum class CameraCapability {
    Preview,
    PreviewFrameAnalysis,
    CaptureJpeg,
    CaptureHeic,
    CaptureRaw,
    CaptureRawJpeg,
    ManualIso,
    ManualShutter,
    ExposureCompensation,
    WhiteBalance,
    ColorTemperature,
    ManualFocus,
    TouchFocus,
    FocusLock,
    ExposureLock,
    Zebra,
    FocusPeaking,
    Histogram,
    Grid,
    MediaStoreSave,
    SidecarMetadata,
    DebugDump,
}

data class CapabilityInfo(
    val state: CapabilityState,
    val displayName: String,
    val userReadableReason: String? = null,
    val providerMetadata: Map<String, String> = emptyMap(),
) {
    fun toDebugJson(): String {
        return mapOf(
            "state" to state.name,
            "displayName" to displayName,
            "userReadableReason" to userReadableReason,
            "providerMetadata" to providerMetadata,
        ).toJsonObject()
    }
}

enum class CapabilityState {
    Available,
    Unavailable,
    PermissionRequired,
    NeedsDeviceSetup,
    Degraded,
    Unknown,
}

data class CameraSettingDescriptor(
    val id: String,
    val displayName: String,
    val category: SettingCategory,
    val valueType: SettingValueType,
    val currentValue: SettingValue?,
    val availableValues: List<SettingValue>,
    val writable: Boolean,
    val state: CapabilityState,
    val userReadableReason: String? = null,
    val providerMetadata: Map<String, String> = emptyMap(),
) {
    fun toDebugJson(): String {
        return mapOf(
            "id" to id,
            "displayName" to displayName,
            "category" to category.name,
            "valueType" to valueType.name,
            "currentValue" to currentValue?.debugValue,
            "availableValues" to availableValues.map { it.debugValue },
            "writable" to writable,
            "state" to state.name,
            "userReadableReason" to userReadableReason,
            "providerMetadata" to providerMetadata,
        ).toJsonObject()
    }
}

enum class SettingCategory {
    Exposure,
    Focus,
    WhiteBalance,
    ImageQuality,
    HdrDro,
    Storage,
    Debug,
}

enum class SettingValueType {
    Boolean,
    Integer,
    Decimal,
    Text,
    Choice,
}

sealed interface SettingValue {
    val label: String
    val debugValue: String

    data class Bool(val value: Boolean, override val label: String = value.toString()) : SettingValue {
        override val debugValue: String = value.toString()
    }

    data class IntValue(val value: Int, override val label: String = value.toString()) : SettingValue {
        override val debugValue: String = value.toString()
    }

    data class DecimalValue(val value: Double, override val label: String = value.toString()) : SettingValue {
        override val debugValue: String = value.toString()
    }

    data class Text(val value: String, override val label: String = value) : SettingValue {
        override val debugValue: String = value
    }

    data class Choice(val id: String, override val label: String) : SettingValue {
        override val debugValue: String = id
    }
}

interface PreviewController {
    val previewState: StateFlow<PreviewState>
    val frames: Flow<PreviewFrame>

    suspend fun bind(surface: PreviewSurface)
    suspend fun unbind()
}

data class PreviewSurface(
    val nativeSurface: Any,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)

data class PreviewState(
    val running: Boolean = false,
    val fps: Double = 0.0,
    val latencyMs: Long? = null,
    val droppedFrames: Long = 0L,
    val error: CameraError? = null,
)

data class PreviewFrame(
    val id: Long,
    val timestamp: Instant,
    val width: Int,
    val height: Int,
    val luminance: Float,
    val sampleRgba: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewFrame) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

interface CaptureController {
    val captureState: StateFlow<CaptureState>
    suspend fun capture(request: CameraCaptureRequest = CameraCaptureRequest()): CaptureResult
}

data class CameraCaptureRequest(
    val format: CaptureFormat = CaptureFormat.Jpeg,
    val metadata: Map<String, String> = emptyMap(),
)

enum class CaptureFormat {
    Jpeg,
    Heic,
    Raw,
    RawAndJpeg,
    PreviewJpeg,
}

sealed interface CaptureState {
    data object Idle : CaptureState
    data object Focusing : CaptureState
    data object Capturing : CaptureState
    data object Writing : CaptureState
    data class Completed(val result: CaptureResult) : CaptureState
    data class Failed(val error: CameraError) : CaptureState
}

data class CaptureResult(
    val jobId: String = UUID.randomUUID().toString(),
    val format: CaptureFormat,
    val files: List<StoredCameraFile>,
    val metadata: Map<String, String> = emptyMap(),
    val error: CameraError? = null,
)

data class StoredCameraFile(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val kind: CameraObjectKind,
    val bytes: Long? = null,
    val checksumSha256: String? = null,
)

interface CameraSettingsController {
    val settings: StateFlow<List<CameraSettingDescriptor>>
    suspend fun refreshSettings(): List<CameraSettingDescriptor>
    suspend fun writeSetting(settingId: String, value: SettingValue): SettingWriteResult
}

data class SettingWriteResult(
    val settingId: String,
    val requested: SettingValue,
    val confirmed: SettingValue?,
    val success: Boolean,
    val error: CameraError? = null,
)

interface FocusController {
    val focusState: StateFlow<FocusState>
    suspend fun focusAt(point: FocusPoint): FocusResult
    suspend fun lockFocus(): FocusResult
    suspend fun unlockFocus(): FocusResult
}

data class FocusPoint(
    val normalizedX: Float,
    val normalizedY: Float,
)

data class FocusState(
    val mode: String = "auto",
    val point: FocusPoint? = null,
    val locked: Boolean = false,
    val level: FocusCapabilityLevel = FocusCapabilityLevel.None,
    val status: FocusStatus = FocusStatus.Idle,
)

enum class FocusCapabilityLevel {
    None,
    DisplayOnly,
    CenterAf,
    AfRegion,
    AfPoint,
}

enum class FocusStatus {
    Idle,
    Running,
    Locked,
    Degraded,
    Failed,
}

data class FocusResult(
    val success: Boolean,
    val state: FocusState,
    val error: CameraError? = null,
)

interface CameraStorageController {
    suspend fun write(request: StorageWriteRequest, writer: suspend (OutputStream) -> Unit): StorageWriteResult
}

data class StorageWriteRequest(
    val providerId: String,
    val providerName: String,
    val deviceId: String,
    val deviceName: String,
    val kind: CameraObjectKind,
    val extension: String,
    val mimeType: String,
    val capturedAt: Instant = Instant.now(),
    val originalFileName: String? = null,
    val objectHandle: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class StorageWriteResult(
    val file: StoredCameraFile?,
    val sidecar: StoredCameraFile?,
    val success: Boolean,
    val error: CameraError? = null,
)

fun interface MetadataController {
    suspend fun currentMetadata(): Map<String, String>
}

data class CameraObject(
    val objectId: String,
    val fileName: String,
    val kind: CameraObjectKind,
    val sizeBytes: Long?,
    val capturedAt: Instant?,
    val providerMetadata: Map<String, String> = emptyMap(),
)

enum class CameraObjectKind {
    Raw,
    Jpeg,
    Heic,
    Preview,
    Video,
    Sidecar,
}

data class CameraEvent(
    val timestamp: Instant = Instant.now(),
    val type: CameraEventType,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
)

enum class CameraEventType {
    DeviceDiscovered,
    Connected,
    Disconnected,
    CapabilityChanged,
    PreviewStarted,
    PreviewStopped,
    CaptureStarted,
    CaptureCompleted,
    Error,
    Debug,
}

data class CameraError(
    val type: CameraErrorType,
    val userMessageZh: String,
    val debugMessage: String,
    val fallbackSuggestionZh: String? = null,
    val causeCode: String? = null,
    val recoverable: Boolean = true,
)

enum class CameraErrorType {
    PermissionError,
    DeviceNotFound,
    DeviceDisconnected,
    ConnectionFailed,
    CapabilityUnsupported,
    SettingReadFailed,
    SettingWriteFailed,
    PreviewFailed,
    CaptureFailed,
    DownloadFailed,
    StorageFailed,
    NativeCrashRisk,
    UnknownError,
}

fun unsupportedError(feature: String): CameraError {
    return CameraError(
        type = CameraErrorType.CapabilityUnsupported,
        userMessageZh = "$feature 当前设备不支持",
        debugMessage = "Unsupported feature: $feature",
        fallbackSuggestionZh = "请切换支持该能力的设备或关闭该控制项。",
    )
}

internal fun String.escapeJson(): String {
    return buildString {
        this@escapeJson.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

internal fun Map<String, Any?>.toJsonObject(): String {
    return entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"${key.escapeJson()}\": ${value.toJsonValue()}"
    }
}

private fun Any?.toJsonValue(): String {
    return when (this) {
        null -> "null"
        is Boolean -> toString()
        is Number -> toString()
        is String -> "\"${escapeJson()}\""
        is Map<*, *> -> entries.joinToString(prefix = "{", postfix = "}") { entry ->
            "\"${entry.key.toString().escapeJson()}\": ${entry.value.toJsonValue()}"
        }
        is Iterable<*> -> joinToString(prefix = "[", postfix = "]") { it.toJsonValue() }
        else -> "\"${toString().escapeJson()}\""
    }
}
