package com.pasich.encly.desktop.di

import com.pasich.encly.core.di.ApplicationScope
import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.core.security.AutoLockPolicy
import com.pasich.encly.core.security.HandoffStagingSweeper
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.desktop.platform.ClipboardWiper
import com.pasich.encly.desktop.platform.DesktopAppLocaleController
import com.pasich.encly.desktop.platform.DesktopDocumentPickers
import com.pasich.encly.desktop.platform.DesktopEnvironment
import com.pasich.encly.desktop.platform.DesktopToaster
import com.pasich.encly.desktop.platform.DropRouter
import com.pasich.encly.desktop.security.DesktopAppLifecycle
import com.pasich.encly.desktop.security.DesktopDeviceLockWatcher
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.platform.AppInfo
import com.pasich.encly.platform.ExternalLinks
import com.pasich.encly.platform.NavigationRequests
import com.pasich.encly.platform.SensitiveClipboard
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineScope

/**
 * The one dependency graph of the desktop process: the counterpart of the Android `AppGraph`. The bindings of
 * the shared code (`@ContributesTo(AppScope)` and `@ContributesIntoMap` in `:shared` and the `:core` modules)
 * come in by themselves; [DesktopPlatformModule] and [DesktopBindingsModule] add what the platform provides.
 *
 * Unlike Android, the desktop creates every object itself, so the entry points are plain accessors: the
 * window host reads what it needs from here once.
 */
@DependencyGraph(AppScope::class)
interface DesktopAppGraph : ViewModelGraph {
    val securityManager: SecurityManager
    val sessionLockManager: SessionLockManager
    val settingsRepository: SettingsRepository
    val autoLockPolicy: AutoLockPolicy
    val handoffStagingSweeper: HandoffStagingSweeper
    val flags: KeyValueStore

    @get:ApplicationScope
    val applicationScope: CoroutineScope

    val environment: DesktopEnvironment
    val lifecycle: DesktopAppLifecycle
    val deviceLockWatcher: DesktopDeviceLockWatcher
    val dropRouter: DropRouter
    val navigationRequests: NavigationRequests
    val toaster: DesktopToaster
    val clipboardWiper: ClipboardWiper
    val locales: DesktopAppLocaleController
    val documentPickers: DesktopDocumentPickers
    val externalLinks: ExternalLinks
    val clipboard: SensitiveClipboard
    val appInfo: AppInfo

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides environment: DesktopEnvironment): DesktopAppGraph
    }
}
