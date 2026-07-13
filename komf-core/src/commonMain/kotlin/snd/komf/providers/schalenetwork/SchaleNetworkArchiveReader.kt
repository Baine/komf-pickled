@file:Suppress("DEPRECATION")
package snd.komf.providers.schalenetwork

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import snd.komf.providers.schalenetwork.model.SchaleNetworkId
import snd.komf.providers.schalenetwork.model.SchaleNetworkSource
import java.io.File

private val logger = KotlinLogging.logger {}

class SchaleNetworkArchiveReader {

    fun readSource(archivePath: String): SchaleNetworkId? {
        val yamlContent = readInfoYaml(archivePath) ?: return null
        return parseSource(yamlContent)
    }

    private fun readInfoYaml(archivePath: String): String? {
        val file = File(archivePath)
        if (!file.isFile) return null
        return try {
            when {
                isZip(archivePath) -> readFromZip(archivePath)
                isSevenZ(archivePath) -> readFromSevenZ(file)
                else -> null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read info.yaml from $archivePath" }
            null
        }
    }

    private fun readFromZip(archivePath: String): String? {
        ZipFile(archivePath).use { zip ->
            for (name in listOf("info.yaml", "info.yml")) {
                val entry = zip.getEntry(name) ?: continue
                return zip.getInputStream(entry).bufferedReader().readText()
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

    private fun parseSource(content: String): SchaleNetworkId? {
        val source = try {
            val kaml = Yaml(configuration = YamlConfiguration(strictMode = false))
            kaml.decodeFromString(SchaleNetworkInfoYaml.serializer(), content).source
        } catch (e: Exception) {
            logger.trace { "Failed to parse info.yaml source: ${e.message}" }
            return null
        }
        if (source == null) return null
        return SchaleNetworkSource(source).parse()
    }

    companion object {
        private fun isZip(path: String) = path.endsWith(".cbz", true) || path.endsWith(".zip", true)
        private fun isSevenZ(path: String) = path.endsWith(".7z", true) || path.endsWith(".cb7", true)
    }
}

@kotlinx.serialization.Serializable
data class SchaleNetworkInfoYaml(
    val source: String? = null,
)
