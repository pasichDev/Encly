package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.security_previews_desc
import com.pasich.encly.core.resources.security_previews_title
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySwitchRow
import com.pasich.encly.presentation.viewmodel.NotePreviewsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/** "Show note previews" (see NotePreviews): on by default; off, the note lists show no text of the notes. */
@Composable
internal fun NotePreviewsSetting(modifier: Modifier = Modifier, viewModel: NotePreviewsViewModel = metroViewModel()) {
    val show by viewModel.show.collectAsState()
    EnclyGroup(modifier = modifier) {
        EnclySwitchRow(
            title = stringResource(Res.string.security_previews_title),
            supporting = stringResource(Res.string.security_previews_desc),
            icon = EnclyIcons.Eye,
            checked = show,
            onCheckedChange = viewModel::setShow,
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
        )
    }
}
