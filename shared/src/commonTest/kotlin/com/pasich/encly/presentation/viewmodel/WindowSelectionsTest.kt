package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import com.pasich.encly.core.di.AppViewModelFactory
import com.pasich.encly.core.security.NeverLocked
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class WindowSelectionsTest {
    private class HoldsSelection(val holder: SelectedTagHolder) : ViewModel()

    private val tags = SelectedTags(NeverLocked)
    private val factory = AppViewModelFactory(
        viewModelProviders = mapOf(HoldsSelection::class to { HoldsSelection(tags.forViewModel()) }),
        assistedFactoryProviders = emptyMap(),
        manualAssistedFactoryProviders = emptyMap(),
    )

    @Test
    fun aHostWithOneWindowSharesOneSelection() {
        val first = factory.create(HoldsSelection::class, CreationExtras.Empty)
        val second = factory.create(HoldsSelection::class, CreationExtras.Empty)

        assertSame(first.holder, second.holder)
    }

    @Test
    fun theViewModelsOfAWindowGetThatWindowsSelectionAndNoOtherWindowSeesIt() {
        val windowA = SelectedTagHolder()
        val windowB = SelectedTagHolder()

        val inA = factory.forWindow(windowA).create(HoldsSelection::class, CreationExtras.Empty)
        val alsoInA = factory.forWindow(windowA).create(HoldsSelection::class, CreationExtras.Empty)
        val inB = factory.forWindow(windowB).create(HoldsSelection::class, CreationExtras.Empty)

        assertSame(windowA, inA.holder)
        assertSame(windowA, alsoInA.holder)
        assertSame(windowB, inB.holder)
        assertNotSame(inA.holder, factory.create(HoldsSelection::class, CreationExtras.Empty).holder)
    }

    @Test
    fun theWindowOnlyHoldsWhileItsViewModelIsBeingCreated() {
        val window = SelectedTagHolder()

        WindowSelections.creatingFor(window) { assertSame(window, tags.forViewModel()) }

        assertNotSame(window, tags.forViewModel())
    }
}
