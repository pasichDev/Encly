package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.AuthenticationManager
import com.pasich.encly.core.security.JvmVaultFileIo
import com.pasich.encly.core.security.LockoutClock
import com.pasich.encly.core.security.UnlockSecretPolicy
import com.pasich.encly.core.security.VaultStore
import java.nio.file.Files
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A [LockoutClock] the test moves by hand. */
internal class ManualClock(var elapsed: Long = 1_000_000L) : LockoutClock {
    override fun elapsedRealtime(): Long = elapsed

    override fun bootCount(): Int = 1
}

/** An HMAC in the place of Argon2id: instant, deterministic, and different for every password and salt. */
internal val cheapEngine: Argon2Function = { password, salt, _, _, _ ->
    Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(salt, "HmacSHA256"))
        doFinal(password)
    }
}

/**
 * An [AuthenticationManager] under [policy] over a vault file in a temp directory and the real [DesktopPinFactor] on
 * an [InMemorySecretStore]: everything but the OS secret store is as on the desktop.
 */
internal class PolicyFixture(policy: UnlockSecretPolicy) {
    val store = VaultStore(JvmVaultFileIo(Files.createTempDirectory("vault").toFile().resolve("vault_state.bin")))
    val secrets = InMemorySecretStore()
    val clock = ManualClock()
    val auth = AuthenticationManager(store, DesktopPinFactor(secrets), clock, policy)
}
