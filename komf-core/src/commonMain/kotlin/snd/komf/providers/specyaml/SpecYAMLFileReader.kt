@file:Suppress("DEPRECATION")
package snd.komf.providers.specyaml

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

private val logger = KotlinLogging.logger {}

class SpecYAMLFileReader {
    fun readText(filePath: String): String? {
        val file = File(filePath)
        return if (file.exists() && file.isFile) file.readText() else null
    }

    fun readEmbeddedYaml(archivePath: String): String? {
        val file = File(archivePath)
        logger.trace { "SY:readEmbeddedYaml file=$archivePath isFile=${file.isFile}" }
        if (!file.isFile) return null

        val zipExt = isZip(archivePath)
        val sevenZExt = isSevenZ(archivePath)
        logger.trace { "SY:readEmbeddedYaml isZip=$zipExt isSevenZ=$sevenZExt" }

        return try {
            when {
                zipExt -> readFromZip(archivePath)
                sevenZExt -> readFromSevenZ(file)
                else -> null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read embedded YAML from $archivePath" }
            null
        }
    }

    private fun readFromZip(archivePath: String): String? {
        logger.trace { "SY:readFromZip creating ZipFile $archivePath" }
        ZipFile(archivePath).use { zip ->
            logger.trace { "SY:readFromZip ZipFile created ok" }
            for (name in listOf("info.yaml", "info.yml")) {
                val entry = zip.getEntry(name)
                logger.trace { "SY:readFromZip looking for $name found=${entry != null}" }
                if (entry != null) return zip.getInputStream(entry).bufferedReader().readText()
            }
        }
        return null
    }

    private fun readFromSevenZ(file: File): String? {
        SevenZFile(file).use { sevenZ ->
            var entry = sevenZ.getNextEntry()
            while (entry != null) {
                if (entry.name == "info.yaml" || entry.name == "info.yml") {
                    val bytes = ByteArray(entry.size.toInt())
                    sevenZ.read(bytes, 0, bytes.size)
                    return bytes.decodeToString()
                }
                entry = sevenZ.getNextEntry()
            }
        }
        return null
    }

    fun exists(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists() && file.isFile
    }

    companion object {
        private fun isZip(path: String) = path.endsWith(".cbz", true) || path.endsWith(".zip", true)
        private fun isSevenZ(path: String) = path.endsWith(".7z", true) || path.endsWith(".cb7", true)
    }
}