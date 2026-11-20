package com.pasich.encly.ui.screens

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.presentation.designsystem.EnclyNoteColorPicker
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorPickerTest : ComposeScreenTest() {

    @Test
    fun everySwatchIsNamedAndTheSelectedOneIsMarked() {
        // Wider than the screen, so that the lazy row composes every swatch.
        setScreen { EnclyNoteColorPicker(NoteColor.TEAL, onSelect = {}, modifier = Modifier.requiredWidth(WIDE)) }

        NoteColor.entries.forEach { color ->
            val swatch = rule.onNodeWithContentDescription(str(color.labelRes))
            if (color == NoteColor.TEAL) swatch.assertIsSelected() else swatch.assertIsNotSelected()
        }
    }

    @Test
    fun tappingASwatchSelectsThatColour() {
        var picked: NoteColor? = null
        var selected by mutableStateOf(NoteColor.NONE)
        setScreen {
            EnclyNoteColorPicker(
                selected = selected,
                modifier = Modifier.requiredWidth(WIDE),
                onSelect = {
                    picked = it
                    selected = it
                },
            )
        }

        rule.onNodeWithContentDescription(str(NoteColor.PURPLE.labelRes)).performClick()

        assertEquals(NoteColor.PURPLE, picked)
        rule.onNodeWithContentDescription(str(NoteColor.PURPLE.labelRes)).assertIsSelected()
        rule.onNodeWithContentDescription(str(NoteColor.NONE.labelRes)).assertIsNotSelected()
    }

    private companion object {
        val WIDE = 700.dp
    }
}
