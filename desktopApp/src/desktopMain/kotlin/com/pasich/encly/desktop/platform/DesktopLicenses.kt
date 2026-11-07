package com.pasich.encly.desktop.platform

import com.pasich.encly.platform.OpenSourceNotice

/**
 * The libraries only the desktop app ships, for About -> Licenses (the ones every build has are listed there already).
 * Their licence texts and notices are in the installers, under `licenses/` in the app resources (see
 * `packaging/resources/common/licenses`); keep the two in step when a dependency changes.
 */
object DesktopLicenses {
    private const val APACHE_2 = "Apache License 2.0"
    private const val MIT = "MIT License"

    val notices: List<OpenSourceNotice> = listOf(
        OpenSourceNotice("Apache PDFBox, FontBox", APACHE_2, "https://pdfbox.apache.org/"),
        OpenSourceNotice(
            "Apache Commons Logging",
            APACHE_2,
            "https://commons.apache.org/proper/commons-logging/",
        ),
        OpenSourceNotice(
            "JNA (Java Native Access)",
            "Apache License 2.0 or LGPL 2.1 or later",
            "https://github.com/java-native-access/jna",
        ),
        OpenSourceNotice("dbus-java", MIT, "https://github.com/hypfvieh/dbus-java"),
        OpenSourceNotice("SLF4J", MIT, "https://www.slf4j.org/"),
        OpenSourceNotice("Skiko", APACHE_2, "https://github.com/JetBrains/skiko"),
        OpenSourceNotice("JetBrains Runtime API, JSpecify", APACHE_2, "https://jspecify.dev/"),
        OpenSourceNotice("OpenJDK", "GPL 2 with the Classpath Exception", "https://openjdk.org/legal/gplv2+ce.html"),
    )
}
