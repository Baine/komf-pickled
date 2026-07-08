@file:Suppress("DEPRECATION")
package snd.komf.providers.chaikafile

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

private val logger = KotlinLogging.logger {}

class ChaikaFileReader {
    fun readApiJson(archivePath: String): ChaikaFileInfo? {
        logger.trace { "CF:readApiJson enter $archivePath" }
        val text = readEmbeddedJson(archivePath)
        logger.trace { "CF:readEmbeddedJson result: ${text != null}" }
        if (text == null) return null
        val parsed = parseApiJson(text) ?: return null
        if (parsed.gallery == null && parsed.id == null) return null
        return parsed
    }

    private fun readEmbeddedJson(archivePath: String): String? {
        val file = File(archivePath)
        logger.trace { "CF:readEmbeddedJson file=$archivePath isFile=${file.isFile}" }
        if (!file.isFile) return null

        return try {
            when {
                isZip(archivePath) -> readFromZip(archivePath)
                isSevenZ(archivePath) -> readFromSevenZ(file)
                else -> null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read api.json from $archivePath" }
            null
        }
    }

    private fun readFromZip(archivePath: String): String? {
        logger.trace { "CF:readFromZip creating ZipFile $archivePath" }
        ZipFile(archivePath).use { zip ->
            logger.trace { "CF:readFromZip ZipFile created ok" }
            val entry = zip.getEntry("api.json") ?: run {
                logger.trace { "CF:readFromZip api.json not found in zip" }
                return null
            }
            logger.trace { "CF:readFromZip found entry size=${entry.size}" }
            val text = zip.getInputStream(entry).bufferedReader().readText()
            logger.trace { "CF:readFromZip read ${text.length} chars" }
            return text
        }
    }

    private fun readFromSevenZ(file: File): String? {
        SevenZFile(file).use { sevenZ ->
            var entry = sevenZ.getNextEntry()
            while (entry != null) {
                if (entry.name == "api.json") {
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

private val jsonParser = Json { ignoreUnknownKeys = true }

fun parseApiJson(content: String): ChaikaFileInfo? {
    return try {
        val stripped = if (content.firstOrNull() == '\uFEFF') content.drop(1) else content
        val obj = jsonParser.parseToJsonElement(stripped).jsonObject

        val download = obj["download"]?.jsonPrimitive?.content
            ?: obj["archives"]?.jsonArray?.firstOrNull()?.jsonObject?.get("link")?.jsonPrimitive?.content

        ChaikaFileInfo(
            title = obj["title"]?.jsonPrimitive?.content,
            tags = obj["tags"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
            category = obj["category"]?.jsonPrimitive?.content,
            download = download,
            gallery = obj["gallery"]?.jsonPrimitive?.content,
            id = obj["id"]?.jsonPrimitive?.content,
            posted = obj["posted"]?.jsonPrimitive?.content,
        )
    } catch (_: Exception) {
        logger.trace { "Failed to parse api.json: ${content.take(120)}" }
        null
    }
}
