package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.VaultUnlockResult
import com.pasich.encly.testutil.FakePassphrasePolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** The lock screen's ViewModel where the secret is a passphrase: the same calls as for a PIN, a different kind. */
@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelPassphraseTest {
    private lateinit var security: SecurityManager
    private lateinit var sessionLock: SessionLockManager

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        sessionLock = SessionLockManager(security)
        sessionLock.onStart(mock(LifecycleOwner::class.java))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(withPolicy: Boolean) = if (withPolicy) {
        LockViewModel(security, sessionLock, CoroutineScope(Dispatchers.Default), FakePassphrasePolicy())
    } else {
        LockViewModel(security, sessionLock, CoroutineScope(Dispatchers.Default))
    }

    private suspend fun LockViewModel.check(typed: String): PinUnlockResult {
        val result = CompletableDeferred<PinUnlockResult>()
        authenticatePin(typed.toCharArray()) { result.complete(it) }
        return result.await()
    }

    @Test
    fun theKindIsThePolicysAndAPinWithoutOne() {
        assertEquals(SecretKind.PASSWORD, viewModel(withPolicy = true).secretKind)
        assertEquals(SecretKind.PIN, viewModel(withPolicy = false).secretKind)
    }

    @Test
    fun theRightPassphraseUnlocksThroughTheSameCallAsAPin() = runTest {
        `when`(security.unlockWithPin(FakePassphrasePolicy.GOOD.toCharArray())).thenReturn(VaultUnlockResult.SUCCESS)

        assertEquals(PinUnlockResult.SUCCESS, viewModel(withPolicy = true).check(FakePassphrasePolicy.GOOD))
        assertFalse(sessionLock.locked.value)
    }

    @Test
    fun aWrongPassphraseIsTheSameAnswerAsAWrongPin() = runTest {
        `when`(security.unlockWithPin("not the one at all".toCharArray()))
            .thenReturn(VaultUnlockResult.INVALID_CREDENTIAL)

        assertEquals(PinUnlockResult.WRONG_PIN, viewModel(withPolicy = true).check("not the one at all"))
    }
}
