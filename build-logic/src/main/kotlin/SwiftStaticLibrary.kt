import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Compiles one Swift file into a static library for an Apple SDK, so Kotlin/Native can link it through
 * cinterop. The file exports plain C functions (`@_cdecl`) declared in a hand-written header; there is no
 * Objective-C header and no Swift module to import. `-runtime-compatibility-version none` keeps the
 * back-deployment libraries of the toolchain out of the link line: everything else Swift needs is in the
 * SDK. Needs Xcode (`swiftc`, `xcrun`), so it runs on macOS only.
 */
@CacheableTask
abstract class SwiftStaticLibrary
@Inject
constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: RegularFileProperty

    /** The SDK name `xcrun` knows: `iphonesimulator` or `iphoneos`. */
    @get:Input
    abstract val sdk: Property<String>

    /** The target triple, for example `arm64-apple-ios15.0-simulator`. */
    @get:Input
    abstract val triple: Property<String>

    /** The base name of the library; the file is `lib<name>.a`. */
    @get:Input
    abstract val libraryName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        val sdkPath = ByteArrayOutputStream().also { out ->
            exec.exec {
                commandLine("xcrun", "--sdk", sdk.get(), "--show-sdk-path")
                standardOutput = out
            }
        }.toString().trim()
        val out = outputDir.get().asFile
        out.mkdirs()
        exec.exec {
            commandLine(
                "swiftc",
                "-emit-library",
                "-static",
                "-parse-as-library",
                "-module-name", libraryName.get(),
                "-O",
                "-runtime-compatibility-version", "none",
                "-target", triple.get(),
                "-sdk", sdkPath,
                "-o", out.resolve("lib${libraryName.get()}.a").path,
                source.get().asFile.path,
            )
        }
    }
}
