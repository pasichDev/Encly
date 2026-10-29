package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.documents.PrintLauncher
import com.pasich.encly.desktop.security.DesktopAppLifecycle
import com.pasich.encly.platform.PrintResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.print.Pageable
import java.awt.print.PrinterException
import java.awt.print.PrinterJob
import java.io.IOException

/**
 * The system print dialog and printer through `java.awt.print`. The dialog is the OS's own and is modal; while it
 * (and the job) is open the app counts as in front ([lifecycle]), so taking the focus away from the window is not
 * mistaken for leaving the app, which would lock the vault in the middle of a print. Needs a display: it is
 * outside the coverage gate, like the other code that talks to the OS.
 */
class AwtPrinting(private val lifecycle: DesktopAppLifecycle) : PrintLauncher {
    override suspend fun print(pages: Pageable): PrintResult {
        if (GraphicsEnvironment.isHeadless()) throw IOException("There is no display to print from")
        lifecycle.beginOwnDialog()
        try {
            val job = PrinterJob.getPrinterJob()
            job.setPageable(pages)
            job.jobName = JOB_NAME
            val confirmed = withContext(Dispatchers.Main.immediate) { job.printDialog() }
            if (!confirmed) return PrintResult.CANCELLED
            // Spooling a long job is not for the UI thread.
            withContext(Dispatchers.IO) { printJob(job) }
            return PrintResult.PRINTED
        } finally {
            // Back on the caller's thread (the UI thread), where the lifecycle is driven.
            lifecycle.endOwnDialog()
        }
    }

    private fun printJob(job: PrinterJob) {
        try {
            job.print()
        } catch (e: PrinterException) {
            throw IOException("Printing failed", e)
        }
    }

    private companion object {
        // A generic name: the job's name is shown in the system's print queue.
        const val JOB_NAME = "Encly note"
    }
}
