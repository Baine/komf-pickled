package snd.komf.providers.specyaml

import kotlinx.datetime.LocalDate
import snd.komf.model.Author
import snd.komf.model.AuthorRole
import snd.komf.model.BookMetadata
import snd.komf.model.Image
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.ReleaseDate
import snd.komf.model.SeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.model.SeriesTitle
import snd.komf.model.TitleType
import snd.komf.model.WebLink
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.CoreProviders
import snd.komf.providers.MetadataConfigApplier
import snd.komf.providers.SeriesMetadataConfig

class SpecYAMLMetadataMapper(
    private val seriesMetadataConfig: SeriesMetadataConfig,
    private val bookMetadataConfig: BookMetadataConfig,
    private val authorRoles: Collection<AuthorRole>,
    private val artistRoles: Collection<AuthorRole>,
) {
    fun toSeriesMetadata(
        yaml: SpecYAMLFile,
        yamlPath: String,
        thumbnail: Image? = null,
    ): ProviderSeriesMetadata {
        val authors = yaml.artists?.flatMap { a -> authorRoles.map { Author(a, it) } } ?: emptyList()
        val seriesId = yamlPath.substringAfterLast("/").substringBeforeLast(".")
            .let { ProviderSeriesId(it) }

        val titles = listOfNotNull(
            yaml.title?.let { SeriesTitle(it, TitleType.LOCALIZED, "en") },
        )

        val publisher = yaml.publisher?.let { snd.komf.model.Publisher(it, snd.komf.model.PublisherType.LOCALIZED, "en") }

        val genres = yaml.parodies?.toList() ?: emptyList()
        val tags = yaml.tags?.toSet() ?: emptySet()

        val metadata = SeriesMetadata(
            titles = titles,
            summary = yaml.description,
            publisher = publisher,
            genres = genres,
            tags = tags,
            authors = authors,
            totalBookCount = null,
            ageRating = yaml.ageRating?.toIntOrNull(),
            releaseDate = parseReleaseDate(yaml.released),
            thumbnail = thumbnail,
            links = yaml.urls?.map { (name, url) -> WebLink(name, url) } ?: emptyList(),
            score = null,
        )
        return MetadataConfigApplier.apply(
            ProviderSeriesMetadata(
                id = seriesId,
                metadata = metadata,
                books = emptyList(),
            ),
            seriesMetadataConfig,
        )
    }

    fun toBookMetadata(yaml: SpecYAMLFile, thumbnail: Image? = null): ProviderBookMetadata {
        val authors = yaml.artists?.flatMap { a -> authorRoles.map { Author(a, it) } } ?: emptyList()
        val tags = yaml.tags?.toSet() ?: emptySet()
        val localDate = yaml.released?.let { parseToLocalDate(it) }

        val metadata = BookMetadata(
            title = yaml.title,
            summary = yaml.description,
            number = null,
            releaseDate = localDate,
            authors = authors,
            tags = tags,
            isbn = null,
            thumbnail = thumbnail,
        )
        return MetadataConfigApplier.apply(
            ProviderBookMetadata(id = ProviderBookId(""), metadata = metadata),
            bookMetadataConfig,
        )
    }

    fun toSeriesSearchResult(yaml: SpecYAMLFile, yamlPath: String): SeriesSearchResult {
        val title = yaml.title ?: yamlPath.substringAfterLast("/").substringBeforeLast(".")
        return SeriesSearchResult(
            url = yamlPath,
            imageUrl = null,
            title = title,
            provider = CoreProviders.SPEC_YAML,
            resultId = yamlPath.substringAfterLast("/").substringBeforeLast("."),
        )
    }

    private fun parseReleaseDate(released: String?): ReleaseDate? {
        if (released == null) return null
        return try {
            val year = released.take(4).toIntOrNull()
            val month = if (released.length > 5) released.substring(5, 7).toIntOrNull() else null
            val day = if (released.length > 8) released.substring(8, 10).toIntOrNull() else null
            if (year != null) ReleaseDate(year, month, day) else null
        } catch (e: Exception) { null }
    }

    private fun parseToLocalDate(released: String): LocalDate? {
        return try {
            val parts = released.split("-")
            if (parts.size >= 3) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                val d = parts[2].toIntOrNull()
                if (y != null && m != null && d != null) LocalDate(y, m, d) else null
            } else null
        } catch (e: Exception) { null }
    }
}