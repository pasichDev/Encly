package com.pasich.encly.release

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Screens read colour roles (MaterialTheme.colorScheme.*) and never raw colours: a hex colour
 * literal belongs in ui/theme, where every palette defines it once for light and dark. A literal
 * anywhere else would ignore the chosen palette and mode (design spec §1.1).
 */
class NoColorLiteralsTest {

    private val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

    @Test
    fun colorLiteralsOnlyInUiTheme() {
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.contains("/com/pasich/encly/ui/theme/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.relativeTo(sourceRoot).invariantSeparatorsPath}:${index + 1}"
                        .takeIf { COLOR_LITERAL.containsMatchIn(line) }
                }
            }
            .toList()
        assertTrue("Use MaterialTheme.colorScheme roles instead of colour literals: $offenders", offenders.isEmpty())
    }

    @Test
    fun patternCatchesEveryLiteralForm() {
        listOf("Color(0xFF112233)", "Color( 0xff112233 )", "Color(color = 0xFF112233)", "Color(0x80000000L)")
            .forEach { assertTrue(it, COLOR_LITERAL.containsMatchIn(it)) }
    }

    private companion object {
        val COLOR_LITERAL = Regex("""\bColor\(\s*(color\s*=\s*)?0x[0-9a-fA-F]""")
    }
}
