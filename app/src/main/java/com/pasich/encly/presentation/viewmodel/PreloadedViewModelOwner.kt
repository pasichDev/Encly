package com.pasich.encly.presentation.viewmodel

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/** A screen-test owner whose ViewModels are already present in its store. */
interface PreloadedViewModelOwner : ViewModelStoreOwner

/**
 * AndroidX Hilt 1.4 creates its Activity-bound factory before checking the owner store. Screen
 * tests use a plain Activity and preloaded ViewModels, so they can read that store directly.
 * App owners still use the regular Hilt factory.
 */
@Composable
inline fun <reified VM : ViewModel> enclyViewModel(): VM {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner provided" }
    return if (owner is PreloadedViewModelOwner) {
        viewModel(viewModelStoreOwner = owner)
    } else {
        hiltViewModel(viewModelStoreOwner = owner)
    }
}
