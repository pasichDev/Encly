package com.pasich.encly.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class UiTextTest {
    @Test
    fun aResourceTextCarriesItsIdAndIsComparedByIt() {
        assertEquals(UiText.Resource(7), UiText.of(7))
        assertEquals(7, UiText.Resource(7).id)
    }
}
