package com.camraw.core.importing

import com.camraw.core.camera.api.CameraObject
import com.camraw.core.camera.api.CameraObjectKind

data class ImportBatchPlan(
    val objects: List<CameraObject>,
    val rawCount: Int,
    val jpegCount: Int,
    val previewCount: Int,
)

object ImportBatchPlanner {
    fun plan(
        objects: List<CameraObject>,
        includeRaw: Boolean = true,
        includeJpeg: Boolean = true,
        includePreview: Boolean = false,
    ): ImportBatchPlan {
        val planned = objects.filter { cameraObject ->
            when (cameraObject.kind) {
                CameraObjectKind.Raw -> includeRaw
                CameraObjectKind.Jpeg, CameraObjectKind.Heic -> includeJpeg
                CameraObjectKind.Preview -> includePreview
                else -> false
            }
        }
        return ImportBatchPlan(
            objects = planned,
            rawCount = planned.count { it.kind == CameraObjectKind.Raw },
            jpegCount = planned.count { it.kind == CameraObjectKind.Jpeg || it.kind == CameraObjectKind.Heic },
            previewCount = planned.count { it.kind == CameraObjectKind.Preview },
        )
    }
}
