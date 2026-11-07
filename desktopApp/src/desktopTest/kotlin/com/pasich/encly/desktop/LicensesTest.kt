package com.pasich.encly.desktop

import com.pasich.encly.desktop.platform.DesktopLicenses
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/** The desktop-only libraries are listed in the app and their texts are in the installers. */
class LicensesTest {
    private val folder: Path = Paths.get("packaging", "resources", "common", "licenses")

    @Test
    fun `every desktop only library is on the licences screen`() {
        val names = DesktopLicenses.notices.map { it.name }
        for (library in listOf("PDFBox", "Commons Logging", "JNA", "dbus-java", "SLF4J")) {
            assertTrue(names.any { library in it }, "$library is not listed: $names")
        }
        assertTrue(DesktopLicenses.notices.all { it.license.isNotBlank() && it.url.startsWith("https://") })
    }

    @Test
    fun `the licence texts and notices of those libraries ship with the installers`() {
        val expected = listOf(
            "THIRD-PARTY-NOTICES.txt",
            "pdfbox-LICENSE.txt",
            "pdfbox-NOTICE.txt",
            "commons-logging-LICENSE.txt",
            "commons-logging-NOTICE.txt",
            "jna-LICENSE.txt",
            "jna-AL2.0.txt",
            "jna-LGPL2.1.txt",
            "dbus-java-LICENSE.txt",
            "slf4j-LICENSE.txt",
        )
        for (name in expected) {
            val file = folder.resolve(name)
            assertTrue(Files.isRegularFile(file) && Files.size(file) > 0, "$file is missing or empty")
        }
        // The index names every file, so a text cannot be added without being mentioned.
        val index = Files.readString(folder.resolve("THIRD-PARTY-NOTICES.txt"))
        for (name in expected.drop(1)) {
            assertTrue(name in index, "$name is not in THIRD-PARTY-NOTICES.txt")
        }
    }
}
