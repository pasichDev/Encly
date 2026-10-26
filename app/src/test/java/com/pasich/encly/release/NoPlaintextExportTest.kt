package com.pasich.encly.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plaintext never leaves the vault by a side door (issue #15): no share sheet, no FileProvider
 * handing out files, no notifications or alarms that could show or schedule note content, and no
 * clipboard write except through SensitiveClip, which marks the clip sensitive and clears it.
 * There are two ways out, both started by the user and written through the system document picker: an
 * encrypted backup, and a Markdown export, which is plaintext and therefore sits behind a warning and a
 * re-authentication (see SECURITY.md → Markdown export). Creating a document is allowed only in the
 * two files that host them.
 *
 * Scans every source and resource under app/src/main; comments are skipped, so a comment may
 * still name what is banned.
 */
class NoPlaintextExportTest {

    private val mainRoot = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    @Test
    fun nothingInTheAppExportsPlaintext() {
        val offenders = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension in SCANNED }
            .flatMap { file ->
                val path = file.relativeTo(mainRoot).invariantSeparatorsPath
                file.readLines().mapIndexedNotNull { index, line ->
                    if (isComment(line)) return@mapIndexedNotNull null
                    BANNED.filter { (_, rule) -> rule.matches(path, line) }
                        .map { (name, _) -> "$path:${index + 1} ($name)" }
                        .takeIf { it.isNotEmpty() }
                }.flatten()
            }
            .toList()
        assertTrue(
            "Plaintext must not leave the app except as an encrypted backup or the warned, re-authenticated " +
                "Markdown export (see SECURITY.md): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun patternsCatchTheSideDoorsButNotTheirNeighbours() {
        val caught = listOf(
            "share sheet" to "Intent(Intent.ACTION_SEND).apply {",
            "share sheet" to "val i = Intent(ACTION_SEND_MULTIPLE)",
            "share sheet" to """<action android:name="android.intent.action.SEND" />""",
            "FileProvider" to "FileProvider.getUriForFile(context, authority, file)",
            "notification" to "NotificationCompat.Builder(context, CHANNEL)",
            "notification" to """<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />""",
            "alarm" to "context.getSystemService(AlarmManager::class.java)",
            "clipboard write" to "clipboard.setPrimaryClip(ClipData.newPlainText(\"\", text))",
            "clipboard write" to "clipboard.setClipEntry(entry)",
            "clipboard write" to "val clipboard = LocalClipboardManager.current",
            "document creation" to "ActivityResultContracts.CreateDocument(\"text/plain\")",
            "document creation" to "Intent(Intent.ACTION_CREATE_DOCUMENT)",
        )
        caught.forEach { (name, line) ->
            assertTrue("$name: $line", BANNED.getValue(name).matches(OTHER_FILE, line))
        }

        // The support e-mail link (a mailto: ACTION_SENDTO) and SensitiveClip's own writes.
        listOf(
            OTHER_FILE to "val intent = Intent(Intent.ACTION_SENDTO).apply {",
            SENSITIVE_CLIP to "clipboard.setPrimaryClip(clip)",
            SENSITIVE_CLIP to "delegate.setClipEntry(clipEntry)",
            BACKUP_SCREEN to "ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE),",
            MARKDOWN_HOST to "ActivityResultContracts.CreateDocument(MarkdownExport.MIME_NOTE),",
        ).forEach { (path, line) ->
            assertFalse("$path: $line", BANNED.values.any { it.matches(path, line) })
        }
        assertTrue(isComment("    // no FileProvider: nothing is shared"))
        assertTrue(isComment("<!-- no POST_NOTIFICATIONS -->"))
    }

    private class Rule(private val pattern: Regex, private val allowedIn: Set<String> = emptySet()) {
        fun matches(path: String, line: String): Boolean =
            pattern.containsMatchIn(line) && allowedIn.none { path.endsWith(it) }
    }

    private companion object {
        val SCANNED = setOf("kt", "java", "xml")
        const val SENSITIVE_CLIP = "java/com/pasich/encly/presentation/components/SensitiveClip.kt"
        const val BACKUP_SCREEN = "java/com/pasich/encly/presentation/screen/backup/BackupScreen.kt"
        const val MARKDOWN_HOST = "java/com/pasich/encly/presentation/screen/backup/MarkdownExportHost.kt"
        const val OTHER_FILE = "java/com/pasich/encly/presentation/screen/SomeScreen.kt"

        val BANNED = mapOf(
            // ACTION_SEND and ACTION_SEND_MULTIPLE, in code or an intent filter; not ACTION_SENDTO.
            "share sheet" to Rule(Regex("""ACTION_SEND(?!TO)\w*|android\.intent\.action\.SEND(?!TO)\w*""")),
            "FileProvider" to Rule(Regex("""\bFileProvider\b""")),
            "notification" to Rule(Regex("""\bNotificationCompat\b|\bPOST_NOTIFICATIONS\b""")),
            "alarm" to Rule(Regex("""\bAlarmManager\b""")),
            "clipboard write" to Rule(
                Regex("""\bsetPrimaryClip\b|\bsetClipEntry\b|\bLocalClipboardManager\b"""),
                allowedIn = setOf(SENSITIVE_CLIP),
            ),
            // Creating a document is how a file leaves the app: only the two export hosts may.
            "document creation" to Rule(
                Regex("""\bCreateDocument\b|ACTION_CREATE_DOCUMENT"""),
                allowedIn = setOf(BACKUP_SCREEN, MARKDOWN_HOST),
            ),
        )

        fun isComment(line: String): Boolean {
            val code = line.trimStart()
            return code.startsWith("//") || code.startsWith("*") || code.startsWith("/*") ||
                code.startsWith("<!--")
        }
    }
}
