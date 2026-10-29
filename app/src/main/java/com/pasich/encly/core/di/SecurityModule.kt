package com.pasich.encly.core.di

import android.content.Context
import android.content.SharedPreferences
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.AutoLockPolicy
import com.pasich.encly.core.security.DeviceLockWatcher
import com.pasich.encly.core.security.KeystorePinFactor
import com.pasich.encly.core.security.LockoutClock
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.SystemDeviceLockWatcher
import com.pasich.encly.core.security.SystemLockoutClock
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.core.security.VaultStore
import com.pasich.encly.data.handoff.CacheHandoffStagingSweeper
import com.pasich.encly.data.handoff.HandoffStagingSweeper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File

@ContributesTo(AppScope::class)
@BindingContainer
object SecurityModule {

    /**
     * Key slots and the PIN lockout. A plain app-private file in `no_backup`: every secret in
     * it is already an AES-GCM envelope, and it depends on no Keystore key to be read (see
     * [VaultStore]). Construction touches no disk, so it can never fail at injection time.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideVaultStore(@ApplicationContext context: Context): VaultStore =
        VaultStore(File(context.noBackupFilesDir, VAULT_FILE))

    /**
     * Non-secret flags only (onboarding finished, last export time). Plain preferences: they
     * carry nothing that needs encryption, and a Keystore failure must never make them
     * unreadable at startup.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAppFlags(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(FLAGS_FILE, Context.MODE_PRIVATE)

    private const val VAULT_FILE = "vault_state_v3.bin"
    private const val FLAGS_FILE = "encly_flags_v3"
}

@ContributesTo(AppScope::class)
@BindingContainer
interface SecurityBindingsModule {
    @Binds
    fun bindPinHardwareFactor(factor: KeystorePinFactor): PinHardwareFactor

    @Binds
    fun bindLockoutClock(clock: SystemLockoutClock): LockoutClock

    @Binds
    fun bindDeviceLockWatcher(watcher: SystemDeviceLockWatcher): DeviceLockWatcher

    @Binds
    fun bindVaultLockEvents(manager: SessionLockManager): VaultLockEvents

    @Binds
    fun bindAutoLockPolicy(autoLock: AutoLock): AutoLockPolicy

    @Binds
    fun bindHandoffStagingSweeper(sweeper: CacheHandoffStagingSweeper): HandoffStagingSweeper
}
