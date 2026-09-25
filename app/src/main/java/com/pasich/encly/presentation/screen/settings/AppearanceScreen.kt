package com.pasich.encly.presentation.screen.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.presentation.designsystem.EnclyCard
import com.pasich.encly.presentation.designsystem.EnclyCardStyle
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyRadioRow
import com.pasich.encly.presentation.designsystem.EnclySegmentedControl
import com.pasich.encly.presentation.designsystem.EnclySwitchRow
import com.pasich.encly.presentation.designsystem.EnclyTagPill
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.Segment
import com.pasich.encly.presentation.designsystem.ThemeSwatch
import com.pasich.encly.presentation.viewmodel.AppearanceViewModel
import com.pasich.encly.presentation.viewmodel.ModeNote
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.colorSchemeFor
import com.pasich.encly.ui.theme.fontSet

/** Swatches render at this opacity while dynamic colour overrides the palette accent. */
private const val DYNAMIC_SWATCH_ALPHA = 0.38f

@Composable
fun AppearanceScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val settings by viewModel.themeSettings.collectAsStateWithLifecycle()
    AppearanceContent(
        settings = settings,
        supportsDynamicColor = viewModel.supportsDynamicColors(),
        actions = AppearanceActions(
            onBack = { navController.popBackStack() },
            onPalette = viewModel::selectPalette,
            onMode = viewModel::selectMode,
            onDynamicColor = viewModel::setDynamicColor,
            onFontStyle = viewModel::selectFontStyle,
        ),
        modifier = modifier,
    )
}

@Composable
private fun AppearanceContent(
    settings: ThemeSettings,
    supportsDynamicColor: Boolean,
    actions: AppearanceActions,
    modifier: Modifier = Modifier,
) {
    val spacing = EnclyTheme.spacing
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { EnclyTopBar(title = stringResource(R.string.settings_appearance), onBack = actions.onBack) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.gutter, end = spacing.gutter, top = spacing.s, bottom = spacing.l),
        ) {
            ThemeSection(
                settings = settings,
                dynamicActive = settings.dynamic && supportsDynamicColor,
                onPalette = actions.onPalette,
            )
            ModeSection(settings = settings, onMode = actions.onMode)
            if (supportsDynamicColor) {
                EnclySwitchRow(
                    title = stringResource(R.string.dynamic_color),
                    supporting = stringResource(R.string.dynamic_color_desc),
                    checked = settings.dynamic,
                    onCheckedChange = actions.onDynamicColor,
                )
            }
            FontSection(selected = settings.fontStyle, onFontStyle = actions.onFontStyle)
            PreviewCard()
        }
    }
}

/** What the Appearance screen can ask for; every choice is saved at once. */
private class AppearanceActions(
    val onBack: () -> Unit,
    val onPalette: (ThemePalette) -> Unit,
    val onMode: (ThemeType) -> Unit,
    val onDynamicColor: (Boolean) -> Unit,
    val onFontStyle: (FontStyleType) -> Unit,
)

@StringRes
private fun ThemePalette.labelRes(): Int = when (this) {
    ThemePalette.PAPER -> R.string.palette_paper
    ThemePalette.FOREST -> R.string.palette_forest
    ThemePalette.OCEAN -> R.string.palette_ocean
    ThemePalette.GRAPHITE -> R.string.palette_graphite
    ThemePalette.MIDNIGHT -> R.string.palette_midnight
}

@Composable
private fun ThemeSection(
    settings: ThemeSettings,
    dynamicActive: Boolean,
    onPalette: (ThemePalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = settings.isDark(isSystemInDarkTheme())
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(R.string.theme))
        Row(
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            ThemePalette.entries.forEach { palette ->
                ThemeSwatch(
                    // A miniature of the palette in the resolved mode (Midnight is always dark).
                    preview = colorSchemeFor(palette, dark || palette.isAlwaysDark),
                    label = stringResource(palette.labelRes()),
                    selected = palette == settings.palette,
                    onClick = { onPalette(palette) },
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (dynamicActive) DYNAMIC_SWATCH_ALPHA else 1f),
                )
            }
        }
        if (dynamicActive) {
            Text(
                text = stringResource(R.string.appearance_dynamic_on_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeSection(settings: ThemeSettings, onMode: (ThemeType) -> Unit, modifier: Modifier = Modifier) {
    val selectable = AppearanceViewModel.isModeSelectable(settings.palette)
    val note = when (AppearanceViewModel.modeNote(settings, isSystemInDarkTheme())) {
        ModeNote.SYSTEM_SHOWING_LIGHT -> R.string.appearance_mode_note_system_light
        ModeNote.SYSTEM_SHOWING_DARK -> R.string.appearance_mode_note_system_dark
        ModeNote.ALWAYS_LIGHT -> R.string.appearance_mode_note_light
        ModeNote.ALWAYS_DARK -> R.string.appearance_mode_note_dark
        ModeNote.ALWAYS_DARK_PALETTE -> R.string.appearance_mode_note_midnight
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(R.string.appearance_mode))
        EnclySegmentedControl(
            segments = listOf(
                Segment(ThemeType.SYSTEM, stringResource(R.string.theme_type_system), enabled = selectable),
                Segment(ThemeType.LIGHT, stringResource(R.string.theme_type_light), enabled = selectable),
                // Dark stays tappable-looking for Midnight: it is the mode actually shown.
                Segment(ThemeType.DARK, stringResource(R.string.theme_type_dark)),
            ),
            selected = if (selectable) settings.type else ThemeType.DARK,
            onSelect = onMode,
        )
        Text(
            text = stringResource(note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class FontOption(
    val style: FontStyleType,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
)

private val fontOptions = listOf(
    FontOption(FontStyleType.COZY_EDITOR, R.string.font_style_cozy, R.string.font_style_cozy_desc),
    FontOption(FontStyleType.MODERN_SIMPLE, R.string.font_style_modern, R.string.font_style_modern_desc),
    FontOption(FontStyleType.TECH_MINIMAL, R.string.font_style_tech, R.string.font_style_tech_desc),
)

@Composable
private fun FontSection(selected: FontStyleType, onFontStyle: (FontStyleType) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(R.string.appearance_font))
        EnclyGroup(modifier = Modifier.selectableGroup()) {
            fontOptions.forEach { option ->
                FontRow(option = option, selected = option.style == selected, onClick = { onFontStyle(option.style) })
            }
        }
    }
}

@Composable
private fun FontRow(option: FontOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EnclyRadioRow(
        title = stringResource(option.title),
        description = stringResource(option.description),
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leading = {
            Text(
                text = stringResource(R.string.font_sample),
                style = EnclyTheme.typography.fontSample.copy(fontFamily = option.style.fontSet().display),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = EnclyTheme.spacing.xxl),
            )
        },
    )
}

@Composable
private fun PreviewCard(modifier: Modifier = Modifier) {
    EnclyCard(modifier = modifier, style = EnclyCardStyle.OUTLINED) {
        Text(stringResource(R.string.appearance_preview_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.appearance_preview_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        ) {
            val meta = EnclyTheme.typography.meta.copy(fontWeight = FontWeight.SemiBold)
            EnclyTagPill(text = stringResource(R.string.appearance_preview_tag))
            Text(
                stringResource(R.string.appearance_preview_label),
                style = meta,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
