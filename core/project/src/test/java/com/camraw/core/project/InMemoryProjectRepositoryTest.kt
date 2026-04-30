package com.camraw.core.project

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryProjectRepositoryTest {
    @Test
    fun createsProject() = runTest {
        val repository = InMemoryProjectRepository()

        val project = repository.createProject("Shoot")

        assertEquals("Shoot", project.name)
        assertEquals(listOf(project), repository.observeProjects().first())
    }
}
