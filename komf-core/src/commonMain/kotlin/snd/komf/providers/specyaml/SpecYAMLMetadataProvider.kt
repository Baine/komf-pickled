package snd.komf.providers.specyaml

import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komf.model.Image
import snd.komf.model.MatchQuery
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.providers.CoreProviders
import snd.komf.providers.MetadataProvider

private val logger = KotlinLogging.logger { }

class SpecYAMLMetadataProvider(
    private val fileReader: SpecYAMLFileReader,
    private val metadataMapper: SpecYAMLMetadataMapper,
) : MetadataProvider {

    override fun providerName(): CoreProviders = CoreProviders.SPEC_YAML

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val yamlContent = fileReader.readText(seriesId.value)
            ?: throw IllegalStateException("SpecYAML file disappeared: ${seriesId.value}")

        val yaml = parseYaml(yamlContent)
            ?: throw IllegalStateException("Failed to parse SpecYAML file: ${seriesId.value}")

        return metadataMapper.toSeriesMetadata(yaml, seriesId.value)
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? = null

    override suspend fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        val yamlPath = deriveYamlPathFromBookPath(bookId.id)
            ?: return metadataMapper.toBookMetadata(SpecYAMLFile(), null)

        val yamlContent = fileReader.readText(yamlPath) ?: return metadataMapper.toBookMetadata(SpecYAMLFile(), null)
        val yaml = parseYaml(yamlContent) ?: return metadataMapper.toBookMetadata(SpecYAMLFile(), null)

        return metadataMapper.toBookMetadata(yaml, null)
    }

    override suspend fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> = emptyList()

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        for (bookPath in matchQuery.bookPaths) {
            val yamlPath = deriveYamlPathFromBookPath(bookPath)
            if (yamlPath != null) {
                val yamlContent = fileReader.readText(yamlPath)
                if (yamlContent != null) {
                    val yaml = parseYaml(yamlContent)
                    if (yaml != null) return metadataMapper.toSeriesMetadata(yaml, yamlPath)
                }
            }

            val embedded = fileReader.readEmbeddedYaml(bookPath)
            if (embedded != null) {
                val yaml = parseYaml(embedded)
                if (yaml != null) {
                    logger.debug { "Found embedded SpecYAML in $bookPath" }
                    return metadataMapper.toSeriesMetadata(yaml, bookPath)
                }
            }
        }

        return null
    }

    private fun deriveYamlPathFromBookPath(bookPath: String): String? {
        if (!bookPath.contains("/") && !bookPath.contains("\\")) {
            return null
        }

        for (ext in listOf(".cbz", ".zip", ".rar", ".7z", ".cbr", ".cb7", ".CBZ", ".ZIP", ".RAR", ".7Z", ".CBR", ".CB7")) {
            if (bookPath.endsWith(ext)) {
                val basePath = bookPath.dropLast(ext.length)

                val yamlPath = "$basePath.yaml"
                if (fileReader.exists(yamlPath)) return yamlPath

                val ymlPath = "$basePath.yml"
                if (fileReader.exists(ymlPath)) return ymlPath

                return null
            }
        }

        return null
    }

    private fun parseYaml(content: String): SpecYAMLFile? {
        return try {
            val kaml = com.charleskorn.kaml.Yaml(
                configuration = com.charleskorn.kaml.YamlConfiguration(strictMode = false)
            )
            kaml.decodeFromString(SpecYAMLFile.serializer(), content)
        } catch (e: Exception) {
            logger.warn { "Failed to parse SpecYAML: ${e.message}" }
            null
        }
    }
}
