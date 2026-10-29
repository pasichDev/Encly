package com.pasich.encly.presentation.screen.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.appearance_dynamic_on_note
import com.pasich.encly.core.resources.appearance_font
import com.pasich.encly.core.resources.appearance_mode
import com.pasich.encly.core.resources.appearance_mode_note_dark
import com.pasich.encly.core.resources.appearance_mode_note_light
import com.pasich.encly.core.resources.appearance_mode_note_midnight
import com.pasich.encly.core.resources.appearance_mode_note_system_dark
import com.pasich.encly.core.resources.appearance_mode_note_system_light
import com.pasich.encly.core.resources.appearance_preview_body
import com.pasich.encly.core.resources.appearance_preview_label
import com.pasich.encly.core.resources.appearance_preview_tag
import com.pasich.encly.core.resources.appearance_preview_title
import com.pasich.encly.core.resources.appearance_text_desc
import com.pasich.encly.core.resources.appearance_text_section
import com.pasich.encly.core.resources.dynamic_color
import com.pasich.encly.core.resources.dynamic_color_desc
import com.pasich.encly.core.resources.font_sample
import com.pasich.encly.core.resources.font_style_cozy
import com.pasich.encly.core.resources.font_style_cozy_desc
import com.pasich.encly.core.resources.font_style_modern
import com.pasich.encly.core.resources.font_style_modern_desc
import com.pasich.encly.core.resources.font_style_tech
import com.pasich.encly.core.resources.font_style_tech_desc
import com.pasich.encly.core.resources.palette_forest
import com.pasich.encly.core.resources.palette_graphite
import com.pasich.encly.core.resources.palette_midnight
import com.pasich.encly.core.resources.palette_ocean
import com.pasich.encly.core.resources.palette_paper
import com.pasich.encly.core.resources.settings_appearance
import com.pasich.encly.core.resources.theme
import com.pasich.encly.core.resources.theme_type_dark
import com.pasich.encly.core.resources.theme_type_light
import com.pasich.encly.core.resources.theme_type_system
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.presentation.components.FontSizeSlider
import com.pasich.encly.presentation.components.NoteLineSpacingSlider
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
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Swatches render at this opacity while dynamic colour overrides the palette accent. */
private const val DYNAMIC_SWATCH_ALPHA = 0.38f

@Composable
fun AppearanceScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = metroViewModel(),
) {
    val settings by viewModel.themeSettings.collectAsStateWithLifecycle()
    val fontSize by viewModel.defaultFontSize.collectAsStateWithLifecycle()
    val lineSpacing by viewModel.defaultLineSpacing.collectAsStateWithLifecycle()
    AppearanceContent(
        settings = settings,
        textDefaults = TextDefaults(
            fontSize,
            lineSpacing,
            viewModel::setDefaultFontSize,
            viewModel::setDefaultLineSpacing,
        ),
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
    textDefaults: TextDefaults,
    supportsDynamicColor: Boolean,
    actions: AppearanceActions,
    modifier: Modifier = Modifier,
) {
    val spacing = EnclyTheme.spacing
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { EnclyTopBar(title = stringResource(Res.string.settings_appearance), onBack = actions.onBack) },
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
                    title = stringResource(Res.string.dynamic_color),
                    supporting = stringResource(Res.string.dynamic_color_desc),
                    checked = settings.dynamic,
                    onCheckedChange = actions.onDynamicColor,
                )
            }
            FontSection(selected = settings.fontStyle, onFontStyle = actions.onFontStyle)
            TextDefaultsSection(textDefaults)
            PreviewCard()
        }
    }
}

/** The default text size and line spacing of notes, and how to change them. */
private class TextDefaults(
    val fontSize: Int,
    val lineSpacing: NoteLineSpacing,
    val onFontSize: (Int) -> Unit,
    val onLineSpacing: (NoteLineSpacing) -> Unit,
)

/** The size and spacing every note starts with; a note can set its own from its menu. */
@Composable
private fun TextDefaultsSection(defaults: TextDefaults, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(Res.string.appearance_text_section))
        FontSizeSlider(currentSize = defaults.fontSize, onSizeChange = defaults.onFontSize)
        NoteLineSpacingSlider(selected = defaults.lineSpacing, onSelect = defaults.onLineSpacing)
        Text(
            text = stringResource(Res.string.appearance_text_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun ThemePalette.labelRes(): StringResource = when (this) {
    ThemePalette.PAPER -> Res.string.palette_paper
    ThemePalette.FOREST -> Res.string.palette_forest
    ThemePalette.OCEAN -> Res.string.palette_ocean
    ThemePalette.GRAPHITE -> Res.string.palette_graphite
    ThemePalette.MIDNIGHT -> Res.string.palette_midnight
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
        SectionOverline(stringResource(Res.string.theme))
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
                text = stringResource(Res.string.appearance_dynamic_on_note),
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
        ModeNote.SYSTEM_SHOWING_LIGHT -> Res.string.appearance_mode_note_system_light
        ModeNote.SYSTEM_SHOWING_DARK -> Res.string.appearance_mode_note_system_dark
        ModeNote.ALWAYS_LIGHT -> Res.string.appearance_mode_note_light
        ModeNote.ALWAYS_DARK -> Res.string.appearance_mode_note_dark
        ModeNote.ALWAYS_DARK_PALETTE -> Res.string.appearance_mode_note_midnight
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(Res.string.appearance_mode))
        EnclySegmentedControl(
            segments = listOf(
                Segment(ThemeType.SYSTEM, stringResource(Res.string.theme_type_system), enabled = selectable),
                Segment(ThemeType.LIGHT, stringResource(Res.string.theme_type_light), enabled = selectable),
                // Dark stays tappable-looking for Midnight: it is the mode actually shown.
                Segment(ThemeType.DARK, stringResource(Res.string.theme_type_dark)),
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

private data class FontOption(val style: FontStyleType, val title: StringResource, val description: StringResource)

private val fontOptions = listOf(
    FontOption(FontStyleType.COZY_EDITOR, Res.string.font_style_cozy, Res.string.font_style_cozy_desc),
    FontOption(FontStyleType.MODERN_SIMPLE, Res.string.font_style_modern, Res.string.font_style_modern_desc),
    FontOption(FontStyleType.TECH_MINIMAL, Res.string.font_style_tech, Res.string.font_style_tech_desc),
)

@Composable
private fun FontSection(selected: FontStyleType, onFontStyle: (FontStyleType) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(Res.string.appearance_font))
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
                text = stringResource(Res.string.font_sample),
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
        Text(stringResource(Res.string.appearance_preview_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(Res.string.appearance_preview_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        ) {
            val meta = EnclyTheme.typography.meta.copy(fontWeight = FontWeight.SemiBold)
            EnclyTagPill(text = stringResource(Res.string.appearance_preview_tag))
            Text(
                stringResource(Res.string.appearance_preview_label),
                style = meta,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
