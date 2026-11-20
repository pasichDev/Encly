package com.pasich.encly.presentation.screen.editnote

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.note_load_failed
import com.pasich.encly.core.resources.note_open_elsewhere
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/** What is said under the title: that the content could not be read, or that another window edits the note. */
internal fun LazyListScope.editorNotices(contentLoadFailed: Boolean, editedElsewhere: Boolean, gap: Dp) {
    if (contentLoadFailed) {
        item(key = "unreadable") {
            EnclyCallout(
                text = stringResource(Res.string.note_load_failed),
                tone = CalloutTone.WARNING,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter, vertical = gap),
            )
        }
    }
    if (editedElsewhere) {
        item(key = "elsewhere") {
            EnclyCallout(
                text = stringResource(Res.string.note_open_elsewhere),
                tone = CalloutTone.INFO,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter, vertical = gap),
            )
        }
    }
}
