package com.pasich.encly.editor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockIdTest {
    @Test
    fun anIdIsALowercaseHyphenatedUuid() {
        val id = newBlockId()
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(id), id)
    }

    @Test
    fun idsAreRandomVersion4Uuids() {
        val ids = List(200) { newBlockId() }
        assertEquals(200, ids.toSet().size)
        assertTrue(ids.all { it[14] == '4' && it[19] in "89ab" })
    }
}
