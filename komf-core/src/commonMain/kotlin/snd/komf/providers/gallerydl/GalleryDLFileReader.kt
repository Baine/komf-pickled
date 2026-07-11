@file:Suppress("DEPRECATION")
package snd.komf.providers.gallerydl

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import snd.komf.util.stripBom
import java.io.File

private val logger = KotlinLogging.logger {}

class GalleryDLFileReader {
    fun readInfoJson(archivePath: String): GalleryDLInfo? {
        logger.trace { "GDL:readInfoJson enter $archivePath" }
        val external = externalJsonPath(archivePath)
        logger.trace { "GDL:externalJsonPath result: $external" }
        if (external != null) {
            logger.trace { "GDL:external found $external" }
            val text = readText(external)
            logger.trace { "GDL:external readText result: ${text != null}" }
            if (text != null) return parseGalleryDLJson(text)
        }

        val text = readEmbeddedJson(archivePath)
        logger.trace { "GDL:readEmbeddedJson result: ${text != null}" }
        if (text != null) return parseGalleryDLJson(text)

        logger.info { "No gallery-dl JSON found for $archivePath" }
        return null
    }

    private fun externalJsonPath(archivePath: String): String? {
        val ext = ARCHIVE_EXTS.firstOrNull { archivePath.endsWith(it, ignoreCase = true) } ?: return null
        val jsonPath = archivePath.dropLast(ext.length) + ".json"
        return if (File(jsonPath).isFile) jsonPath else null
    }

    private fun readEmbeddedJson(archivePath: String): String? {
        val file = File(archivePath)
        logger.trace { "GDL:readEmbeddedJson file=$archivePath isFile=${file.isFile}" }
        if (!file.isFile) return null

        val zipExt = isZip(archivePath)
        val sevenZExt = isSevenZ(archivePath)
        logger.trace { "GDL:readEmbeddedJson isZip=$zipExt isSevenZ=$sevenZExt" }

        return try {
            when {
                zipExt -> readFromZip(archivePath)
                sevenZExt -> readFromSevenZ(file)
                else -> null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read embedded info.json from $archivePath" }
            null
        }
    }

    private fun readFromZip(archivePath: String): String? {
        logger.trace { "GDL:readFromZip creating ZipFile $archivePath" }
        ZipFile(archivePath).use { zip ->
            logger.trace { "GDL:readFromZip ZipFile created ok" }
            val entry = zip.getEntry("info.json") ?: run {
                logger.trace { "GDL:readFromZip info.json not found in zip" }
                return null
            }
            logger.trace { "GDL:readFromZip found entry size=${entry.size}" }
            val text = zip.getInputStream(entry).bufferedReader().readText()
            logger.trace { "GDL:readFromZip read ${text.length} chars" }
            return text
        }
    }

    private fun readFromSevenZ(file: File): String? {
        SevenZFile(file).use { sevenZ ->
            var entry = sevenZ.getNextEntry()
            while (entry != null) {
                if (entry.name == "info.json") {
                    val bytes = ByteArray(entry.size.toInt())
                    sevenZ.read(bytes, 0, bytes.size)
                    return bytes.decodeToString()
                }
                entry = sevenZ.getNextEntry()
            }
        }
        return null
    }

    private fun readText(filePath: String): String? {
        val file = File(filePath)
        return if (file.isFile) file.readText() else null
    }

    companion object {
        private val ARCHIVE_EXTS = listOf(".cbz", ".zip", ".rar", ".cbr", ".7z", ".cb7")

        private fun isZip(path: String) = path.endsWith(".cbz", true) || path.endsWith(".zip", true)
        private fun isSevenZ(path: String) = path.endsWith(".7z", true) || path.endsWith(".cb7", true)
    }
}

private val jsonParser = Json { ignoreUnknownKeys = true }

fun parseGalleryDLJson(content: String): GalleryDLInfo? {
    return try {
        val stripped = stripBom(content)
        val element = jsonParser.parseToJsonElement(stripped)
        val obj = when (element) {
            is JsonArray -> element.firstOrNull()?.jsonObject
            is JsonObject -> element
            else -> null
        } ?: return null

        GalleryDLInfo(
            title = obj["title"]?.jsonPrimitive?.content,
            tags = parseTags(obj["tags"]),
            artists = parseArtists(obj),
            language = obj["lang"]?.jsonPrimitive?.content
                ?: obj["language"]?.jsonPrimitive?.content,
            description = obj["description"]?.jsonPrimitive?.content,
            url = obj["url"]?.jsonPrimitive?.content ?: obj["source"]?.jsonPrimitive?.content,
        )
    } catch (_: Exception) {
        logger.trace { "GDL:parseGalleryDLJson failed: ${content.take(120)}" }
        null
    }
}

private fun parseTags(element: JsonElement?): Map<String, List<String>> {
    if (element == null) return emptyMap()
    return when (element) {
        is JsonObject -> {
            element.mapValues { (_, v) ->
                v.jsonArray.map { it.jsonPrimitive.content }
            }
        }
        is JsonArray -> {
            val result = mutableMapOf<String, MutableList<String>>()
            for (item in element) {
                val text = item.jsonPrimitive.content
                val colon = text.indexOf(':')
                if (colon > 0) {
                    result.getOrPut(text.substring(0, colon)) { mutableListOf() }
                        .add(text.substring(colon + 1))
                } else {
                    result.getOrPut("tag") { mutableListOf() }.add(text)
                }
            }
            result
        }
        else -> emptyMap()
    }
}

private fun parseArtists(json: JsonObject): List<String> {
    val fromPlural = json["artists"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    val fromSingular = when (val a = json["artist"]) {
        is JsonPrimitive -> listOf(a.content)
        is JsonArray -> a.map { it.jsonPrimitive.content }
        else -> emptyList()
    }
    return fromPlural + fromSingular
}
