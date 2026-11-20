import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Turns the translations, which stay in Android's `strings.xml` format because that is what Weblate
 * reads and writes, into the files Compose Multiplatform resources read.
 *
 * The two formats differ in how a text is written, not in what it says:
 * - Android escapes `'`, `"`, `@` and `?` with a backslash; Compose resources keep the backslash, so
 *   `Couldn\'t` would reach the screen as typed. The backslash is dropped.
 * - Android formats `%d` and `%s` in order and `%%` as a percent sign; Compose resources fill in
 *   only the numbered `%1$d` and `%1$s` and have no `%%`. `%d` becomes `%<n>$d` by its place in the
 *   text, and `%%` becomes `%`.
 *
 * Anything else a format could hold (`%.1f`, `%02d`, a stray `%`) has no meaning in Compose
 * resources, so it fails the build instead of reaching the screen as typed. `\n`, `\t`, `\\` and
 * `\uXXXX` are resolved by Compose resources themselves and pass through.
 */
@CacheableTask
abstract class ConvertAndroidStrings : DefaultTask() {
    /** The folder with `values/`, `values-<lang>/`, ... in the Android format. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: DirectoryProperty

    /** The same folders in the Compose resources format; set it as a `composeResources` directory. */
    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun convert() {
        val from = sources.get().asFile
        val to = output.get().asFile
        to.deleteRecursively()
        from.walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach { file ->
            val target = to.resolve(file.relativeTo(from).path)
            target.parentFile.mkdirs()
            target.writeText(convertAndroidStrings(file.readText(), file.relativeTo(from).path))
        }
    }
}

private val TEXT = Regex("""(<(?:string|item)\b[^>]*>)(.*?)(</(?:string|item)>)""", RegexOption.DOT_MATCHES_ALL)
private val ESCAPED = Regex("""\\(['"@?])""")
private val SPECIFIER = Regex("""%(?:(\d+)\$)?([-#+ 0,(]*\d*(?:\.\d+)?)([a-zA-Z%])""")

/** [xml], an Android `strings.xml` read from [file], as a Compose resources file. */
fun convertAndroidStrings(xml: String, file: String): String =
    TEXT.replace(xml) { it.groupValues[1] + convertText(it.groupValues[2], file) + it.groupValues[3] }

private fun convertText(raw: String, file: String): String {
    val text = ESCAPED.replace(raw) { it.groupValues[1] }
    val out = StringBuilder()
    var last = 0
    var numbered = 0
    var positional = false
    var sequential = false
    for (match in SPECIFIER.findAll(text)) {
        val (position, flags, conversion) = match.destructured
        out.append(text, last, match.range.first)
        last = match.range.last + 1
        when {
            conversion == "%" && position.isEmpty() && flags.isEmpty() -> out.append('%')

            flags.isNotEmpty() || (conversion != "d" && conversion != "s") ->
                error("$file: '${match.value}' cannot be filled in by Compose resources, use %d or %s: $raw")

            position.isNotEmpty() -> {
                positional = true
                out.append(match.value)
            }

            else -> {
                sequential = true
                out.append('%').append(++numbered).append('$').append(conversion)
            }
        }
    }
    out.append(text, last, text.length)
    check(!(positional && sequential)) { "$file: numbered and unnumbered format arguments in one text: $raw" }
    return out.toString()
}
