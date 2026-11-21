package com.pasich.encly.testutil

import com.pasich.encly.core.backup.BackupFlags
import com.pasich.encly.core.security.AuthenticationManager
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.DuressPinChange
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SeedPhraseManager
import com.pasich.encly.core.security.VaultDatabase
import com.pasich.encly.core.security.VaultKind
import com.pasich.encly.core.security.VaultStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.mockito.Mockito.mock

/**
 * A [VaultDatabase] that remembers what a real one would: which file exists, which one is open and
 * under which key. [failNextCreate] leaves the file behind and fails, like a creation that stopped
 * half way; [beforeWipeIfClosed] runs first in [wipeIfClosed] (a place to "kill the process").
 */
internal class FakeVaultDatabase : VaultDatabase {
    class Open(val vault: VaultKind, val allowCreate: Boolean, val key: List<Byte>)

    val files = mutableSetOf<VaultKind>()
    var open: VaultKind? = null
        private set
    private var openKey: List<Byte>? = null
    val opens = mutableListOf<Open>()
    var failNextCreate = false
    var beforeWipeIfClosed: (() -> Unit)? = null

    override fun isDatabaseUnlocked(): Boolean = open != null

    override fun hasEncryptedDatabase(vault: VaultKind): Boolean = vault in files

    override fun unlockDatabase(dek: ByteArray, allowCreate: Boolean): Boolean =
        unlockDatabase(dek, allowCreate, VaultKind.PRIMARY)

    override fun unlockDatabase(dek: ByteArray, allowCreate: Boolean, vault: VaultKind): Boolean {
        val key = dek.toList()
        opens += Open(vault, allowCreate, key)
        if (open != null) return open == vault && openKey == key
        if (vault !in files) {
            if (!allowCreate) return false
            files += vault
            if (failNextCreate) {
                failNextCreate = false
                return false
            }
        }
        open = vault
        openKey = key
        return true
    }

    override fun reset() {
        open = null
        openKey = null
    }

    override fun wipe() {
        reset()
        files.clear()
    }

    override fun wipeIfClosed(only: VaultKind?): Boolean {
        beforeWipeIfClosed?.invoke()
        if (open != null && (only == null || open == only)) return false
        if (only == null) files.clear() else files -= only
        return true
    }
}

/**
 * A committed vault (a recovery phrase, the PIN [PIN], onboarding done) over a real state file, a
 * fake database and a fake Keystore, and "processes" over it: [restart] builds a new
 * [SecurityManager] over the same file, flags and Keystore, with nothing in memory.
 */
internal class DuressVaultFixture(withDuressPin: Boolean = false) {
    val file = tempVaultFile()
    val factor = FakePinFactor()
    val clock = FakeLockoutClock()
    val prefs = InMemoryKeyValueStore()
    val db = FakeVaultDatabase()
    val bootstrap: ByteArray
    var store: VaultStore = VaultStore(file)
        private set
    var security: SecurityManager
        private set

    init {
        val seed = SeedPhraseManager(store)
        val auth = AuthenticationManager(store, factor, clock)
        assertTrue(seed.initializeVault(seed.generateMnemonic()))
        bootstrap = seed.copyBootstrapKey()!!
        assertTrue(auth.configurePin(PIN.toCharArray(), bootstrap))
        if (withDuressPin) {
            assertEquals(
                DuressPinChange.SET_NEW_VAULT,
                auth.configureDuressPin(DURESS_PIN.toCharArray(), bootstrap) { true },
            )
        }
        db.files += VaultKind.PRIMARY
        prefs.commit {
            putBoolean("onboarding_shown_v3", true)
            putLong(BackupFlags.LAST_EXPORT_KEY, 1L)
        }
        security = newSecurity(store)
    }

    /** A new process: the memory is gone, the state file, the flags, the Keystore and the files stay. */
    fun restart(): SecurityManager {
        store = VaultStore(file)
        security = newSecurity(store)
        return security
    }

    private fun newSecurity(vaultStore: VaultStore): SecurityManager = SecurityManager(
        prefs,
        vaultStore,
        SeedPhraseManager(vaultStore),
        db,
        AuthenticationManager(vaultStore, factor, clock),
        mock(BiometricManager::class.java),
    ) {}

    companion object {
        const val PIN = "135790"
        const val DURESS_PIN = "975310"
        const val OTHER_DURESS_PIN = "112358"
    }
}
