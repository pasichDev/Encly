package com.pasich.encly.ios.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.pasich.encly.core.common.AppStrings
import com.pasich.encly.core.common.ComposeAppStrings
import com.pasich.encly.core.keystore.BiometricKeychain
import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.DeviceLockWatcher
import com.pasich.encly.core.security.HandoffStagingSweeper
import com.pasich.encly.core.security.IosVaultFileIo
import com.pasich.encly.core.security.LockoutClock
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.VaultStore
import com.pasich.encly.data.database.DatabasePlatform
import com.pasich.encly.data.database.IosDatabasePlatform
import com.pasich.encly.data.handoff.HandoffStaging
import com.pasich.encly.ios.platform.IosAppInfo
import com.pasich.encly.ios.platform.IosAppLocaleController
import com.pasich.encly.ios.platform.IosCacheDirectory
import com.pasich.encly.ios.platform.IosClipboardPolicy
import com.pasich.encly.ios.platform.IosDeviceCapabilities
import com.pasich.encly.ios.platform.IosDocumentPickers
import com.pasich.encly.ios.platform.IosDocuments
import com.pasich.encly.ios.platform.IosExternalLinks
import com.pasich.encly.ios.platform.IosSensitiveClipboard
import com.pasich.encly.ios.platform.IosToaster
import com.pasich.encly.ios.platform.PickedCopies
import com.pasich.encly.ios.platform.SecurityScopes
import com.pasich.encly.ios.security.BackgroundLockGuard
import com.pasich.encly.ios.security.IosAppLifecycle
import com.pasich.encly.ios.security.IosBiometricManager
import com.pasich.encly.ios.security.IosBiometricTexts
import com.pasich.encly.ios.security.IosDeviceLockWatcher
import com.pasich.encly.ios.security.IosLockoutClock
import com.pasich.encly.ios.security.IosMainThreadTimer
import com.pasich.encly.ios.security.PrivacyShield
import com.pasich.encly.ios.storage.IosKeyValueStore
import com.pasich.encly.platform.AppInfo
import com.pasich.encly.platform.AppLocaleController
import com.pasich.encly.platform.CacheDirectory
import com.pasich.encly.platform.ExternalLinks
import com.pasich.encly.platform.PlatformDocuments
import com.pasich.encly.platform.SensitiveClipboard
import com.pasich.encly.platform.Toaster
import com.pasich.encly.utils.DeviceCapabilities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.io.files.Path
import okio.Path.Companion.toPath

/** File names inside the data directory. The first three are the ones the Android app uses, by the same names. */
internal object DataFiles {
    const val VAULT_STATE = "vault_state_v3.bin"
    const val FLAGS = "encly_flags_v3.json"
    const val SETTINGS = "my_notes_settings.preferences_pb"
    const val CACHE_DIR = "cache"
    const val PICKED_DIR = "picked"
    const val EXPORT_DIR = "export"
}

/**
 * Every binding iOS makes for what `:app` binds on Android, in one place. The [IosEnvironment] is the only input of
 * the graph.
 *
 * | Android (`:app`)                          | iOS                                                   |
 * |-------------------------------------------|-------------------------------------------------------|
 * | KeystorePinFactor                         | `environment.pinFactor` (`IosPinFactor`, the Keychain)|
 * | KeystoreBiometricManager                  | [IosBiometricManager] (Keychain + biometryCurrentSet) |
 * | HandlerMainThreadTimer                    | [IosMainThreadTimer]                                  |
 * | SystemLockoutClock                        | [IosLockoutClock] (continuous clock, boot session)    |
 * | SystemDeviceLockWatcher                   | [IosDeviceLockWatcher] (protected data)               |
 * | VaultStore in `no_backup`                 | [VaultStore] in the data dir, excluded from backups   |
 * | SharedPreferences flags                   | [IosKeyValueStore]                                    |
 * | AndroidDatabasePlatform                   | `IosDatabasePlatform` in the data directory           |
 * | preferencesDataStore                      | `PreferenceDataStoreFactory` file in the data dir     |
 * | AndroidDocuments, AndroidToaster, ...     | the `Ios*` services of `ios.platform`                 |
 */
@ContributesTo(AppScope::class)
@BindingContainer
@Suppress("TooManyFunctions") // One binding per platform service.
internal object IosPlatformModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideVaultStore(environment: IosEnvironment): VaultStore =
        VaultStore(IosVaultFileIo("${environment.dataDirectory}/${DataFiles.VAULT_STATE}"))

    @Provides
    @SingleIn(AppScope::class)
    fun provideFlags(environment: IosEnvironment): KeyValueStore =
        IosKeyValueStore("${environment.dataDirectory}/${DataFiles.FLAGS}")

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabasePlatform(environment: IosEnvironment): DatabasePlatform =
        IosDatabasePlatform(Path(environment.dataDirectory))

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsStore(environment: IosEnvironment): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { "${environment.dataDirectory}/${DataFiles.SETTINGS}".toPath() },
        )

    @Provides
    fun providePinHardwareFactor(environment: IosEnvironment): PinHardwareFactor = environment.pinFactor

    @Provides
    @SingleIn(AppScope::class)
    fun provideBiometricTexts(): IosBiometricTexts = IosBiometricTexts()

    @Provides
    @SingleIn(AppScope::class)
    fun provideBiometricManager(store: VaultStore, texts: IosBiometricTexts): BiometricManager =
        IosBiometricManager(store, BiometricKeychain(), texts)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMainThreadTimer(): MainThreadTimer = IosMainThreadTimer()

    @Provides
    @SingleIn(AppScope::class)
    fun provideLockoutClock(): LockoutClock = IosLockoutClock()

    @Provides
    @SingleIn(AppScope::class)
    fun provideIosDeviceLockWatcher(): IosDeviceLockWatcher = IosDeviceLockWatcher()

    @Provides
    fun provideDeviceLockWatcher(watcher: IosDeviceLockWatcher): DeviceLockWatcher = watcher

    @Provides
    @SingleIn(AppScope::class)
    fun provideLifecycle(watcher: IosDeviceLockWatcher): IosAppLifecycle =
        IosAppLifecycle(onForeground = watcher::appReturned)

    @Provides
    @SingleIn(AppScope::class)
    fun provideShield(): PrivacyShield = PrivacyShield()

    @Provides
    @SingleIn(AppScope::class)
    fun provideBackgroundLockGuard(security: SecurityManager, locks: SessionLockManager): BackgroundLockGuard =
        BackgroundLockGuard(isVaultOpen = security::isDatabaseUnlocked, lockNow = locks::lockNow)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppStrings(): AppStrings = ComposeAppStrings()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHandoffStagingSweeper(cache: CacheDirectory): HandoffStagingSweeper =
        HandoffStagingSweeper { HandoffStaging.sweep(Path(cache.path, HandoffStaging.DIR_NAME)) }
}

/** The platform services of `:shared` (`com.pasich.encly.platform`) and the host's own helpers. */
@ContributesTo(AppScope::class)
@BindingContainer
@Suppress("TooManyFunctions") // One binding per platform service.
internal object IosServicesModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSecurityScopes(): SecurityScopes = SecurityScopes()

    @Provides
    @SingleIn(AppScope::class)
    fun providePickedCopies(cache: CacheDirectory): PickedCopies = PickedCopies(Path(cache.path, DataFiles.PICKED_DIR))

    @Provides
    @SingleIn(AppScope::class)
    fun provideDocuments(): PlatformDocuments = IosDocuments()

    @Provides
    @SingleIn(AppScope::class)
    fun providePickers(scopes: SecurityScopes, copies: PickedCopies, cache: CacheDirectory): IosDocumentPickers =
        IosDocumentPickers(scopes, copies, Path(cache.path, DataFiles.EXPORT_DIR))

    @Provides
    @SingleIn(AppScope::class)
    fun provideExternalLinks(): ExternalLinks = IosExternalLinks()

    @Provides
    @SingleIn(AppScope::class)
    fun provideToaster(): IosToaster = IosToaster()

    @Provides
    fun provideToasterInterface(toaster: IosToaster): Toaster = toaster

    @Provides
    @SingleIn(AppScope::class)
    fun provideClipboardPolicy(timer: MainThreadTimer): IosClipboardPolicy = IosClipboardPolicy(timer)

    @Provides
    @SingleIn(AppScope::class)
    fun provideClipboard(policy: IosClipboardPolicy): SensitiveClipboard = IosSensitiveClipboard(policy)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocales(flags: KeyValueStore): IosAppLocaleController = IosAppLocaleController(flags)

    @Provides
    fun provideLocaleController(controller: IosAppLocaleController): AppLocaleController = controller

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppInfo(): AppInfo = IosAppInfo()

    @Provides
    @SingleIn(AppScope::class)
    fun provideCacheDirectory(environment: IosEnvironment): CacheDirectory =
        IosCacheDirectory(Path(environment.dataDirectory, DataFiles.CACHE_DIR))

    @Provides
    fun provideDeviceCapabilities(): DeviceCapabilities = IosDeviceCapabilities
}
