package com.pasich.encly.presentation.components

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * A toast with the string resource [text] (filled with [args]) in the in-app language. Resources
 * are read in a coroutine and the toast is shown on the main thread, so click handlers and other
 * plain functions can call it.
 */
internal fun showToast(context: Context, text: StringResource, duration: Int, vararg args: Any) {
    CoroutineScope(Dispatchers.Main.immediate).launch {
        Toast.makeText(context, getString(text, *args), duration).show()
    }
}
