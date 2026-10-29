package com.pasich.encly.desktop.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.pasich.encly.core.common.AppStrings
import com.pasich.encly.core.common.ComposeAppStrings
import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.DeviceLockWatcher
import com.pasich.encly.core.security.HandoffStagingSweeper
import com.pasich.encly.core.security.JvmVaultFileIo
import com.pasich.encly.core.security.LockoutClock
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.VaultStore
import com.pasich.encly.data.database.DatabasePlatform
import com.pasich.encly.data.database.DesktopDatabasePlatform
import com.pasich.encly.data.handoff.HandoffStaging
import com.pasich.encly.desktop.platform.ClipboardWiper
import com.pasich.encly.desktop.platform.DesktopAppInfo
import com.pasich.encly.desktop.platform.DesktopAppLocaleController
import com.pasich.encly.desktop.platform.DesktopCacheDirectory
import com.pasich.encly.desktop.platform.DesktopDeviceCapabilities
import com.pasich.encly.desktop.platform.DesktopDocumentPickers
import com.pasich.encly.desktop.platform.DesktopDocuments
import com.pasich.encly.desktop.platform.DesktopEnvironment
import com.pasich.encly.desktop.platform.DesktopExternalLinks
import com.pasich.encly.desktop.platform.DesktopSensitiveClipboard
import com.pasich.encly.desktop.platform.DesktopToaster
import com.pasich.encly.desktop.platform.DropRouter
import com.pasich.encly.desktop.platform.GatedFileDialogs
import com.pasich.encly.desktop.security.DesktopAppLifecycle
import com.pasich.encly.desktop.security.DesktopDeviceLockWatcher
import com.pasich.encly.desktop.security.NoBiometricManager
import com.pasich.encly.desktop.security.OsUptimeClock
import com.pasich.encly.desktop.storage.FileKeyValueStore
import com.pasich.encly.platform.AppInfo
import com.pasich.encly.platform.CacheDirectory
import com.pasich.encly.platform.ExternalLinks
import com.pasich.encly.platform.NavigationRequests
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.io.files.Path
import okio.Path.Companion.toPath

/** File names inside the data directory. The first two are the ones the Android app uses, by the same names. */
internal object DataFiles {
    const val VAULT_STATE = "vault_state_v3.bin"
    const val FLAGS = "encly_flags_v3.properties"
    const val SETTINGS = "my_notes_settings.preferences_pb"
    const val CACHE_DIR = "cache"
}

/**
 * Every binding the desktop makes for what `:app` binds on Android, in one place. The [DesktopEnvironment] is
 * the only input of the graph: with a temporary directory and fakes in it the whole graph runs in a test.
 *
 * | Android (`:app`)                          | Desktop                                            |
 * |-------------------------------------------|----------------------------------------------------|
 * | KeystorePinFactor                         | `environment.pinFactor` (`DesktopPinFactor`)       |
 * | KeystoreBiometricManager                  | [NoBiometricManager]                               |
 * | HandlerMainThreadTimer                    | `environment.timer` (Swing timer)                  |
 * | SystemLockoutClock                        | [OsUptimeClock]                                    |
 * | SystemDeviceLockWatcher                   | [DesktopDeviceLockWatcher]                         |
 * | VaultStore in `no_backup`                 | [VaultStore] in the private data directory         |
 * | SharedPreferences flags                   | [FileKeyValueStore]                                |
 * | AndroidDatabasePlatform                   | `DesktopDatabasePlatform` in the data directory    |
 * | preferencesDataStore                      | `PreferenceDataStoreFactory` file in the data dir  |
 * | AndroidDocuments, AndroidToaster, ...     | the `Desktop*` services of `platform`              |
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DesktopPlatformModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideVaultStore(environment: DesktopEnvironment): VaultStore =
        VaultStore(JvmVaultFileIo(environment.dataDirectory.resolve(DataFiles.VAULT_STATE).toFile()))

    @Provides
    @SingleIn(AppScope::class)
    fun provideFlags(environment: DesktopEnvironment): KeyValueStore =
        FileKeyValueStore(environment.dataDirectory.resolve(DataFiles.FLAGS))

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabasePlatform(environment: DesktopEnvironment): DatabasePlatform =
        DesktopDatabasePlatform(Path(environment.dataDirectory.toString()))

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsStore(environment: DesktopEnvironment): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { environment.dataDirectory.resolve(DataFiles.SETTINGS).toString().toPath() },
        )

    @Provides
    fun providePinHardwareFactor(environment: DesktopEnvironment): PinHardwareFactor = environment.pinFactor

    @Provides
    @SingleIn(AppScope::class)
    fun provideBiometricManager(): BiometricManager = NoBiometricManager()

    @Provides
    fun provideMainThreadTimer(environment: DesktopEnvironment): MainThreadTimer = environment.timer

    @Provides
    @SingleIn(AppScope::class)
    fun provideLockoutClock(environment: DesktopEnvironment): LockoutClock = OsUptimeClock(environment.bootClock)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDesktopDeviceLockWatcher(environment: DesktopEnvironment): DesktopDeviceLockWatcher =
        DesktopDeviceLockWatcher(environment.lockSignals)

    @Provides
    fun provideDeviceLockWatcher(watcher: DesktopDeviceLockWatcher): DeviceLockWatcher = watcher

    @Provides
    @SingleIn(AppScope::class)
    fun provideLifecycle(): DesktopAppLifecycle = DesktopAppLifecycle()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppStrings(): AppStrings = ComposeAppStrings()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHandoffStagingSweeper(cache: CacheDirectory): HandoffStagingSweeper =
        HandoffStagingSweeper { HandoffStaging.sweep(Path(cache.path, HandoffStaging.DIR_NAME)) }
}

/** The platform services of `:shared` (`com.pasich.encly.platform`) and the host's own window helpers. */
@ContributesTo(AppScope::class)
@BindingContainer
object DesktopServicesModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideNavigationRequests(): NavigationRequests = NavigationRequests()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDropRouter(navigation: NavigationRequests): DropRouter = DropRouter(navigation)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDocuments(): PlatformDocuments = DesktopDocuments()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDocumentPickers(
        environment: DesktopEnvironment,
        lifecycle: DesktopAppLifecycle,
        drops: DropRouter,
    ): DesktopDocumentPickers = DesktopDocumentPickers(GatedFileDialogs(environment.fileDialogs, lifecycle), drops)

    @Provides
    @SingleIn(AppScope::class)
    fun provideExternalLinks(environment: DesktopEnvironment): ExternalLinks =
        DesktopExternalLinks(environment.linkOpener)

    @Provides
    @SingleIn(AppScope::class)
    fun provideToaster(): DesktopToaster = DesktopToaster()

    @Provides
    fun provideToasterInterface(toaster: DesktopToaster): Toaster = toaster

    @Provides
    @SingleIn(AppScope::class)
    fun provideClipboardWiper(environment: DesktopEnvironment): ClipboardWiper =
        ClipboardWiper(environment.systemClipboard, environment.timer)

    @Provides
    @SingleIn(AppScope::class)
    fun provideClipboard(environment: DesktopEnvironment, wiper: ClipboardWiper): SensitiveClipboard =
        DesktopSensitiveClipboard(environment.systemClipboard, wiper)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocales(flags: KeyValueStore): DesktopAppLocaleController = DesktopAppLocaleController(flags)

    @Provides
    fun provideLocaleController(controller: DesktopAppLocaleController): com.pasich.encly.platform.AppLocaleController =
        controller

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppInfo(): AppInfo = DesktopAppInfo.fromBuild()

    @Provides
    @SingleIn(AppScope::class)
    fun provideCacheDirectory(environment: DesktopEnvironment): CacheDirectory =
        DesktopCacheDirectory(Path(environment.dataDirectory.resolve(DataFiles.CACHE_DIR).toString()))

    @Provides
    fun provideDeviceCapabilities(): DeviceCapabilities = DesktopDeviceCapabilities
}
