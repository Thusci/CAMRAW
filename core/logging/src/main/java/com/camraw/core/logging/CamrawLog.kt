package com.camraw.core.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CamrawLog {
    @Volatile
    var minimumLevel: LogLevel = LogLevel.Debug

    @Volatile
    private var fileSink: File? = null

    fun installFileSink(context: Context) {
        val logDir = File(context.filesDir, "logs").apply { mkdirs() }
        fileSink = File(logDir, "camraw-${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}.log")
    }

    fun error(category: LogCategory, message: String, throwable: Throwable? = null) {
        write(LogLevel.Error, category, message, throwable)
    }

    fun warn(category: LogCategory, message: String, throwable: Throwable? = null) {
        write(LogLevel.Warn, category, message, throwable)
    }

    fun info(category: LogCategory, message: String, throwable: Throwable? = null) {
        write(LogLevel.Info, category, message, throwable)
    }

    fun debug(category: LogCategory, message: String, throwable: Throwable? = null) {
        write(LogLevel.Debug, category, message, throwable)
    }

    fun trace(category: LogCategory, message: String, throwable: Throwable? = null) {
        write(LogLevel.Trace, category, message, throwable)
    }

    private fun write(level: LogLevel, category: LogCategory, message: String, throwable: Throwable?) {
        if (level.priority > minimumLevel.priority) return
        val tag = "CAMRAW/${category.tag}"
        when (level) {
            LogLevel.Error -> Log.e(tag, message, throwable)
            LogLevel.Warn -> Log.w(tag, message, throwable)
            LogLevel.Info -> Log.i(tag, message, throwable)
            LogLevel.Debug -> Log.d(tag, message, throwable)
            LogLevel.Trace -> Log.v(tag, message, throwable)
        }
        val sanitized = message.replace(Regex("/storage/[^\\s]+"), "/storage/[redacted]")
        val throwableText = throwable?.stackTraceToString()?.let { "\n$it" }.orEmpty()
        fileSink?.appendText(
            "${Instant.now()} ${level.name.uppercase()} ${category.tag}: $sanitized$throwableText\n",
        )
    }

    suspend fun exportZip(context: Context): File = withContext(Dispatchers.IO) {
        val logDir = File(context.filesDir, "logs").apply { mkdirs() }
        val export = File(context.cacheDir, "camraw-logs.zip")
        ZipOutputStream(export.outputStream()).use { zip ->
            logDir.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        export
    }
}

enum class LogLevel(val priority: Int) {
    Error(0),
    Warn(1),
    Info(2),
    Debug(3),
    Trace(4),
}

enum class LogCategory(val tag: String) {
    App("app"),
    Camera("camera"),
    Provider("provider"),
    Usb("usb"),
    Ptp("ptp"),
    Sony("sony"),
    Preview("preview"),
    Capture("capture"),
    Storage("storage"),
    Native("native"),
}
