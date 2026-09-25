package com.pasich.encly.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Screens take their spacing and sizes from the design system (EnclyTheme.spacing and the
 * components in presentation/designsystem) and never write dp values of their own (design spec
 * §3.2). `0.dp` is allowed: it means "none", not a size.
 */
class NoDpLiteralsTest {

    private val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

    @Test
    fun dpLiteralsOnlyInTheDesignSystem() {
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { file -> ALLOWED.any { file.invariantSeparatorsPath.contains(it) } }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.relativeTo(sourceRoot).invariantSeparatorsPath}:${index + 1}"
                        .takeIf { DP_LITERAL.findAll(line).any { it.groupValues[1].toDouble() != 0.0 } }
                }
            }
            .toList()
        assertTrue(
            "Use EnclyTheme.spacing or a design-system component instead of dp literals: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun patternCatchesLiteralsButNotTokens() {
        listOf("Modifier.padding(16.dp)", "size(2.5.dp)", "height(64.dp)")
            .forEach { assertTrue(it, DP_LITERAL.containsMatchIn(it)) }
        listOf("EnclyTheme.spacing.m", "width.dp", "configuration.screenWidthDp.dp")
            .forEach { assertFalse(it, DP_LITERAL.containsMatchIn(it)) }
    }

    private companion object {
        val ALLOWED = listOf("/com/pasich/encly/ui/theme/", "/com/pasich/encly/presentation/designsystem/")
        val DP_LITERAL = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\.dp\b""")
    }
}
