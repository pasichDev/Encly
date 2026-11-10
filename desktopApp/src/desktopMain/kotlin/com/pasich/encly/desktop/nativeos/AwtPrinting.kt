package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.documents.PrintLauncher
import com.pasich.encly.desktop.security.DesktopAppLifecycle
import com.pasich.encly.platform.PrintResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.print.PageFormat
import java.awt.print.Pageable
import java.awt.print.Printable
import java.awt.print.PrinterAbortException
import java.awt.print.PrinterException
import java.awt.print.PrinterJob
import java.io.IOException

/**
 * The system print dialog and printer through `java.awt.print`. The dialog is the OS's own and is modal; while it
 * (and the job) is open the app counts as in front ([lifecycle]), so taking the focus away from the window is not
 * mistaken for leaving the app, which would lock the vault in the middle of a print. Needs a display: it is
 * outside the coverage gate, like the other code that talks to the OS.
 *
 * The dialog cannot be closed from outside, so a lock that comes while it is open is only seen when it returns:
 * `mayProceed` is asked then, and again before every page, and a job that may not go on is cancelled.
 */
class AwtPrinting(private val lifecycle: DesktopAppLifecycle) : PrintLauncher {
    override suspend fun print(pages: Pageable, mayProceed: () -> Boolean): PrintResult {
        if (GraphicsEnvironment.isHeadless()) throw IOException("There is no display to print from")
        lifecycle.beginOwnDialog()
        try {
            val job = PrinterJob.getPrinterJob()
            job.setPageable(GuardedPages(pages, job, mayProceed))
            job.jobName = JOB_NAME
            val confirmed = withContext(Dispatchers.Main.immediate) { job.printDialog() }
            if (!confirmed || !mayProceed()) return PrintResult.CANCELLED
            // Spooling a long job is not for the UI thread.
            val printed = withContext(Dispatchers.IO) { printJob(job) }
            return if (printed) PrintResult.PRINTED else PrintResult.CANCELLED
        } finally {
            // Back on the caller's thread (the UI thread), where the lifecycle is driven.
            lifecycle.endOwnDialog()
        }
    }

    /** True when the job went to the printer, false when it was cancelled on the way. */
    private fun printJob(job: PrinterJob): Boolean = try {
        job.print()
        true
    } catch (_: PrinterAbortException) {
        false
    } catch (e: PrinterException) {
        throw IOException("Printing failed", e)
    }

    private companion object {
        // A generic name: the job's name is shown in the system's print queue.
        const val JOB_NAME = "Encly note"
    }
}

/** [pages] that cancel [job] as soon as [mayProceed] says the print must stop: no further page is handed out. */
private class GuardedPages(
    private val pages: Pageable,
    private val job: PrinterJob,
    private val mayProceed: () -> Boolean,
) : Pageable {
    override fun getNumberOfPages(): Int = pages.numberOfPages

    override fun getPageFormat(pageIndex: Int): PageFormat {
        check()
        return pages.getPageFormat(pageIndex)
    }

    override fun getPrintable(pageIndex: Int): Printable {
        check()
        return pages.getPrintable(pageIndex)
    }

    private fun check() {
        if (!mayProceed()) job.cancel()
    }
}
