package com.pasich.encly.data.importer

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import kotlinx.io.Buffer
import kotlinx.io.asSource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One member of a test TAR; [type] is the typeflag ('0' file, '2' symlink, '5' directory, ...). */
internal class TarFile(val name: String, val data: ByteArray = ByteArray(0), val type: Char = '0')

/** Builds inputs for the import tests in memory, and reads the real exports under `import-fixtures`. */
internal object ImportFixtures {
    fun entry(path: String, text: String) = ImportEntry(path, text.toByteArray(Charsets.UTF_8))

    fun source(vararg files: Pair<String, ByteArray>): ImportFileSource = ImportFileSource {
        files.asSequence().map { (path, bytes) -> SourceFile(path) { Buffer().apply { write(bytes) } } }
    }

    fun textSource(vararg files: Pair<String, String>): ImportFileSource =
        source(*files.map { (path, text) -> path to text.toByteArray(Charsets.UTF_8) }.toTypedArray())

    fun zip(vararg files: Pair<String, ByteArray>, store: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            if (store) zip.setLevel(0)
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun zipText(vararg files: Pair<String, String>): ByteArray =
        zip(*files.map { (path, text) -> path to text.toByteArray(Charsets.UTF_8) }.toTypedArray())

    /** A ustar archive; a name longer than 100 characters is written as a GNU long-name entry. */
    fun tar(vararg files: TarFile): ByteArray {
        val out = ByteArrayOutputStream()
        for (file in files) {
            var name = file.name
            if (name.toByteArray().size > NAME_LENGTH) {
                out.write(header("././@LongLink", file.name.toByteArray().size + 1L, 'L'))
                out.write(pad(file.name.toByteArray() + 0))
                name = name.take(NAME_LENGTH)
            }
            out.write(header(name, file.data.size.toLong(), file.type))
            out.write(pad(file.data))
        }
        out.write(ByteArray(BLOCK * 2))
        return out.toByteArray()
    }

    private const val BLOCK = 512
    private const val NAME_LENGTH = 100

    private fun pad(data: ByteArray): ByteArray {
        val rest = (BLOCK - data.size % BLOCK) % BLOCK
        return data + ByteArray(rest)
    }

    private fun header(name: String, size: Long, type: Char): ByteArray {
        val block = ByteArray(BLOCK)
        fun put(offset: Int, text: String) = text.toByteArray(Charsets.ISO_8859_1).copyInto(block, offset)
        put(0, name)
        put(100, "0000644\u0000")
        put(108, "0000000\u0000")
        put(116, "0000000\u0000")
        put(124, "%011o\u0000".format(size))
        put(136, "00000000000\u0000")
        put(148, "        ")
        block[156] = type.code.toByte()
        put(257, "ustar\u000000")
        val sum = block.sumOf { it.toInt() and 0xff }
        put(148, "%06o\u0000 ".format(sum))
        return block
    }

    /** A real export file under src/test/resources/import-fixtures, or null if it is not there (yet). */
    fun resource(path: String): ByteArray? =
        ImportFixtures::class.java.classLoader?.getResourceAsStream("import-fixtures/$path")?.use { it.readBytes() }

    /** The real files of a fixture folder as a source, paths relative to [folder]; null if absent. */
    fun resourceFolder(folder: String, names: List<String>): ImportFileSource? {
        val files = names.map { it to (resource("$folder/$it") ?: return null) }
        return source(*files.toTypedArray())
    }

    /** A directory as a [DocumentTree]: document ids are relative paths. */
    private class FileTree(private val dir: java.io.File) : DocumentTree {
        override val rootId = ""

        override fun children(parentId: String): List<TreeNode> =
            java.io.File(dir, parentId).listFiles().orEmpty().sortedBy { it.name }.map {
                TreeNode(it.relativeTo(dir).invariantSeparatorsPath, it.name, it.isDirectory)
            }

        override fun open(id: String) = java.io.File(dir, id).inputStream().asSource()
    }

    /** A real fixture folder, walked by the same [TreeWalker] the Storage Access Framework tree uses. */
    fun resourceTree(folder: String): ImportFileSource? {
        val dir = ImportFixtures::class.java.classLoader?.getResource("import-fixtures/$folder")
            ?.let { java.io.File(it.toURI()) }?.takeIf { it.isDirectory } ?: return null
        return TreeWalker(FileTree(dir)).source()
    }

    fun read(source: ImportFileSource, limits: ImportLimits = ImportLimits()): ReadResult =
        ImportReader(limits).read(source)

    fun analyze(source: ImportFileSource, forced: ImportSource? = null): Analysis =
        ImportAnalyzer.analyze(read(source), forced, now = NOW)

    fun ready(source: ImportFileSource, forced: ImportSource? = null): ImportPlan =
        (analyze(source, forced) as Analysis.Ready).plan

    const val NOW = 1_800_000_000_000L

    /** Blocks as short strings for exact assertions. */
    fun describe(blocks: List<Block>): List<String> = blocks.map { block ->
        when (block) {
            is Block.TextBlock -> "text:${block.text.value}"

            is Block.HBlock -> "${block.blockType.name.lowercase()}:${block.text.value}"

            is Block.QuoteBlock -> "quote:${block.text.value}"

            is Block.SeparatorBlock -> "separator"

            is Block.LinkBlock -> "link"

            is Block.ListBlock -> {
                val kind = when (block.blockType) {
                    BlockType.LIST_CHECK -> "check"
                    BlockType.LIST_NUMBER -> "number"
                    else -> "bullet"
                }
                "$kind:" + block.items.value.joinToString("|") { (if (it.isCheck) "[x] " else "[ ] ") + it.value }
            }
        }
    }

    fun parse(source: ImportSource, vararg entries: ImportEntry): ParsedNotes {
        val list = entries.toList()
        val limits = ImportLimits()
        return when (source) {
            ImportSource.GOOGLE_KEEP -> KeepParser.parse(list, limits)
            ImportSource.STANDARD_NOTES -> StandardNotesParser.parse(list, limits)
            ImportSource.JOPLIN -> JoplinParser.parse(list, limits)
            ImportSource.NOTESNOOK -> MarkdownNotesParser.parse(list, limits, notesnook = true)
            ImportSource.MARKDOWN -> MarkdownNotesParser.parse(list, limits, notesnook = false)
        }
    }
}
