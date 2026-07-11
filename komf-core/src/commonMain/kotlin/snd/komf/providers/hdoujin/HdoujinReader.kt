@file:Suppress("DEPRECATION")
package snd.komf.providers.hdoujin

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import snd.komf.util.stripBom

private val logger = KotlinLogging.logger {}
private val jsonParser = Json { ignoreUnknownKeys = true }

class HdoujinReader {
    fun readMetadata(archivePath: String): HdoujinInfo? {
        val infoJson = readFileFromArchive(archivePath, "info.json")
        if (infoJson != null) {
            val parsed = parseInfoJson(infoJson)
            if (parsed != null) {
                val tagsFromJson = extractTagsFromJson(parsed)
                if (tagsFromJson.isNotEmpty()) {
                    return HdoujinInfo(
                        title = parsed["title"]?.jsonPrimitive?.content,
                        description = parsed["description"]?.jsonPrimitive?.content,
                        tags = tagsFromJson,
                    )
                }
                val tagsTxt = readFileFromArchive(archivePath, "tags.txt")
                if (tagsTxt != null) {
                    val txtTags = parseTagsTxt(tagsTxt)
                    return HdoujinInfo(
                        title = parsed["title"]?.jsonPrimitive?.content,
                        description = parsed["description"]?.jsonPrimitive?.content,
                        tags = txtTags,
                    )
                }
                return HdoujinInfo(
                    title = parsed["title"]?.jsonPrimitive?.content,
                    description = parsed["description"]?.jsonPrimitive?.content,
                    tags = emptyMap(),
                )
            }
        }

        val tagsTxt = readFileFromArchive(archivePath, "tags.txt")
        if (tagsTxt != null) {
            return HdoujinInfo(
                title = null,
                description = null,
                tags = parseTagsTxt(tagsTxt),
            )
        }

        val infoTxt = readFileFromArchive(archivePath, "info.txt")
        if (infoTxt != null) {
            return parseInfoTxt(infoTxt)
        }

        return null
    }

    private fun readFileFromArchive(archivePath: String, fileName: String): String? {
        val file = File(archivePath)
        if (!file.isFile) return null
        return try {
            when {
                isZip(archivePath) -> readFromZip(archivePath, fileName)
                isSevenZ(archivePath) -> readFromSevenZ(file, fileName)
                else -> null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read $fileName from $archivePath" }
            null
        }
    }

    private fun readFromZip(archivePath: String, fileName: String): String? {
        ZipFile(archivePath).use { zip ->
            val entry = zip.getEntry(fileName) ?: return null
            return zip.getInputStream(entry).bufferedReader().readText()
        }
    }

    private fun readFromSevenZ(file: File, fileName: String): String? {
        SevenZFile(file).use { sevenZ ->
            var entry = sevenZ.getNextEntry()
            while (entry != null) {
                if (entry.name == fileName) {
                    val bytes = ByteArray(entry.size.toInt())
                    sevenZ.read(bytes, 0, bytes.size)
                    return bytes.decodeToString()
                }
                entry = sevenZ.getNextEntry()
            }
        }
        return null
    }

    companion object {
        private fun isZip(path: String) = path.endsWith(".cbz", true) || path.endsWith(".zip", true)
        private fun isSevenZ(path: String) = path.endsWith(".7z", true) || path.endsWith(".cb7", true)
    }
}

private fun parseInfoJson(content: String): JsonObject? {
    return try {
        val stripped = stripBom(content)
        var obj = jsonParser.parseToJsonElement(stripped).jsonObject
        if ("manga_info" in obj) obj = obj["manga_info"]!!.jsonObject
        obj
    } catch (_: Exception) {
        logger.trace { "Failed to parse info.json: ${content.take(120)}" }
        null
    }
}

private val TAG_NAMESPACE_MAP = mapOf(
    "characters" to "character",
    "misc" to "other",
    "othertags" to "",
    "tags" to "",
    "url" to "source",
)

internal fun normalizeNamespace(namespace: String): String =
    TAG_NAMESPACE_MAP[namespace.lowercase()] ?: namespace.lowercase()

internal fun extractTagsFromJson(json: JsonObject): Map<String, List<String>> {
    val tagKeys = listOf("artist", "author", "circle", "character", "characters", "language", "othertags", "parody", "series", "tags", "url")
    val tags = mutableMapOf<String, MutableList<String>>()

    for (key in json.keys) {
        if (key.lowercase() !in tagKeys) continue
        val namespace = normalizeNamespace(key)
        val element = json[key] ?: continue

        when {
            element is kotlinx.serialization.json.JsonArray -> {
                for (item in element) {
                    val value = item.jsonPrimitive.content.trim()
                    if (value.isNotEmpty()) tags.getOrPut(namespace) { mutableListOf() }.add(value)
                }
            }
            element is JsonObject -> {
                for ((nestedNs, nestedValues) in element) {
                    val resolvedNs = normalizeNamespace(nestedNs)
                    if (nestedValues is kotlinx.serialization.json.JsonArray) {
                        for (item in nestedValues) {
                            val value = item.jsonPrimitive.content.trim()
                            if (value.isNotEmpty()) tags.getOrPut(resolvedNs) { mutableListOf() }.add(value)
                        }
                    } else {
                        val value = nestedValues.jsonPrimitive.content.trim()
                        if (value.isNotEmpty()) tags.getOrPut(resolvedNs) { mutableListOf() }.add(value)
                    }
                }
            }
            element is kotlinx.serialization.json.JsonPrimitive -> {
                val value = element.content.trim()
                if (value.isNotEmpty()) tags.getOrPut(namespace) { mutableListOf() }.add(value)
            }
        }
    }

    return tags
}

internal fun parseTagsTxt(content: String): Map<String, List<String>> {
    val tags = mutableMapOf<String, MutableList<String>>()
    for (line in content.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val parts = trimmed.split(":", limit = 2)
        val namespace = if (parts.size == 2) normalizeNamespace(parts[0].trim()) else ""
        val value = (if (parts.size == 2) parts[1] else parts[0]).trim()
        if (value.isNotEmpty()) tags.getOrPut(namespace) { mutableListOf() }.add(value)
    }
    return tags
}

internal fun parseInfoTxt(content: String): HdoujinInfo? {
    var title: String? = null
    var description: String? = null
    val tags = mutableMapOf<String, MutableList<String>>()
    val titleLine = Regex("(?i)^title:\\s*(.*)")
    val descLine = Regex("(?i)^description:\\s*(.*)")
    val tagLine = Regex("(?i)^(artist|author|circle|characters?|language|parody|series|tags|url):\\s*(.*)")

    for (line in content.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        titleLine.matchEntire(trimmed)?.let {
            title = it.groupValues[1].trim().ifEmpty { null }
            continue
        }
        descLine.matchEntire(trimmed)?.let {
            description = it.groupValues[1].trim().ifEmpty { null }
            continue
        }
        tagLine.matchEntire(trimmed)?.let {
            val ns = normalizeNamespace(it.groupValues[1])
            val value = it.groupValues[2].trim()
            if (value.isNotEmpty()) tags.getOrPut(ns) { mutableListOf() }.add(value)
        }
    }

    return if (tags.isNotEmpty() || title != null || description != null)
        HdoujinInfo(title = title, description = description, tags = tags)
    else null
}
