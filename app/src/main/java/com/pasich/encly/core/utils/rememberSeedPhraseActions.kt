package com.pasich.encly.core.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SeedPhraseActions(
    private val context: Context,
    private val onFileSaveSuccess: () -> Unit,
    private val onFileSaveError: (Exception) -> Unit,
    private val onGoogleDriveSuccess: () -> Unit,
    private val onGoogleDriveError: (Exception) -> Unit,
    private val onCopySuccess: () -> Unit
) {
    /**
     * Copies the seed phrase to the clipboard.
     */
    fun copyToClipboard(seedPhrase: String) {
        val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
        val clip = ClipData.newPlainText("Seed-phase", seedPhrase)
        clipboard?.setPrimaryClip(clip)
        onCopySuccess()
    }


    /**
     * Save the seed phrase to a file using the Storage Access Framework (system file picker)
     */
    fun saveToFileWithPicker(
        launcher: ActivityResultLauncher<String>, onError: (Exception) -> Unit = onFileSaveError
    ) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault())
            val currentTime = dateFormat.format(Date())
            val fileName = "encly_${currentTime}.txt"
            launcher.launch(fileName)
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Write the seed phrase to the given Uri (from SAF)
     */
    fun writeSeedToUri(
        uri: Uri?,
        seedPhrase: String,
        onSuccess: () -> Unit = onFileSaveSuccess,
        onError: (Exception) -> Unit = onFileSaveError
    ) {
        if (uri == null) return
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                java.io.OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    val content = buildString {
                        appendLine("My Notes - Seed Phrase Backup")
                        appendLine("------------------------------")
                        appendLine()
                        appendLine("This is your recovery seed phrase for My Notes app.")
                        appendLine("Do NOT share it with anyone!")
                        appendLine()
                        appendLine(
                            "Generated: ${
                                SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
                                ).format(Date())
                            }"
                        )
                        appendLine()
                        appendLine("Seed Phrase:")
                        appendLine(seedPhrase)
                        appendLine()
                        appendLine("IMPORTANT:")
                        appendLine("• Store this phrase in a safe place.")
                        appendLine("• Never share it with anyone.")
                        appendLine("• Losing this phrase means losing access to all your encrypted notes.")
                    }
                    writer.write(content)
                    writer.flush()
                }
            }
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Save the seed phrase to Google Drive via intent
     */
    fun saveToGoogleDrive(
        seedPhrase: String,
        onSuccess: () -> Unit = onGoogleDriveSuccess,
        onError: (Exception) -> Unit = onGoogleDriveError
    ) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault())
            val currentTime = dateFormat.format(Date())
            val fileName = "encly_${currentTime}.txt"
            val content = buildString {
                appendLine("My Notes - Seed Phrase Backup")
                appendLine("------------------------------")
                appendLine()
                appendLine("This is your recovery seed phrase for My Notes app.")
                appendLine("Do NOT share it with anyone!")
                appendLine()
                appendLine(
                    "Generated: ${
                        SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
                        ).format(Date())
                    }"
                )
                appendLine()
                appendLine("Seed Phrase:")
                appendLine(seedPhrase)
                appendLine()
                appendLine("IMPORTANT:")
                appendLine("• Store this phrase in a safe place.")
                appendLine("• Never share it with anyone.")
                appendLine("• Losing this phrase means losing access to all your encrypted notes.")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                setPackage("com.google.android.apps.docs")
            }
            context.startActivity(intent)
            onSuccess()
        } catch (_: Exception) {
            try {
                // Fallback: generic share intent
                val content = buildString {
                    appendLine("🔐 My Notes - Seed Phrase Backup")
                    appendLine("═══════════════════════════════")
                    appendLine()
                    appendLine("This is your recovery seed phrase for My Notes app.")
                    appendLine("Do NOT share it with anyone!")
                    appendLine()
                    appendLine(
                        "Generated: ${
                            SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
                            ).format(Date())
                        }"
                    )
                    appendLine()
                    appendLine("Seed Phrase:")
                    appendLine(seedPhrase)
                    appendLine()
                    appendLine("⚠️  IMPORTANT:")
                    appendLine("• Store this phrase in a safe place.")
                    appendLine("• Never share it with anyone.")
                    appendLine("• Losing this phrase means losing access to all your encrypted notes.")
                }
                val shareIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                    putExtra(Intent.EXTRA_SUBJECT, "My Notes - Seed Phrase Backup")
                }, "Save seed phrase")
                context.startActivity(shareIntent)
                onSuccess()
            } catch (shareException: Exception) {
                onError(shareException)
            }
        }
    }
}

/**
 * Composable for creating and remembering SeedPhraseActions with SAF launcher support
 */
class SeedPhraseActionsState(
    val actions: SeedPhraseActions,
    var fileWriterLauncher: ActivityResultLauncher<String>? = null,
    var currentSeedPhrase: String = "",
    val onFileSaveSuccess: () -> Unit = {},
    val onFileSaveError: (Exception) -> Unit = {},
    val onGoogleDriveSuccess: () -> Unit = {},
    val onGoogleDriveError: (Exception) -> Unit = {},
    val onCopySuccess: () -> Unit = {}
) {
    fun saveToFile(seedPhrase: String) {
        currentSeedPhrase = seedPhrase
        fileWriterLauncher?.let { launcher ->
            actions.saveToFileWithPicker(launcher, onFileSaveError)
        }
    }

    fun handleFileResult(uri: Uri?) {
        actions.writeSeedToUri(uri, currentSeedPhrase, onFileSaveSuccess, onFileSaveError)
    }

    fun saveToGoogleDrive(seedPhrase: String) {
        actions.saveToGoogleDrive(seedPhrase, onGoogleDriveSuccess, onGoogleDriveError)
    }

    fun copyToClipboard(seedPhrase: String) {
        actions.copyToClipboard(seedPhrase)
        onCopySuccess()
    }
}

@Composable
fun rememberSeedPhraseActions(
    context: Context,
    onFileSaveSuccess: () -> Unit = {},
    onFileSaveError: (Exception) -> Unit = {},
    onGoogleDriveSuccess: () -> Unit = {},
    onGoogleDriveError: (Exception) -> Unit = {},
    onCopySuccess: () -> Unit = {}
): SeedPhraseActionsState {
    val actions = remember {
        SeedPhraseActions(
            context,
            onFileSaveSuccess,
            onFileSaveError,
            onGoogleDriveSuccess,
            onGoogleDriveError,
            onCopySuccess
        )
    }
    val state = remember {
        SeedPhraseActionsState(
            actions = actions,
            onFileSaveSuccess = onFileSaveSuccess,
            onFileSaveError = onFileSaveError,
            onGoogleDriveSuccess = onGoogleDriveSuccess,
            onGoogleDriveError = onGoogleDriveError,
            onCopySuccess = onCopySuccess
        )
    }
    val fileWriterLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            state.handleFileResult(uri)
        }
    state.fileWriterLauncher = fileWriterLauncher
    return state
}
