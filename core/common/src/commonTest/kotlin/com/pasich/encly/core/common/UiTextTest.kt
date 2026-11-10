package com.pasich.encly.core.common

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalResourceApi::class)
class UiTextTest {
    @Test
    fun aResourceTextCarriesItsResourceAndIsComparedByIt() {
        assertEquals(UiText.Resource(resource("a")), UiText.of(resource("a")))
        assertEquals("a", UiText.Resource(resource("a")).id.key)
    }
}

/** A string resource named [key], as the generated `Res.string` accessors build them. */
@OptIn(InternalResourceApi::class)
internal fun resource(key: String) = StringResource("string:$key", key, emptySet())
