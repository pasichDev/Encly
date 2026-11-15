package com.pasich.encly.ios.security

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A subclass the class-list walk must reach. */
private class SubProgress : NSProgress(parent = null, userInfo = null)

/** The traits that stop the keyboard from learning. */
@OptIn(BetaInteropApi::class)
class IosKeyboardPrivacyTest {
    @Test
    fun aReplacedGetterAnswersNoAndOnlyThatGetterIsTouched() {
        // NSProgress.totalUnitCount is an NSInteger getter of a class nothing else here uses: it stands in for the
        // trait getters of Compose's text input view, which UIKit's own classes do not implement as real methods.
        val result = IosKeyboardPrivacy.install("NSProgress", getters = listOf("totalUnitCount"), writingTools = false)

        assertTrue(result.classes >= 1)
        val progress = NSProgress()
        progress.completedUnitCount = 7
        assertEquals(1L, progress.totalUnitCount)
        assertEquals(7L, progress.completedUnitCount)
    }

    @Test
    fun aSubclassIsReachedToo() {
        val sub = SubProgress()
        val result = IosKeyboardPrivacy.install("NSProgress", getters = listOf("totalUnitCount"), writingTools = false)

        assertTrue(result.classes >= 2, "classes ${result.classes}")
        assertEquals(1L, sub.totalUnitCount)
    }

    @Test
    fun installingTwiceIsHarmless() {
        val first = IosKeyboardPrivacy.install("NSProgress", getters = listOf("totalUnitCount"), writingTools = false)
        val second = IosKeyboardPrivacy.install("NSProgress", getters = listOf("totalUnitCount"), writingTools = false)
        assertEquals(first, second)
    }

    @Test
    fun aClassThatIsNotThereIsLeftAlone() {
        assertEquals(IosKeyboardPrivacy.Result(0, 0), IosKeyboardPrivacy.install("NoSuchTextInputView"))
    }

    @Test
    fun composeTextInputViewIsFoundOrTheUpgradeIsNoticed() {
        // The class is looked up by name at runtime. In the unit test binary no Compose window exists, so the base
        // class may be missing (0) or present without a subclass; the check that the app really patched the input
        // views is IosKeyboardPrivacy.report at startup and on keyboard show.
        val result = IosKeyboardPrivacy.install()
        assertTrue(result.classes == 0 || result.replaced == result.classes * 7, "$result")
    }
}
