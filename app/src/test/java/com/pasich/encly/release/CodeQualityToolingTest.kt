package com.pasich.encly.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Guards the checks that keep the codebase clean, so none of them is dropped by accident:
 * - CI runs Spotless (ktlint) and detekt;
 * - detekt loads the Jetpack Compose rules;
 * - app/lint.xml keeps untranslated/hardcoded text and the security checks as errors;
 * - CI compiles and runs the instrumented (Room migration/schema) tests;
 * - the opt-in git hooks exist, pre-commit runs Gradle on a supported JDK, and commit-msg
 *   accepts Conventional Commits only.
 */
class CodeQualityToolingTest {

    private val root = listOf(File(".."), File("."))
        .first { File(it, "version.properties").isFile }

    @Test
    fun ciChecksFormattingAndRunsDetekt() {
        val workflow = File(root, ".github/workflows/android.yml").readText()
        assertTrue("CI must run spotlessCheck", "./gradlew spotlessCheck" in workflow)
        assertTrue("CI must run detekt", "./gradlew :app:detekt" in workflow)
    }

    @Test
    fun ciCompilesAndRunsTheInstrumentedTests() {
        val workflow = File(root, ".github/workflows/android.yml").readText()
        assertTrue(
            "CI must compile androidTest so it cannot rot",
            "./gradlew :app:compileFdroidDebugAndroidTestKotlin" in workflow,
        )
        assertTrue(
            "CI must run the instrumented tests (Room migrations, schema) on an emulator",
            "reactivecircus/android-emulator-runner" in workflow &&
                "./gradlew :app:connectedFdroidDebugAndroidTest" in workflow,
        )
    }

    @Test
    fun preCommitHookPicksASupportedJdkInsteadOfTheOneOnPath() {
        val hook = File(root, ".githooks/pre-commit").readText()
        assertTrue("pre-commit must export the JDK it checked", "export JAVA_HOME" in hook)
        assertTrue(
            "pre-commit must tell a Gradle start-up failure apart from failed checks",
            "not a formatting or detekt failure" in hook,
        )
    }

    @Test
    fun detektLoadsTheComposeRules() {
        val config = File(root, "config/detekt/detekt.yml").readText()
        val build = File(root, "app/build.gradle.kts").readText()
        assertTrue("detekt.yml must configure the Compose rule set", Regex("(?m)^Compose:$").containsMatchIn(config))
        assertTrue("the Compose rules must be a detekt plugin", "detektPlugins(libs.compose.rules.detekt)" in build)
    }

    @Test
    fun lintTreatsTextAndSecurityIssuesAsErrors() {
        val lintXml = File(root, "app/lint.xml").readText()
        val errors = Regex("""<issue id="(\w+)" severity="error"\s*/>""")
            .findAll(lintXml)
            .map { it.groupValues[1] }
            .toSet()
        val missing = REQUIRED_LINT_ERRORS - errors
        assertTrue("app/lint.xml must keep these as errors: $missing", missing.isEmpty())
    }

    @Test
    fun gitHooksAreShipped() {
        HOOKS.forEach { name ->
            val hook = File(root, ".githooks/$name")
            assertTrue(".githooks/$name is missing", hook.isFile)
            assertTrue(".githooks/$name must start with a shebang", hook.readText().startsWith("#!/bin/sh"))
        }
    }

    @Test
    fun commitMsgHookAcceptsOnlyConventionalCommits() {
        assumeTrue("needs a POSIX shell", File("/bin/sh").canExecute())
        ACCEPTED.forEach { subject -> assertEquals(subject, 0, runCommitMsgHook(subject)) }
        REJECTED.forEach { subject -> assertTrue(subject, runCommitMsgHook(subject) != 0) }
    }

    private fun runCommitMsgHook(message: String): Int {
        val messageFile = File.createTempFile("COMMIT_EDITMSG", null).apply {
            deleteOnExit()
            writeText("$message\n\n# Please enter the commit message for your changes.\n")
        }
        val process = ProcessBuilder("/bin/sh", File(root, ".githooks/commit-msg").path, messageFile.path)
            .redirectErrorStream(true)
            .start()
        process.inputStream.readBytes()
        assertTrue("commit-msg hook timed out", process.waitFor(HOOK_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return process.exitValue()
    }

    private companion object {
        const val HOOK_TIMEOUT_SECONDS = 10L

        val HOOKS = listOf("pre-commit", "commit-msg")

        val REQUIRED_LINT_ERRORS = setOf(
            "HardcodedText",
            "MissingTranslation",
            "UnusedResources",
            "SetWorldReadable",
            "SetWorldWritable",
            "TrustAllX509TrustManager",
            "AllowBackup",
            "HardcodedDebugMode",
            "UnspecifiedImmutableFlag",
        )

        val ACCEPTED = listOf(
            "feat(editor): add a checklist block",
            "fix: keep the lock screen on resume",
            "build(deps): bump the kotlin group",
            "refactor!: drop the legacy vault format",
            "Merge branch 'main' into beta-hardening",
            "fixup! fix: keep the lock screen on resume",
        )

        val REJECTED = listOf(
            "Update stuff",
            "feat:missing space",
            "feature: not a type",
            "fix: " + "x".repeat(100),
        )
    }
}
