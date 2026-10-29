package com.pasich.encly.desktop.storage

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

/** The desktop operating systems Encly runs on. */
enum class DesktopOs {
    WINDOWS,
    MACOS,
    LINUX,
    ;

    companion object {
        /** The OS named by the `os.name` property; Linux for any other unix, since the layout is the XDG one. */
        fun fromName(osName: String): DesktopOs {
            val name = osName.lowercase()
            return when {
                name.startsWith("windows") -> WINDOWS
                name.startsWith("mac") || name.startsWith("darwin") -> MACOS
                else -> LINUX
            }
        }

        fun current(): DesktopOs = fromName(System.getProperty("os.name").orEmpty())
    }
}

/**
 * Where the app keeps everything that is its own: the encrypted databases, the key slots file, the settings,
 * the window state and the cache. One directory per OS user, in the place the OS reserves for it.
 */
object AppDirectories {
    private const val WINDOWS_NAME = "Encly"
    private const val MACOS_NAME = "Encly"
    private const val LINUX_NAME = "encly"

    /**
     * The data directory for [os]. Pure: [env] answers environment variables and [home] is the user's home.
     * - Windows: `%APPDATA%\Encly` (the same folder the DPAPI blob of the PIN factor lives in);
     * - macOS: `~/Library/Application Support/Encly`;
     * - Linux: `$XDG_DATA_HOME/encly`, or `~/.local/share/encly` when it is unset, empty or not absolute (the
     *   XDG specification says a relative value is invalid and must be ignored).
     */
    fun dataDirectory(os: DesktopOs, env: (String) -> String?, home: String): Path = when (os) {
        DesktopOs.WINDOWS -> {
            val appData = env("APPDATA")?.takeIf { it.isNotBlank() }
            (appData?.let(Paths::get) ?: Paths.get(home, "AppData", "Roaming")).resolve(WINDOWS_NAME)
        }

        DesktopOs.MACOS -> Paths.get(home, "Library", "Application Support", MACOS_NAME)

        DesktopOs.LINUX -> {
            val xdg = env("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(Paths::get)?.takeIf { it.isAbsolute }
            (xdg ?: Paths.get(home, ".local", "share")).resolve(LINUX_NAME)
        }
    }

    /** [dataDirectory] for the running JVM. */
    fun systemDataDirectory(): Path =
        dataDirectory(DesktopOs.current(), System::getenv, System.getProperty("user.home"))

    /**
     * Creates [dir] (and its parents) for the owner only and returns it.
     *
     * On POSIX systems (macOS, Linux) the directory gets `rwx------`, also when it already existed with looser
     * permissions: the key slots file and the databases inside carry no permission bits of their own that
     * matter, the directory is the boundary. On Windows POSIX permissions do not exist and the ACL is left to
     * inheritance: a folder under `%APPDATA%` is readable by the user, SYSTEM and Administrators only, which
     * is the same trust boundary the DPAPI-protected PIN secret has. Encly does not rewrite ACLs (a wrong
     * ACL can lock the user out of their own vault).
     *
     * @throws IOException when the directory cannot be created or restricted.
     */
    @Throws(IOException::class)
    fun createPrivate(dir: Path): Path {
        val posix = Files.getFileStore(existingParent(dir)).supportsFileAttributeView("posix")
        if (posix) {
            val ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
            Files.createDirectories(dir, ownerOnly)
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"))
        } else {
            Files.createDirectories(dir)
        }
        return dir
    }

    /** The nearest ancestor of [dir] that exists, to ask its file store what it supports. */
    private fun existingParent(dir: Path): Path {
        var candidate: Path? = dir.toAbsolutePath()
        while (candidate != null && !Files.exists(candidate)) candidate = candidate.parent
        return candidate ?: dir.toAbsolutePath().root
    }
}
