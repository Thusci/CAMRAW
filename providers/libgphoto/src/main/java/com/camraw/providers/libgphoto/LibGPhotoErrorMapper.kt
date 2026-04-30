package com.camraw.providers.libgphoto

import com.camraw.core.camera.api.CameraError
import com.camraw.core.camera.api.CameraErrorType

object LibGPhotoErrorMapper {
    fun loadFailure(throwable: Throwable): CameraError {
        return CameraError(
            type = CameraErrorType.ConnectionFailed,
            userMessageZh = "libgphoto native bridge 未加载",
            debugMessage = throwable.stackTraceToString(),
            fallbackSuggestionZh = "请确认 APK 包含 arm64-v8a 的 libcamraw_gphoto_bridge.so；后续接入 libusb/libgphoto2 源码后再重试 USB 相机。",
            causeCode = "GPHOTO_NATIVE_LOAD_FAILED",
        )
    }

    fun nativeFailure(operation: String, throwable: Throwable): CameraError {
        return CameraError(
            type = CameraErrorType.ConnectionFailed,
            userMessageZh = "libgphoto 调用失败",
            debugMessage = "operation=$operation\n${throwable.stackTraceToString()}",
            fallbackSuggestionZh = "请断开 USB 后重新授权连接，并把 Debug 中的 native 信息反馈给我。",
            causeCode = "GPHOTO_NATIVE_CALL_FAILED",
        )
    }

    fun codeFailure(operation: String, code: Int): CameraError {
        return CameraError(
            type = when (code) {
                -95 -> CameraErrorType.CapabilityUnsupported
                else -> CameraErrorType.ConnectionFailed
            },
            userMessageZh = when (code) {
                -95 -> "当前 libgphoto 后端暂不支持该操作"
                else -> "libgphoto 操作失败"
            },
            debugMessage = "operation=$operation nativeCode=$code",
            fallbackSuggestionZh = "如果这是 USB 相机，请确认已授予 USB 权限；如果 nativeBackend 显示 libgphoto2 unsupported，需要继续完成 libgphoto2/camlibs Android 移植。",
            causeCode = if (code == -95) "NATIVE_BACKEND_UNSUPPORTED" else "GPHOTO_NATIVE_CODE_$code",
        )
    }

    fun jsonFailure(operation: String, json: String): CameraError {
        val code = json.stringValue("error") ?: "GPHOTO_NATIVE_JSON_ERROR"
        val backend = json.stringValue("backend") ?: "unknown"
        val message = json.stringValue("message") ?: json
        val userMessage = when (code) {
            "NATIVE_BACKEND_UNSUPPORTED" -> "当前 native 后端尚未完成 libgphoto2 真实操作"
            "NATIVE_BACKEND_INIT_FAILED" -> "native USB/libgphoto 初始化失败"
            "NATIVE_HANDLE_INVALID" -> "native 相机会话已失效"
            else -> "libgphoto native 返回错误"
        }
        return CameraError(
            type = when (code) {
                "NATIVE_BACKEND_UNSUPPORTED" -> CameraErrorType.CapabilityUnsupported
                "NATIVE_HANDLE_INVALID" -> CameraErrorType.DeviceDisconnected
                else -> CameraErrorType.ConnectionFailed
            },
            userMessageZh = userMessage,
            debugMessage = "operation=$operation backend=$backend\n$json",
            fallbackSuggestionZh = message,
            causeCode = code,
            recoverable = code != "NATIVE_HANDLE_INVALID",
        )
    }

    fun permissionRequired(deviceName: String): CameraError {
        return CameraError(
            type = CameraErrorType.PermissionError,
            userMessageZh = "需要 USB 权限才能连接外接相机",
            debugMessage = "USB permission missing for $deviceName",
            fallbackSuggestionZh = "请在系统弹窗中允许 CAMRAW 访问 USB 设备，然后重新点击该设备。",
            causeCode = "USB_PERMISSION_REQUIRED",
        )
    }
}

private fun String.stringValue(key: String): String? {
    val marker = "\"$key\":\""
    val start = indexOf(marker)
    if (start < 0) return null
    val valueStart = start + marker.length
    val valueEnd = indexOf('"', valueStart)
    return if (valueEnd > valueStart) substring(valueStart, valueEnd) else null
}

class LibGPhotoException(
    val cameraError: CameraError,
) : RuntimeException(cameraError.debugMessage)
