package com.camraw.core.project

import com.camraw.core.camera.api.CameraObjectKind
import com.camraw.core.camera.api.StoredCameraFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

data class CamrawProject(
    val projectId: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Instant = Instant.now(),
)

data class ProjectPhotoRecord(
    val photoId: String = UUID.randomUUID().toString(),
    val projectId: String,
    val providerId: String,
    val cameraModel: String?,
    val originalFileName: String?,
    val objectHandle: String?,
    val importedAt: Instant = Instant.now(),
    val files: List<StoredCameraFile>,
    val status: ProjectPhotoStatus,
    val metadata: Map<String, String> = emptyMap(),
) {
    val primaryKind: CameraObjectKind? = files.firstOrNull { it.kind != CameraObjectKind.Sidecar }?.kind
}

enum class ProjectPhotoStatus {
    Queued,
    Imported,
    Partial,
    Failed,
}

interface ProjectRepository {
    fun observeProjects(): Flow<List<CamrawProject>>
    fun observePhotos(projectId: String): Flow<List<ProjectPhotoRecord>>
    suspend fun createProject(name: String): CamrawProject
    suspend fun addPhoto(record: ProjectPhotoRecord): ProjectPhotoRecord
}

class InMemoryProjectRepository : ProjectRepository {
    private val projects = MutableStateFlow<List<CamrawProject>>(emptyList())
    private val photos = MutableStateFlow<List<ProjectPhotoRecord>>(emptyList())

    override fun observeProjects(): Flow<List<CamrawProject>> = projects.asStateFlow()

    override fun observePhotos(projectId: String): Flow<List<ProjectPhotoRecord>> {
        return photos.asStateFlow().map { records -> records.filter { it.projectId == projectId } }
    }

    override suspend fun createProject(name: String): CamrawProject {
        val project = CamrawProject(name = name)
        projects.value = projects.value + project
        return project
    }

    override suspend fun addPhoto(record: ProjectPhotoRecord): ProjectPhotoRecord {
        photos.value = photos.value + record
        return record
    }
}
