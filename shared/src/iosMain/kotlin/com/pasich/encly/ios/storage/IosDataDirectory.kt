@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pasich.encly.ios.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.IOException
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * The app's private data directory: `Application Support/Encly` inside the sandbox. It holds everything Encly
 * stores (the vault state, the database, the flags, the settings, the cache).
 *
 * It follows the rules of the Android app, which excludes all of its data from backups and device transfers:
 * - the directory is excluded from iCloud and iTunes/Finder backups (`isExcludedFromBackup`), so nothing of the
 *   vault is ever restored onto another device. The PIN's device secret is in a this-device-only Keychain item in
 *   any case, so a restored copy would be unreadable;
 * - the directory has the data protection class `NSFileProtectionComplete`, and so do the files created in it: they
 *   cannot be opened while the device is locked. The vault locks when the screen turns off, before that matters.
 */
internal object IosDataDirectory {
    private const val NAME = "Encly"

    /** The directory's path; created, protected and excluded from backups on every call (idempotent). */
    @Suppress("ThrowsCount") // Each step of the setup fails in its own way.
    fun prepare(): String {
        val files = NSFileManager.defaultManager
        val support = (files.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).firstOrNull() as? NSURL)
            ?: throw IOException("no Application Support directory")
        val directory = support.URLByAppendingPathComponent(NAME, isDirectory = true)
            ?: throw IOException("no data directory")
        val path = directory.path ?: throw IOException("no data directory path")
        val protection = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete)
        if (!files.createDirectoryAtPath(path, true, protection, null)) throw IOException("cannot create $path")
        // A directory that existed before keeps its old class otherwise; the files in it are fixed up as well.
        files.setAttributes(protection, ofItemAtPath = path, error = null)
        protectExisting(files, path, protection)
        if (!directory.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)) {
            throw IOException("cannot exclude $path from backups")
        }
        return path
    }

    /** Files from before the directory was protected (or put there by a library) get the class too. */
    private fun protectExisting(files: NSFileManager, path: String, protection: Map<Any?, Any?>) {
        val names = files.contentsOfDirectoryAtPath(path, null) ?: return
        for (name in names) {
            val child = "$path/$name"
            files.setAttributes(protection, ofItemAtPath = child, error = null)
            // One level is enough: the database and the vault file are direct children.
        }
    }
}
