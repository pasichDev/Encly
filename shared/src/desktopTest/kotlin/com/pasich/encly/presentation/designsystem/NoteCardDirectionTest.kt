package com.pasich.encly.presentation.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/** A note card's text takes its paragraph direction from the text itself, whatever the layout direction is. */
@OptIn(ExperimentalTestApi::class)
class NoteCardDirectionTest {
    private fun androidx.compose.ui.test.ComposeUiTest.direction(text: String): ResolvedTextDirection {
        val layout = onNode(hasText(text)).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!
        val results = mutableListOf<TextLayoutResult>()
        layout(results)
        return results.single().getParagraphDirection(0)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(title: String, excerpt: String) = setContent {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme { EnclyNoteCard(title = title, excerpt = excerpt, onClick = {}) }
        }
    }

    @Test
    fun latinTextInAnRtlLayoutReadsLeftToRight() = runComposeUiTest {
        show(title = "Meeting", excerpt = "Agenda for the weekly sync with the whole team, long enough to wrap")

        assertEquals(ResolvedTextDirection.Ltr, direction("Meeting"))
        assertEquals(
            ResolvedTextDirection.Ltr,
            direction("Agenda for the weekly sync with the whole team, long enough to wrap"),
        )
    }

    @Test
    fun arabicTextInAnRtlLayoutStaysRightToLeft() = runComposeUiTest {
        show(title = "اجتماع", excerpt = "جدول الأعمال للاجتماع الأسبوعي")

        assertEquals(ResolvedTextDirection.Rtl, direction("اجتماع"))
        assertEquals(ResolvedTextDirection.Rtl, direction("جدول الأعمال للاجتماع الأسبوعي"))
    }
}
