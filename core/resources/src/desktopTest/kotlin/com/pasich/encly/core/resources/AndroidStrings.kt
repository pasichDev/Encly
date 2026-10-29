package com.pasich.encly.core.resources

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The translations as Android would have read them: the four files per language (`strings`,
 * `strings_handoff`, `strings_import`, `strings_security`) of `src/commonMain/strings/values[-<lang>]/`,
 * which are the files Weblate manages, read with the rules of Android's resource compiler and formatted
 * the way `Resources.getString` formats. It is the reference [AndroidStringsConversionTest] holds the
 * Compose resources to.
 */
internal object AndroidStrings {
    /** A language and the folder its strings were in: `values` is English, the default. */
    class Language(val tag: String, val folder: String)

    val languages = listOf(
        Language("en", "values"),
        Language("de", "values-de"),
        Language("es", "values-es"),
        Language("fr", "values-fr"),
        Language("it", "values-it"),
        Language("nl", "values-nl"),
        Language("pl", "values-pl"),
        Language("pt", "values-pt"),
        Language("uk", "values-uk"),
    )

    private val files = listOf("strings.xml", "strings_handoff.xml", "strings_import.xml", "strings_security.xml")

    class Plural(val items: Map<String, String>)

    class Entries(val strings: Map<String, String>, val plurals: Map<String, Plural>)

    /** The decoded strings and plurals of [language] alone (its own folder, no fallback). */
    fun read(language: Language): Entries {
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Plural>()
        for (file in files) {
            val source = File("src/commonMain/strings/${language.folder}/$file")
            check(source.isFile) { "missing ${source.absolutePath}" }
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source).documentElement
            val nodes = root.childNodes
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as? org.w3c.dom.Element ?: continue
                val name = node.getAttribute("name")
                when (node.tagName) {
                    "string" -> strings.putNew(name, decode(node.textContent))

                    "plurals" -> {
                        val items = linkedMapOf<String, String>()
                        val children = node.childNodes
                        for (child in 0 until children.length) {
                            val item = children.item(child) as? org.w3c.dom.Element ?: continue
                            items[item.getAttribute("quantity")] = decode(item.textContent)
                        }
                        check(plurals.put(name, Plural(items)) == null) { "duplicate plurals $name" }
                    }

                    else -> error("unexpected <${node.tagName}> $name in ${language.folder}/$file")
                }
            }
        }
        return Entries(strings, plurals)
    }

    private fun MutableMap<String, String>.putNew(key: String, value: String) {
        check(put(key, value) == null) { "duplicate string $key" }
    }

    /**
     * The text of a resource as aapt stores it: surrounding whitespace dropped, runs of it outside
     * double quotes collapsed to one space, `\n`, `\t`, `\'`, `\"`, `\\`, `\@`, `\?` and `\uXXXX`
     * escapes resolved and unescaped double quotes (which only switch the quoting) removed.
     */
    fun decode(raw: String): String {
        val text = raw.trim()
        val out = StringBuilder()
        var quoted = false
        var pendingSpace = false
        var index = 0
        fun emit(char: Char) {
            if (pendingSpace) out.append(' ')
            pendingSpace = false
            out.append(char)
        }
        while (index < text.length) {
            val char = text[index]
            when {
                char == '\\' && index + 1 < text.length -> {
                    val next = text[index + 1]
                    index += 2
                    when (next) {
                        'n' -> emit('\n')

                        't' -> emit('\t')

                        'u' -> {
                            emit(text.substring(index, index + HEX_DIGITS).toInt(HEX_RADIX).toChar())
                            index += HEX_DIGITS
                        }

                        else -> emit(next)
                    }
                    continue
                }

                char == '"' -> quoted = !quoted

                char.isWhitespace() && !quoted -> pendingSpace = true

                else -> emit(char)
            }
            index++
        }
        return out.toString()
    }

    /** What `Resources.getString(id, *args)` gives for [decoded]: `java.util.Formatter` semantics. */
    fun format(decoded: String, args: Array<Any>): String = String.format(Locale.ROOT, decoded, *args)

    /** Whether [decoded] has any format specifier, i.e. whether Android would format it at all. */
    fun hasSpecifiers(decoded: String): Boolean = SPECIFIER.containsMatchIn(decoded)

    /**
     * Sample arguments for [decoded]: an integer for each `%d`, a word for each `%s`, in the order
     * of the specifiers (positional ones by their number).
     */
    fun sampleArguments(decoded: String): Array<Any> {
        val kinds = sortedMapOf<Int, Char>()
        var sequential = 0
        for (match in SPECIFIER.findAll(decoded)) {
            val conversion = match.groupValues[3]
            if (conversion == "%") continue
            val position = match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt() ?: ++sequential
            kinds[position] = conversion.single()
        }
        val last = kinds.keys.maxOrNull() ?: return emptyArray()
        return Array(last) { slot ->
            val position = slot + 1
            if (kinds[position] == 's') "word$position" else position * ARGUMENT_STEP + ARGUMENT_BASE
        }
    }

    /** Groups: the position (`%2$d`), the flags and width, the conversion letter. */
    private val SPECIFIER = Regex("""%(?:(\d+)\$)?([-#+ 0,(]*\d*(?:\.\d+)?)([a-zA-Z%])""")

    private const val HEX_DIGITS = 4
    private const val HEX_RADIX = 16
    private const val ARGUMENT_STEP = 7
    private const val ARGUMENT_BASE = 3
}
