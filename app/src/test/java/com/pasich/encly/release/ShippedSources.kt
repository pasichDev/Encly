package com.pasich.encly.release

import java.io.File

/**
 * The sources that ship, for the tests that scan them: the app's own (`app/src/main`) and every production
 * source set of `:shared` (`commonMain`, `androidMain`, ...). The UI and the data logic live in `:shared`, the
 * activities and the Android adapters in `:app`; a scan of one alone would miss the other.
 */
internal object ShippedSources {
    private val repoRoot = listOf(File(".."), File(".")).first { File(it, "version.properties").isFile }

    /** The source roots; the path of a file below one starts with `java/`, `kotlin/` or `res/`. */
    val roots: List<File> = listOf(File(repoRoot, "app/src/main")) +
        File(repoRoot, "shared/src").listFiles().orEmpty().filter { it.isDirectory && it.name.endsWith("Main") }

    /** Every file with one of the [extensions], paired with its path from the repository root. */
    fun files(extensions: Set<String>): List<Pair<File, String>> = roots.flatMap { sourceRoot ->
        sourceRoot.walkTopDown().filter { it.isFile && it.extension in extensions }
            .map { it to it.relativeTo(repoRoot).invariantSeparatorsPath }.toList()
    }
}
