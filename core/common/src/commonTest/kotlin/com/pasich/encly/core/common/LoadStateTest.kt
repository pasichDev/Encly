package com.pasich.encly.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LoadStateTest {
    private val error = LoadError(UiText.of(resource("title")), UiText.of(resource("message")))

    @Test
    fun loadingComesFirstThenEveryValue() = runTest {
        val states = flowOf(1, 2).asLoadState(error).toList()

        assertEquals(listOf(LoadState.Loading, LoadState.Ready(1), LoadState.Ready(2)), states)
    }

    @Test
    fun anUpstreamFailureBecomesTheTypedErrorNotAnEmptyValue() = runTest {
        val states = flow<List<Int>> { throw IllegalStateException("secret detail") }.asLoadState(error).toList()

        assertEquals(listOf(LoadState.Loading, LoadState.Failed(error)), states)
    }

    @Test
    fun aCollectorFailureIsNotTurnedIntoAnErrorState() = runTest {
        val thrown = runCatching {
            flowOf(1).asLoadState(error).collect { state ->
                if (state is LoadState.Ready) error("collector bug")
            }
        }.exceptionOrNull()

        assertEquals("collector bug", thrown?.message)
    }

    @Test
    fun cancellationIsNeverTurnedIntoAnErrorState() = runTest {
        val cancellation = CancellationException("stop")
        val thrown = runCatching {
            flow<Int> { throw cancellation }.asLoadState(error).toList()
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }

    @Test
    fun aReloadKeepsTheLoadedDataUntilTheNewDataArrives() {
        val ready = LoadState.Ready(listOf(1))

        assertSame(ready, ready.reloadWith(LoadState.Loading))
        assertEquals(LoadState.Ready(listOf(2)), ready.reloadWith(LoadState.Ready(listOf(2))))
        assertEquals(LoadState.Failed(error), ready.reloadWith(LoadState.Failed(error)))
        assertEquals(LoadState.Loading, LoadState.Failed(error).reloadWith(LoadState.Loading))
    }

    @Test
    fun suspendRunCatchingRethrowsCancellation() {
        val cancellation = CancellationException("stop")

        val thrown = runCatching { suspendRunCatching { throw cancellation } }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(true, suspendRunCatching { throw IllegalStateException() }.isFailure)
    }
}
