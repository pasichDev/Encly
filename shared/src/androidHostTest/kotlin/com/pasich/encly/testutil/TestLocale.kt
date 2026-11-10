package com.pasich.encly.testutil

import android.os.LocaleList
import java.util.Locale

/**
 * The process's default locale, which is what the Compose resources read: a test that wants a
 * language sets it here and puts the original back afterwards.
 */
object TestLocale {
    fun set(tag: String) {
        val locales = LocaleList.forLanguageTags(tag)
        LocaleList.setDefault(locales)
        Locale.setDefault(locales[0])
    }

    fun <T> within(tag: String, block: () -> T): T {
        val saved = LocaleList.getDefault()
        set(tag)
        try {
            return block()
        } finally {
            LocaleList.setDefault(saved)
            Locale.setDefault(saved[0])
        }
    }
}
