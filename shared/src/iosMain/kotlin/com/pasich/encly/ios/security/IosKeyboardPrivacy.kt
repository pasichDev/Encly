@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.ios.security

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.Foundation.NSClassFromString
import platform.Foundation.NSLog
import platform.objc.objc_copyClassList
import platform.objc.sel_registerName
import platform.posix.RTLD_DEFAULT
import platform.posix.dlsym
import platform.posix.free

private typealias ReplaceMethod =
    (COpaquePointer?, COpaquePointer?, COpaquePointer?, CPointer<ByteVar>?) -> COpaquePointer?

/**
 * What Encly asks of the keyboard for every text input, the iOS counterpart of Android's
 * `IME_FLAG_NO_PERSONALIZED_LEARNING`: no autocorrection, no spell checking, no smart quotes, dashes or
 * insert/delete, no inline predictions, no Writing Tools. The system keyboard learns from what is typed through
 * autocorrection and the suggestions bar; with these off it has nothing to learn from, and a third-party keyboard
 * that honours the traits gets the same signal. iOS has no single "do not learn" flag, and a keyboard may ignore any
 * of these (see SECURITY.md); a note editor cannot use the only stronger signal, secure text entry, which would make
 * the field single-line dots.
 *
 * Compose draws no text field of its own on iOS: every text input is a Kotlin subclass of `CMPTextInputView` (the
 * UITextInput view of Compose Multiplatform), whose traits Compose fills in from `KeyboardOptions`, and only partly.
 * The base class implements none of the trait getters and the subclasses override some of them, so a replacement on
 * the base class alone would never be reached. [install] therefore walks the whole class list and replaces the
 * getters on `CMPTextInputView` and on every class that descends from it, adding the ones a class lacks. The Kotlin
 * subclasses may be registered with the ObjC runtime only when first used, so [install] is idempotent and is run
 * again when the keyboard appears.
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
    private const val WRITING_TOOLS = "writingToolsBehavior"
    private const val NO = 1L

    /** `UIWritingToolsBehaviorNone`; its enum has no `.no` of 1 (that is `.complete`). */
    private const val WRITING_TOOLS_NONE = -1L
    private const val NSINTEGER_GETTER = "q@:"

    /** What [install] did: the classes it patched and the getter replacements it made in them. */
    data class Result(val classes: Int, val replaced: Int)

    /**
     * Makes `className` and every class descending from it answer "no" (1) for every one of [getters], and "none"
     * for `writingToolsBehavior` when [writingTools] is set. Safe to call more than once, as classes may appear
     * later. [getters] is [TRAITS] unless a test points it at a getter of a harmless class.
     */
    fun install(
        className: String = COMPOSE_INPUT_VIEW,
        getters: List<String> = TRAITS,
        writingTools: Boolean = true,
    ): Result {
        if (NSClassFromString(className) == null) return Result(0, 0)
        val answerNo = staticCFunction { _: COpaquePointer?, _: COpaquePointer? -> NO }
        val answerNone = staticCFunction { _: COpaquePointer?, _: COpaquePointer? -> WRITING_TOOLS_NONE }
        val classes = classesDescendingFrom(className)
        var replaced = 0
        classes.forEach { type ->
            getters.forEach { name ->
                replaceMethod(type, name, answerNo)
                replaced++
            }
            if (writingTools) {
                replaceMethod(type, WRITING_TOOLS, answerNone)
                replaced++
            }
        }
        return Result(classes.size, replaced)
    }

    /** What [install] must reach at least, once the Compose input views exist: the base class and a subclass. */
    fun expectedReplacements(getters: List<String> = TRAITS): Int = 2 * (getters.size + 1)

    /** Logs when [result] shows the input views were not (all) patched; the app goes on, the retry comes later. */
    fun report(result: Result) {
        if (result.replaced < expectedReplacements()) {
            NSLog(
                "Encly: keyboard privacy patched %d getters in %d classes, expected %d",
                result.replaced,
                result.classes,
                expectedReplacements(),
            )
        }
    }

    // The runtime calls go through dlsym with raw class pointers: the generated bindings wrap every class they
    // return into an Objective-C object holder (a retain), which crashes on the few root classes of the process
    // that implement no NSObject methods, and the walk touches every class there is.
    private val getName = runtimeFunction("class_getName")
        .reinterpret<CFunction<(COpaquePointer?) -> CPointer<ByteVar>?>>()
    private val getSuperclass = runtimeFunction("class_getSuperclass")
        .reinterpret<CFunction<(COpaquePointer?) -> COpaquePointer?>>()
    private val replace = runtimeFunction("class_replaceMethod")
        .reinterpret<CFunction<ReplaceMethod>>()

    private fun runtimeFunction(name: String): COpaquePointer = checkNotNull(dlsym(RTLD_DEFAULT, name)) { name }

    private fun replaceMethod(type: COpaquePointer, selector: String, answer: COpaquePointer) {
        memScoped { replace.invoke(type, sel_registerName(selector), answer, NSINTEGER_GETTER.cstr.ptr) }
    }

    private fun classesDescendingFrom(className: String): List<COpaquePointer> = memScoped {
        val count = alloc<UIntVar>()
        val all = objc_copyClassList(count.ptr)?.reinterpret<COpaquePointerVar>() ?: return@memScoped emptyList()
        val found = ArrayList<COpaquePointer>()
        for (i in 0 until count.value.toInt()) {
            val type = all[i] ?: continue
            if (descendsFrom(type, className)) found += type
        }
        free(all)
        found
    }

    private fun descendsFrom(type: COpaquePointer, className: String): Boolean {
        var current: COpaquePointer? = type
        while (current != null) {
            if (getName.invoke(current)?.toKString() == className) return true
            current = getSuperclass.invoke(current)
        }
        return false
    }
}
