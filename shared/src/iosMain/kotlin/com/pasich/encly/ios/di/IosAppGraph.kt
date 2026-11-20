package com.pasich.encly.ios.di

import com.pasich.encly.core.di.ApplicationScope
import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.AutoLockPolicy
import com.pasich.encly.core.security.HandoffStagingSweeper
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.backup.RoomVaultDataStore
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.ios.platform.IosAppLocaleController
import com.pasich.encly.ios.platform.IosClipboardPolicy
import com.pasich.encly.ios.platform.IosDocumentPickers
import com.pasich.encly.ios.platform.IosToaster
import com.pasich.encly.ios.platform.PickedCopies
import com.pasich.encly.ios.platform.SecurityScopes
import com.pasich.encly.ios.security.BackgroundLockGuard
import com.pasich.encly.ios.security.IosAppLifecycle
import com.pasich.encly.ios.security.IosBiometricTexts
import com.pasich.encly.ios.security.IosDeviceLockWatcher
import com.pasich.encly.ios.security.PrivacyShield
import com.pasich.encly.platform.AppInfo
import com.pasich.encly.platform.ExternalLinks
import com.pasich.encly.platform.PlatformDocuments
import com.pasich.encly.platform.SensitiveClipboard
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineScope

/**
 * The one dependency graph of the iOS process: the counterpart of the Android `AppGraph` and the desktop graph.
 * The bindings of the shared code (`@ContributesTo(AppScope)` and `@ContributesIntoMap` in `:shared` and the
 * `:core` modules) come in by themselves; [IosPlatformModule], [IosServicesModule] and [IosBindingsModule] add
 * what iOS provides.
 *
 * Like the desktop, iOS creates every object itself, so the entry points are plain accessors: the host reads what
 * it needs from here once.
 */
@DependencyGraph(AppScope::class)
internal interface IosAppGraph : ViewModelGraph {
    val securityManager: SecurityManager
    val sessionLockManager: SessionLockManager
    val settingsRepository: SettingsRepository
    val autoLockPolicy: AutoLockPolicy
    val handoffStagingSweeper: HandoffStagingSweeper
    val flags: KeyValueStore

    @get:ApplicationScope
    val applicationScope: CoroutineScope

    val environment: IosEnvironment
    val lifecycle: IosAppLifecycle
    val deviceLockWatcher: IosDeviceLockWatcher
    val shield: PrivacyShield
    val backgroundLockGuard: BackgroundLockGuard
    val biometricTexts: IosBiometricTexts
    val toaster: IosToaster
    val clipboardPolicy: IosClipboardPolicy
    val locales: IosAppLocaleController
    val externalLinks: ExternalLinks
    val clipboard: SensitiveClipboard
    val appInfo: AppInfo
    val documents: PlatformDocuments
    val pickers: IosDocumentPickers
    val scopes: SecurityScopes
    val pickedCopies: PickedCopies

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides environment: IosEnvironment): IosAppGraph
    }
}

/** The interface bindings `:app` declares in `DatabaseModule` and `SecurityBindingsModule`, for the iOS graph. */
@ContributesTo(AppScope::class)
@BindingContainer
internal interface IosBindingsModule {
    // SecureDatabaseManager is itself a singleton, so this binding is one instance too.
    @Binds
    fun bindDatabaseProvider(secureDatabaseManager: SecureDatabaseManager): DatabaseProvider

    @Binds
    fun bindVaultDataStore(store: RoomVaultDataStore): VaultDataStore

    @Binds
    fun bindVaultLockEvents(manager: SessionLockManager): VaultLockEvents

    @Binds
    fun bindAutoLockPolicy(autoLock: AutoLock): AutoLockPolicy
}

/** What the host gives the graph: the places and the Keychain-backed PIN factor. */
internal class IosEnvironment(val dataDirectory: String, val pinFactor: PinHardwareFactor)
