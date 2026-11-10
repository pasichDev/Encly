@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.ios.security

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import platform.Foundation.NSClassFromString
import platform.objc.class_getInstanceMethod
import platform.objc.class_replaceMethod
import platform.objc.sel_registerName

/**
 * What Encly asks of the keyboard for every text input, the iOS counterpart of Android's
 * `IME_FLAG_NO_PERSONALIZED_LEARNING`: no autocorrection, no spell checking, no smart quotes, dashes or
 * insert/delete, no inline predictions. The system keyboard learns from what is typed through autocorrection and
 * the suggestions bar; with these off it has nothing to learn from, and a third-party keyboard that honours the
 * traits gets the same signal. iOS has no single "do not learn" flag, and a keyboard may ignore any of these (see
 * SECURITY.md); a note editor cannot use the only stronger signal, secure text entry, which would make the field
 * single-line dots.
 *
 * Compose draws no text field of its own on iOS: every text input is a `CMPTextInputView` (the UITextInput view
 * of Compose Multiplatform), whose traits Compose fills in from `KeyboardOptions`, and only partly: it knows
 * `autoCorrectEnabled` and nothing of spell checking or the smart punctuation. The editor's own fields also ask
 * for autocorrection explicitly. So the getters of those traits are replaced on that one class and always answer
 * "no", whatever Compose assigned. If a Compose upgrade renames the class, [install] finds none and returns 0, and
 * the test for it fails.
 */
internal object IosKeyboardPrivacy {
    private const val COMPOSE_INPUT_VIEW = "CMPTextInputView"

    /** The `UITextInputTraits` getters that learn or rewrite; each type is an NSInteger enum whose `.no` is 1. */
    private val TRAITS = listOf(
        "autocorrectionType",
        "spellCheckingType",
        "smartQuotesType",
        "smartDashesType",
        "smartInsertDeleteType",
        "inlinePredictionType",
    )
    private const val NO = 1L
    private const val NSINTEGER_GETTER = "q@:"

    /**
     * Makes the instances of [className] answer "no" (1) for every one of [getters] they implement; the number of
     * getters replaced (0 when the class is not there). Safe to call more than once. [getters] is [TRAITS] unless a
     * test points it at a getter of a harmless class.
     */
    fun install(className: String = COMPOSE_INPUT_VIEW, getters: List<String> = TRAITS): Int {
        val type = NSClassFromString(className) ?: return 0
        val answerNo = staticCFunction { _: COpaquePointer?, _: COpaquePointer? -> NO }
        return getters.count { name ->
            val selector = sel_registerName(name)
            val implemented = class_getInstanceMethod(type, selector) != null
            if (implemented) {
                class_replaceMethod(type, selector, answerNo.reinterpret<CFunction<() -> Unit>>(), NSINTEGER_GETTER)
            }
            implemented
        }
    }
}
