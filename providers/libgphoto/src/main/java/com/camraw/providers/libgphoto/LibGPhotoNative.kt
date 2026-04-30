package com.camraw.providers.libgphoto

import com.camraw.core.camera.api.CameraError

data class NativeResult<out T>(
    val value: T? = null,
    val error: CameraError? = null,
) {
    val isSuccess: Boolean get() = error == null

    companion object {
        fun <T> success(value: T): NativeResult<T> = NativeResult(value = value)
        fun <T> failure(error: CameraError): NativeResult<T> = NativeResult(error = error)
    }
}

object LibGPhotoNative {
    private val loadError: Throwable? = runCatching {
        System.loadLibrary("camraw_gphoto_bridge")
    }.exceptionOrNull()

    fun configureRuntime(nativeLibraryDir: String?) {
        loadError ?: runCatching { nativeConfigureRuntime(nativeLibraryDir.orEmpty()) }
    }

    fun backendVersion(): String {
        val error = loadError
        if (error != null) return "native-load-failed:${error::class.java.simpleName}:${error.message}"
        return runCatching { nativeBackendVersion() }.getOrElse { throwable ->
            "native-call-failed:${throwable::class.java.simpleName}:${throwable.message}"
        }
    }

    fun openFromFd(fd: Int, vendorId: Int, productId: Int): NativeResult<Long> {
        loadError?.let { return NativeResult.failure(LibGPhotoErrorMapper.loadFailure(it)) }
        val handle = runCatching { nativeOpenFromFd(fd, vendorId, productId) }
            .getOrElse { return NativeResult.failure(LibGPhotoErrorMapper.nativeFailure("openFromFd", it)) }
        if (handle <= 0L) {
            val errorJson = runCatching { nativeLastOpenErrorJson() }.getOrNull()
            if (!errorJson.isNullOrBlank() && errorJson.contains("\"error\"")) {
                return NativeResult.failure(LibGPhotoErrorMapper.jsonFailure("openFromFd", errorJson))
            }
            return NativeResult.failure(LibGPhotoErrorMapper.codeFailure("openFromFd", -1))
        }
        return NativeResult.success(handle)
    }

    fun close(handle: Long): CameraError? {
        loadError?.let { return LibGPhotoErrorMapper.loadFailure(it) }
        val code = runCatching { nativeClose(handle) }
            .getOrElse { return LibGPhotoErrorMapper.nativeFailure("close", it) }
        return if (code == 0) null else LibGPhotoErrorMapper.codeFailure("close", code)
    }

    fun getDeviceInfo(handle: Long): NativeResult<String> = callJson("getDeviceInfo") {
        nativeGetDeviceInfoJson(handle)
    }

    fun getCapabilities(handle: Long): NativeResult<String> = callJson("getCapabilities") {
        nativeGetCapabilitiesJson(handle)
    }

    fun getConfigJson(handle: Long): NativeResult<String> = callJson("getConfigJson") {
        nativeGetConfigJson(handle)
    }

    fun setConfigValue(handle: Long, key: String, value: String): CameraError? {
        loadError?.let { return LibGPhotoErrorMapper.loadFailure(it) }
        val code = runCatching { nativeSetConfigValue(handle, key, value) }
            .getOrElse { return LibGPhotoErrorMapper.nativeFailure("setConfigValue", it) }
        return if (code == 0) null else LibGPhotoErrorMapper.codeFailure("setConfigValue", code)
    }

    fun capture(handle: Long): NativeResult<String> = callJson("capture") {
        nativeCaptureJson(handle)
    }

    fun waitForEvent(handle: Long, timeoutMs: Int): NativeResult<String> = callJson("waitForEvent") {
        nativeWaitForEventJson(handle, timeoutMs)
    }

    fun listFiles(handle: Long, folder: String): NativeResult<String> = callJson("listFiles") {
        nativeListFilesJson(handle, folder)
    }

    fun downloadFile(handle: Long, folder: String, filename: String, targetPath: String): CameraError? {
        loadError?.let { return LibGPhotoErrorMapper.loadFailure(it) }
        val code = runCatching { nativeDownloadFile(handle, folder, filename, targetPath) }
            .getOrElse { return LibGPhotoErrorMapper.nativeFailure("downloadFile", it) }
        return if (code == 0) null else LibGPhotoErrorMapper.codeFailure("downloadFile", code)
    }

    fun cancelOperation(handle: Long): CameraError? {
        loadError?.let { return LibGPhotoErrorMapper.loadFailure(it) }
        val code = runCatching { nativeCancelOperation(handle) }
            .getOrElse { return LibGPhotoErrorMapper.nativeFailure("cancelOperation", it) }
        return if (code == 0) null else LibGPhotoErrorMapper.codeFailure("cancelOperation", code)
    }

    private fun callJson(operation: String, block: () -> String): NativeResult<String> {
        loadError?.let { return NativeResult.failure(LibGPhotoErrorMapper.loadFailure(it)) }
        return runCatching {
            val json = block()
            if (json.contains("\"error\"")) {
                NativeResult.failure(LibGPhotoErrorMapper.jsonFailure(operation, json))
            } else {
                NativeResult.success(json)
            }
        }.getOrElse { throwable ->
            NativeResult.failure(LibGPhotoErrorMapper.nativeFailure(operation, throwable))
        }
    }

    private external fun nativeConfigureRuntime(nativeLibraryDir: String)
    private external fun nativeBackendVersion(): String
    private external fun nativeLastOpenErrorJson(): String
    private external fun nativeOpenFromFd(fd: Int, vendorId: Int, productId: Int): Long
    private external fun nativeClose(handle: Long): Int
    private external fun nativeGetDeviceInfoJson(handle: Long): String
    private external fun nativeGetCapabilitiesJson(handle: Long): String
    private external fun nativeGetConfigJson(handle: Long): String
    private external fun nativeSetConfigValue(handle: Long, key: String, value: String): Int
    private external fun nativeCaptureJson(handle: Long): String
    private external fun nativeWaitForEventJson(handle: Long, timeoutMs: Int): String
    private external fun nativeListFilesJson(handle: Long, folder: String): String
    private external fun nativeDownloadFile(handle: Long, folder: String, filename: String, targetPath: String): Int
    private external fun nativeCancelOperation(handle: Long): Int
}
