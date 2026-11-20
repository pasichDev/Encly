package com.pasich.encly.domain.model

import com.pasich.encly.presentation.viewmodel.NotePreviewsViewModel
import com.pasich.encly.testutil.InMemoryKeyValueStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePreviewsTest {
    private val flags = InMemoryKeyValueStore()

    @Test
    fun previewsAreOnByDefault() {
        assertTrue(NotePreviews(flags).show.value)
    }

    @Test
    fun theChoiceIsStoredAndFollowedBeforeAnyScreenAsks() {
        NotePreviews(flags).setShow(false)

        assertFalse("read at once on the next start", NotePreviews(flags).show.value)

        NotePreviews(flags).setShow(true)
        assertTrue(NotePreviews(flags).show.value)
    }

    @Test
    fun theViewModelReadsAndWritesTheSameSwitch() {
        val previews = NotePreviews(flags)
        val viewModel = NotePreviewsViewModel(previews)

        viewModel.setShow(false)

        assertFalse(viewModel.show.value)
        assertFalse(previews.show.value)
        assertFalse(NotePreviews(flags).show.value)
    }
}
