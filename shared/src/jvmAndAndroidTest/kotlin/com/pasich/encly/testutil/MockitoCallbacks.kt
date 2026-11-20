package com.pasich.encly.testutil

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.mockito.stubbing.Stubber

/** Registers an any() matcher for a callback parameter and hands Kotlin a no-op in its place. */
internal fun <T> anyCallback(): (T) -> Unit = ArgumentMatchers.any() ?: {}

/** Stubs a `(…, onResult: (T) -> Unit)` method to call its last argument with [value]. */
internal fun <T> answerCallback(value: T): Stubber = doAnswer { invocation ->
    @Suppress("UNCHECKED_CAST")
    (invocation.arguments.last() as (T) -> Unit)(value)
    null
}
