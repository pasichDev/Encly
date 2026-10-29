package com.pasich.encly.presentation.screen.editnote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.state.NoteTemplate
import com.pasich.encly.editor.state.blocks
import com.pasich.encly.presentation.designsystem.EnclyChip
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * "Start from template" under a brand-new, still empty note: Blank (what a new note is anyway,
 * shown selected) and the ready outlines. Picking one fills the note with ordinary blocks, one undo
 * step; not picking changes nothing, and the row is gone with the first character typed.
 */
@Composable
internal fun TemplateChooser(modifier: Modifier = Modifier, viewModel: EditNoteViewModel = metroViewModel()) {
    val strings = LocalEditorStrings.current
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        modifier = modifier.padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
    ) {
        SectionOverline(stringResource(R.string.template_start))
        Row(
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            NoteTemplate.entries.forEach { template ->
                EnclyChip(
                    label = strings.text(template.label),
                    selected = template == NoteTemplate.BLANK,
                    onClick = { viewModel.applyTemplate(template.blocks(strings::text)) },
                )
            }
        }
    }
}
