package com.pasich.encly.core.keystore

/**
 * Picks the backend for the operating system named by `os.name`. The factories are lazy so that only the
 * chosen backend's native libraries are loaded.
 */
internal fun selectSecretStore(
    osName: String,
    windows: () -> SecretStore,
    macos: () -> SecretStore,
    linux: () -> SecretStore,
): SecretStore {
    val os = osName.lowercase()
    return when {
        os.startsWith("windows") -> windows()
        os.startsWith("mac") || os.startsWith("darwin") -> macos()
        os.contains("linux") -> linux()
        else -> throw SecretStoreUnavailableException("No supported secret store on this operating system")
    }
}
