package com.athenareader.data.renderer

import android.content.Context
import android.net.Uri
import com.athenareader.domain.model.Chapter
import com.athenareader.domain.model.EpubMetadata
import com.athenareader.domain.renderer.EpubRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebViewEpubRendererImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : EpubRenderer {

    private var currentBook: LoadedEpub? = null

    override suspend fun openDocument(filePath: String): EpubMetadata = withContext(Dispatchers.IO) {
        val sourceUri = Uri.parse(filePath)
        val entries = loadZipEntries(sourceUri)
        val containerXml = entries["META-INF/container.xml"]?.decodeToString()
            ?: error("Invalid EPUB: missing META-INF/container.xml")
        val packagePath = parseContainer(containerXml)
        val packageXml = entries[packagePath]?.decodeToString()
            ?: error("Invalid EPUB: missing package document")

        val packageData = parsePackage(packageXml, basePath(packagePath), entries)
        val metadata = EpubMetadata(
            title = packageData.title.ifBlank { "Untitled EPUB" },
            author = packageData.author.ifBlank { "Unknown Author" },
            chapters = packageData.chapters
        )

        currentBook = LoadedEpub(sourceUri, metadata)
        metadata
    }

    override suspend fun getChapterContent(chapterHref: String): String = withContext(Dispatchers.IO) {
        val book = currentBook ?: error("EPUB document not opened")
        readZipEntry(book.sourceUri, normalizePath(chapterHref))?.decodeToString()
            ?: error("Missing chapter content: $chapterHref")
    }

    override suspend fun closeDocument() {
        currentBook = null
    }

    private fun loadZipEntries(uri: Uri): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[normalizePath(entry.name)] = zipStream.readBytes()
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
        } ?: error("Unable to open EPUB: $uri")
        return entries
    }

    private fun readZipEntry(uri: Uri, entryPath: String): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && normalizePath(entry.name) == entryPath) {
                        return zipStream.readBytes()
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
        }
        return null
    }

    private fun parseContainer(containerXml: String): String {
        val parser = xmlParser(containerXml)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (!fullPath.isNullOrBlank()) return normalizePath(fullPath)
            }
            parser.next()
        }
        error("Invalid EPUB: container.xml has no package reference")
    }

    private fun parsePackage(
        packageXml: String,
        packageBasePath: String,
        entries: Map<String, ByteArray>
    ): ParsedPackage {
        val parser = xmlParser(packageXml)
        var title = ""
        var author = ""
        var inMetadata = false
        var spineTocId: String? = null
        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "metadata" -> inMetadata = true
                        "title", "dc:title" -> if (inMetadata) title = parser.nextText().trim()
                        "creator", "dc:creator" -> if (inMetadata) author = parser.nextText().trim()
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                            val properties = parser.getAttributeValue(null, "properties") ?: ""
                            if (id.isNotBlank() && href.isNotBlank()) {
                                manifest[id] = ManifestItem(
                                    href = normalizeRelativePath(packageBasePath, href),
                                    mediaType = mediaType,
                                    properties = properties
                                )
                            }
                        }
                        "spine" -> {
                            spineTocId = parser.getAttributeValue(null, "toc")
                        }
                        "itemref" -> {
                            parser.getAttributeValue(null, "idref")?.takeIf { it.isNotBlank() }?.let(spine::add)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "metadata") inMetadata = false
                }
            }
            parser.next()
        }

        val navTitles = buildTocTitles(manifest, spineTocId, entries)
        val chapters = spine.mapIndexedNotNull { index, idref ->
            val item = manifest[idref] ?: return@mapIndexedNotNull null
            Chapter(
                id = idref,
                title = navTitles[item.href] ?: fallbackChapterTitle(item.href, index),
                href = item.href,
                index = index
            )
        }

        return ParsedPackage(title, author, chapters)
    }

    private fun buildTocTitles(
        manifest: Map<String, ManifestItem>,
        spineTocId: String?,
        entries: Map<String, ByteArray>
    ): Map<String, String> {
        val navItem = manifest.values.firstOrNull { it.properties.contains("nav") }
        if (navItem != null) {
            val navContent = entries[navItem.href]?.decodeToString()
            if (!navContent.isNullOrBlank()) {
                val regex = Regex(
                    "<a[^>]+href=[\"']([^\"'#]+)(?:#[^\"']*)?[\"'][^>]*>(.*?)</a>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
                return regex.findAll(navContent).associate { match ->
                    val href = normalizeRelativePath(basePath(navItem.href), match.groupValues[1])
                    val title = stripHtml(match.groupValues[2]).ifBlank { fallbackChapterTitle(href, 0) }
                    href to title
                }
            }
        }

        val ncxItem = spineTocId?.let(manifest::get)
            ?: manifest.values.firstOrNull { it.mediaType.contains("ncx") }
        if (ncxItem != null) {
            val ncxContent = entries[ncxItem.href]?.decodeToString()
            if (!ncxContent.isNullOrBlank()) {
                val parser = xmlParser(ncxContent)
                val titles = mutableMapOf<String, String>()
                var currentTitle = ""
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "text" -> currentTitle = parser.nextText().trim()
                            "content" -> {
                                val src = parser.getAttributeValue(null, "src")?.substringBefore('#')
                                if (!src.isNullOrBlank()) {
                                    titles[normalizeRelativePath(basePath(ncxItem.href), src)] = currentTitle
                                }
                            }
                        }
                    }
                    parser.next()
                }
                return titles
            }
        }

        return emptyMap()
    }

    private fun xmlParser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        return factory.newPullParser().apply {
            setInput(StringReader(xml))
        }
    }

    private fun normalizeRelativePath(basePath: String, relativePath: String): String {
        return normalizePath(
            buildString {
                if (basePath.isNotBlank()) {
                    append(basePath)
                    if (!basePath.endsWith("/")) append('/')
                }
                append(relativePath)
            }
        )
    }

    private fun normalizePath(path: String): String {
        val cleanPath = path.replace('\\', '/')
        val output = mutableListOf<String>()
        cleanPath.split('/').forEach { segment ->
            when {
                segment.isBlank() || segment == "." -> Unit
                segment == ".." -> if (output.isNotEmpty()) output.removeAt(output.lastIndex)
                else -> output += segment
            }
        }
        return output.joinToString("/")
    }

    private fun basePath(path: String): String {
        val normalized = normalizePath(path)
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash == -1) "" else normalized.substring(0, lastSlash)
    }

    private fun fallbackChapterTitle(href: String, index: Int): String {
        val fileName = href.substringAfterLast('/').substringBeforeLast('.')
        val title = fileName.replace('-', ' ').replace('_', ' ').trim()
        return title.ifBlank { "Chapter ${index + 1}" }
    }

    private fun stripHtml(value: String): String {
        return value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private data class ManifestItem(
        val href: String,
        val mediaType: String,
        val properties: String
    )

    private data class ParsedPackage(
        val title: String,
        val author: String,
        val chapters: List<Chapter>
    )

    private data class LoadedEpub(
        val sourceUri: Uri,
        val metadata: EpubMetadata
    )
}
