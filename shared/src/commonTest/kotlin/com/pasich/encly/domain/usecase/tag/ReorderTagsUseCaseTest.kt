package com.pasich.encly.domain.usecase.tag

import com.pasich.encly.data.model.Tag
import com.pasich.encly.testutil.TestTagsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReorderTagsUseCaseTest {
    private val repository = TestTagsRepository()

    @Test
    fun theListOrderBecomesThePositions() = runTest {
        val tags = listOf(Tag(id = 3, position = 7), Tag(id = 1, position = 0), Tag(id = 2, position = 4))

        assertTrue(ReorderTagsUseCase(repository)(tags).isSuccess)

        val saved = repository.savedOrders.single()
        assertEquals(listOf(3L, 1L, 2L), saved.map { it.id })
        assertEquals(listOf(0, 1, 2), saved.map { it.position })
    }

    @Test
    fun aFailedSaveIsReported() = runTest {
        repository.failWrites = true

        assertTrue(ReorderTagsUseCase(repository)(listOf(Tag(id = 1))).isFailure)
    }
}
