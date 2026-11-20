package com.pasich.encly.testutil

import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.platform.AppLocaleController

/** The in-app language, in memory. */
internal class FakeAppLocales(var language: AppLanguage = AppLanguage.SYSTEM) : AppLocaleController {
    var applied = 0
        private set

    override fun current(): AppLanguage = language

    override fun apply(language: AppLanguage) {
        applied++
        this.language = language
    }
}
