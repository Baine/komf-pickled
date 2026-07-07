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
import snd.komf.providers.SpecYAMLConfig
import snd.komf.util.NameSimilarityMatcher

private val logger = KotlinLogging.logger { }

class SpecYAMLMetadataProvider(
    private val config: SpecYAMLConfig,
    private val fileReader: SpecYAMLFileReader,
    private val metadataMapper: SpecYAMLMetadataMapper,
    private val nameMatcher: NameSimilarityMatcher,
) : MetadataProvider {

    override fun providerName(): CoreProviders = CoreProviders.SPEC_YAML

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val yamlPath = findYamlPath(seriesId.value)
            ?: throw IllegalStateException("No SpecYAML file found for series: ${seriesId.value}")

        val yamlContent = fileReader.readText(yamlPath)
            ?: throw IllegalStateException("SpecYAML file disappeared: $yamlPath")

        val yaml = parseYaml(yamlContent)
            ?: throw IllegalStateException("Failed to parse SpecYAML file: $yamlPath")

        return metadataMapper.toSeriesMetadata(yaml, yamlPath)
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? = null

    override suspend fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        // Try to derive YAML path directly from book file path
        var yamlPath = deriveYamlPathFromBookPath(bookId.id)
        
        // Fall back to search if direct derivation fails
        if (yamlPath == null) {
            yamlPath = findYamlPath(seriesId.value, bookId.id)
        }
        
        if (yamlPath == null) {
            return metadataMapper.toBookMetadata(SpecYAMLFile(), null)
        }

        val yamlContent = fileReader.readText(yamlPath) ?: return metadataMapper.toBookMetadata(SpecYAMLFile(), null)
        val yaml = parseYaml(yamlContent) ?: return metadataMapper.toBookMetadata(SpecYAMLFile(), null)

        return metadataMapper.toBookMetadata(yaml, null)
    }

    override suspend fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> {
        val results = mutableListOf<SeriesSearchResult>()

        for (root in config.mediaRoots) {
            val yamlFiles = fileReader.listYamlFiles(root)
            for (yamlPath in yamlFiles) {
                val yamlContent = fileReader.readText(yamlPath) ?: continue
                val yaml = parseYaml(yamlContent) ?: continue
                val title = yaml.title ?: fileReader.nameWithoutExtension(yamlPath)

                if (nameMatcher.matches(seriesName, title)) {
                    results.add(metadataMapper.toSeriesSearchResult(yaml, yamlPath))
                }

                if (results.size >= limit) break
            }
            if (results.size >= limit) break
        }

        return results.take(limit)
    }

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        val seriesName = matchQuery.seriesName

        for (root in config.mediaRoots) {
            var yamlPath = "$root/$seriesName.yaml"
            var result = tryMatch(yamlPath, seriesName)
            if (result != null) return result

            yamlPath = "$root/$seriesName.yml"
            result = tryMatch(yamlPath, seriesName)
            if (result != null) return result

            yamlPath = "$root/$seriesName/$seriesName.yaml"
            result = tryMatch(yamlPath, seriesName)
            if (result != null) return result

            yamlPath = "$root/$seriesName/$seriesName.yml"
            result = tryMatch(yamlPath, seriesName)
            if (result != null) return result
        }

        return null
    }

    private fun tryMatch(yamlPath: String, searchName: String): ProviderSeriesMetadata? {
        val yamlContent = fileReader.readText(yamlPath) ?: return null
        val yaml = parseYaml(yamlContent) ?: return null
        val title = yaml.title ?: searchName

        if (nameMatcher.matches(searchName, title)) {
            return metadataMapper.toSeriesMetadata(yaml, yamlPath)
        }
        return null
    }

    private fun deriveYamlPathFromBookPath(bookPath: String): String? {
        // If bookPath looks like a full file path (e.g., /path/to/book.cbz),
        // derive the YAML path by replacing the extension
        if (!bookPath.contains("/") && !bookPath.contains("\\")) {
            // Not a full path, return null to fall back to search
            return null
        }

        // Try common archive extensions
        for (ext in listOf(".cbz", ".zip", ".rar", ".7z", ".cbr", ".CBZ", ".ZIP", ".RAR", ".7Z", ".CBR")) {
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

    private fun findYamlPath(seriesId: String, bookName: String? = null): String? {
        for (root in config.mediaRoots) {
            var path = "$root/$seriesId.yaml"
            if (fileReader.exists(path)) return path

            path = "$root/$seriesId.yml"
            if (fileReader.exists(path)) return path

            path = "$root/$seriesId/$seriesId.yaml"
            if (fileReader.exists(path)) return path

            path = "$root/$seriesId/$seriesId.yml"
            if (fileReader.exists(path)) return path

            if (bookName != null) {
                path = "$root/$seriesId/$bookName.yaml"
                if (fileReader.exists(path)) return path

                path = "$root/$seriesId/$bookName.yml"
                if (fileReader.exists(path)) return path
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