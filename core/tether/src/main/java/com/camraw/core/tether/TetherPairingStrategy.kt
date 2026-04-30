package com.camraw.core.tether

import com.camraw.core.camera.api.CameraObject
import com.camraw.core.camera.api.CameraObjectKind
import java.time.Duration

data class RawJpegPair(
    val baseName: String,
    val rawObject: CameraObject?,
    val jpegObject: CameraObject?,
    val status: PairStatus,
)

enum class PairStatus {
    RawAndJpeg,
    RawOnly,
    JpegOnly,
}

object TetherPairingStrategy {
    private val rawExtensions = setOf("arw", "cr2", "cr3", "nef", "raf", "rw2", "orf", "dng", "pef", "srw")
    private val jpegExtensions = setOf("jpg", "jpeg", "jpe")
    private val fallbackCaptureWindow = Duration.ofSeconds(3)

    fun pair(objects: List<CameraObject>): List<RawJpegPair> {
        val rawByBase = linkedMapOf<String, MutableList<CameraObject>>()
        val jpegByBase = linkedMapOf<String, MutableList<CameraObject>>()
        objects.sortedWith(compareBy<CameraObject> { it.capturedAt }.thenBy { it.fileName }).forEach { cameraObject ->
            when {
                cameraObject.isRawLike() -> rawByBase.getOrPut(cameraObject.baseName()) { mutableListOf() }.add(cameraObject)
                cameraObject.isJpegLike() -> jpegByBase.getOrPut(cameraObject.baseName()) { mutableListOf() }.add(cameraObject)
            }
        }

        val pairs = mutableListOf<RawJpegPair>()
        val consumedJpegs = mutableSetOf<String>()
        rawByBase.forEach { (baseName, rawObjects) ->
            rawObjects.forEach { raw ->
                val jpeg = jpegByBase[baseName]?.firstOrNull { it.objectId !in consumedJpegs }
                    ?: findTimeWindowJpeg(raw, jpegByBase.values.flatten(), consumedJpegs)
                if (jpeg != null) consumedJpegs.add(jpeg.objectId)
                pairs += RawJpegPair(
                    baseName = baseName,
                    rawObject = raw,
                    jpegObject = jpeg,
                    status = if (jpeg == null) PairStatus.RawOnly else PairStatus.RawAndJpeg,
                )
            }
        }

        jpegByBase.forEach { (baseName, jpegObjects) ->
            jpegObjects.filterNot { it.objectId in consumedJpegs }.forEach { jpeg ->
                pairs += RawJpegPair(
                    baseName = baseName,
                    rawObject = null,
                    jpegObject = jpeg,
                    status = PairStatus.JpegOnly,
                )
            }
        }
        return pairs.sortedWith(compareBy<RawJpegPair> { it.rawObject?.capturedAt ?: it.jpegObject?.capturedAt }.thenBy { it.baseName })
    }

    private fun findTimeWindowJpeg(
        raw: CameraObject,
        jpegs: List<CameraObject>,
        consumedJpegs: Set<String>,
    ): CameraObject? {
        val rawTime = raw.capturedAt ?: return null
        return jpegs
            .filterNot { it.objectId in consumedJpegs }
            .mapNotNull { jpeg ->
                val jpegTime = jpeg.capturedAt ?: return@mapNotNull null
                val delta = Duration.between(rawTime, jpegTime).abs()
                jpeg.takeIf { delta <= fallbackCaptureWindow }?.let { it to delta }
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun CameraObject.baseName(): String = fileName.substringBeforeLast('.', fileName)

    private fun CameraObject.extension(): String = fileName.substringAfterLast('.', "").lowercase()

    private fun CameraObject.isRawLike(): Boolean {
        return kind == CameraObjectKind.Raw || extension() in rawExtensions
    }

    private fun CameraObject.isJpegLike(): Boolean {
        return kind == CameraObjectKind.Jpeg || extension() in jpegExtensions
    }
}
