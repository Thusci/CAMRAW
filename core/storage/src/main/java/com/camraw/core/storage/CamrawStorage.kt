package com.camraw.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.camraw.core.camera.api.CameraError
import com.camraw.core.camera.api.CameraErrorType
import com.camraw.core.camera.api.CameraObjectKind
import com.camraw.core.camera.api.CameraStorageController
import com.camraw.core.camera.api.StorageWriteRequest
import com.camraw.core.camera.api.StorageWriteResult
import com.camraw.core.camera.api.StoredCameraFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class DefaultCameraStorageController(
    private val context: Context,
    private val rootDirectoryName: String = "CAMRAW",
) : CameraStorageController {
    private val nameGenerator = CameraFileNameGenerator()

    override suspend fun write(
        request: StorageWriteRequest,
        writer: suspend (OutputStream) -> Unit,
    ): StorageWriteResult = withContext(Dispatchers.IO) {
        val displayName = nameGenerator.generate(request)
        val relativePath = request.relativePath(rootDirectoryName)
        val collection = request.collectionUri()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, request.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.DATE_TAKEN, request.capturedAt.toEpochMilli())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: return@withContext StorageWriteResult(
                file = null,
                sidecar = null,
                success = false,
                error = storageError("MediaStore insert returned null for $displayName"),
            )

        var byteCount = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                val counting = DigestCountingOutputStream(output, digest) { bytes -> byteCount += bytes }
                writer(counting)
                counting.flush()
            } ?: error("Unable to open output stream for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }

            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            val stored = StoredCameraFile(
                uri = uri.toString(),
                displayName = displayName,
                mimeType = request.mimeType,
                kind = request.kind,
                bytes = byteCount,
                checksumSha256 = checksum,
            )
            val sidecar = writeSidecar(request, stored, relativePath)
            StorageWriteResult(file = stored, sidecar = sidecar, success = true)
        } catch (throwable: Throwable) {
            resolver.delete(uri, null, null)
            StorageWriteResult(
                file = null,
                sidecar = null,
                success = false,
                error = storageError(throwable.stackTraceToString()),
            )
        }
    }

    private fun writeSidecar(
        request: StorageWriteRequest,
        stored: StoredCameraFile,
        originalRelativePath: String,
    ): StoredCameraFile? {
        val sidecarName = stored.displayName.substringBeforeLast('.') + ".json"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sidecarName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, originalRelativePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
        return try {
            val json = buildSidecarJson(request, stored)
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            StoredCameraFile(
                uri = uri.toString(),
                displayName = sidecarName,
                mimeType = "application/json",
                kind = CameraObjectKind.Sidecar,
                bytes = json.toByteArray(Charsets.UTF_8).size.toLong(),
            )
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
            null
        }
    }
}

class CameraFileNameGenerator {
    private val sequence = AtomicInteger(1)
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US)

    fun generate(request: StorageWriteRequest): String {
        val timestamp = timestampFormatter.format(request.capturedAt.atZone(ZoneId.systemDefault()))
        val provider = request.providerId.cleanFilePart()
        val device = request.deviceName.cleanFilePart()
        val next = sequence.getAndIncrement().coerceAtMost(9999)
        return "${provider}_${device}_${timestamp}_${"%04d".format(next)}.${request.extension.trimStart('.')}"
    }
}

private class DigestCountingOutputStream(
    delegate: OutputStream,
    private val digest: MessageDigest,
    private val onBytes: (Long) -> Unit,
) : FilterOutputStream(delegate) {
    override fun write(b: Int) {
        out.write(b)
        digest.update(b.toByte())
        onBytes(1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        digest.update(b, off, len)
        onBytes(len.toLong())
    }
}

private fun StorageWriteRequest.collectionUri(): Uri {
    return when (kind) {
        CameraObjectKind.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        CameraObjectKind.Sidecar -> MediaStore.Files.getContentUri("external")
        CameraObjectKind.Raw,
        CameraObjectKind.Jpeg,
        CameraObjectKind.Preview -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
}

private fun StorageWriteRequest.relativePath(rootDirectoryName: String): String {
    val date = DateTimeFormatter.ISO_LOCAL_DATE.format(capturedAt.atZone(ZoneId.systemDefault()))
    val type = when (kind) {
        CameraObjectKind.Raw -> "RAW"
        CameraObjectKind.Jpeg -> "JPEG"
        CameraObjectKind.Preview -> "PREVIEW"
        CameraObjectKind.Video -> "VIDEO"
        CameraObjectKind.Sidecar -> "SIDECAR"
    }
    return "Pictures/$rootDirectoryName/$date/${deviceName.cleanFilePart()}/$type"
}

private fun String.cleanFilePart(): String {
    return trim()
        .ifEmpty { "Unknown" }
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .take(48)
}

private fun storageError(debug: String): CameraError {
    return CameraError(
        type = CameraErrorType.StorageFailed,
        userMessageZh = "文件保存失败",
        debugMessage = debug,
        fallbackSuggestionZh = "请确认系统相册可写、存储空间充足后重试。",
    )
}

private fun buildSidecarJson(request: StorageWriteRequest, stored: StoredCameraFile): String {
    val fields = linkedMapOf(
        "fileUri" to stored.uri,
        "fileName" to stored.displayName,
        "providerId" to request.providerId,
        "providerName" to request.providerName,
        "deviceId" to request.deviceId,
        "deviceName" to request.deviceName,
        "kind" to request.kind.name,
        "captureTime" to request.capturedAt.toString(),
        "originalFileName" to request.originalFileName,
        "objectHandle" to request.objectHandle,
        "checksumSha256" to stored.checksumSha256,
        "bytes" to stored.bytes?.toString(),
    )
    val metadata = request.metadata.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"${key.escapeJson()}\": \"${value.escapeJson()}\""
    }
    return buildString {
        appendLine("{")
        fields.entries.forEachIndexed { index, entry ->
            val suffix = if (index == fields.size - 1) "," else ","
            appendLine("  \"${entry.key}\": ${entry.value?.let { "\"${it.escapeJson()}\"" } ?: "null"}$suffix")
        }
        appendLine("  \"metadata\": $metadata")
        appendLine("}")
    }
}

private fun String.escapeJson(): String {
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
