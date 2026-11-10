package com.pasich.encly.core.common

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * [AppStrings] on the Compose resources, which read the platform's current locale. On Android the app
 * keeps that locale on the in-app language (AppLocales), so this follows the language picked in the app.
 */
public class ComposeAppStrings : AppStrings {
    override suspend fun get(id: StringResource, vararg args: Any): String = getString(id, *args)
}
