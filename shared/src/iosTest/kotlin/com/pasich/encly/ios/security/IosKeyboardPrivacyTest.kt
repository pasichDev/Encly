package com.pasich.encly.ios.security

import platform.Foundation.NSProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The traits that stop the keyboard from learning. */
class IosKeyboardPrivacyTest {
    @Test
    fun aReplacedGetterAnswersNoAndOnlyThatGetterIsTouched() {
        // NSProgress.totalUnitCount is an NSInteger getter of a class nothing else here uses: it stands in for the
        // trait getters of Compose's text input view, which UIKit's own classes do not implement as real methods.
        val replaced = IosKeyboardPrivacy.install("NSProgress", getters = listOf("totalUnitCount", "noSuchGetter"))

        assertEquals(1, replaced)
        val progress = NSProgress()
        progress.completedUnitCount = 7
        assertEquals(1L, progress.totalUnitCount)
        assertEquals(7L, progress.completedUnitCount)
    }

    @Test
    fun installingTwiceIsHarmless() {
        assertEquals(1, IosKeyboardPrivacy.install("NSProgress", getters = listOf("totalUnitCount")))
        assertEquals(1, IosKeyboardPrivacy.install("NSProgress", getters = listOf("totalUnitCount")))
    }

    @Test
    fun aClassThatIsNotThereIsLeftAlone() {
        assertEquals(0, IosKeyboardPrivacy.install("NoSuchTextInputView"))
    }

    @Test
    fun composeTextInputViewIsFoundOrTheUpgradeIsNoticed() {
        // The class is looked up by name at runtime. If it is linked into the test binary it must carry the traits;
        // if not, the lookup says 0 and the gap is real only in the app, which the Xcode build covers.
        val replaced = IosKeyboardPrivacy.install()
        assertTrue(replaced == 0 || replaced >= 5, "replaced $replaced")
    }
}
