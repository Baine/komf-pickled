package snd.komf.providers.schalenetwork

import snd.komf.model.Author
import snd.komf.model.AuthorRole
import snd.komf.model.BookMetadata
import snd.komf.model.Image
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.SeriesMetadata
import snd.komf.model.SeriesTitle
import snd.komf.model.TitleType
import snd.komf.model.WebLink
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.MetadataConfigApplier
import snd.komf.providers.SeriesMetadataConfig
import snd.komf.providers.schalenetwork.model.SchaleNetworkMetadata

class SchaleNetworkMetadataMapper(
    private val seriesMetadataConfig: SeriesMetadataConfig,
    private val bookMetadataConfig: BookMetadataConfig,
    private val authorRoles: Collection<AuthorRole>,
    private val artistRoles: Collection<AuthorRole>,
) {

    fun toSeriesMetadata(metadata: SchaleNetworkMetadata, thumbnail: Image? = null): ProviderSeriesMetadata {
        val tags = mutableListOf<String>()
        for ((namespace, values) in metadata.tags) {
            for (value in values) {
                tags.add(if (namespace.isEmpty()) value else "$namespace:$value")
            }
        }

        val authors = mutableListOf<Author>()
        metadata.tags["artist"]?.forEach { name ->
            artistRoles.forEach { authors.add(Author(name, it)) }
            authorRoles.forEach { authors.add(Author(name, it)) }
        }

        val seriesMetadata = SeriesMetadata(
            title = SeriesTitle(metadata.title, TitleType.LOCALIZED, "en"),
            titles = listOf(SeriesTitle(metadata.title, TitleType.LOCALIZED, "en")),
            tags = tags,
            authors = authors,
            thumbnail = thumbnail,
            links = listOf(WebLink("Schale Network", metadata.url())),
            releaseDate = metadata.releasedAt?.let {
                @Suppress("DEPRECATION")
                snd.komf.model.ReleaseDate(year = it.year, month = it.monthNumber, day = it.dayOfMonth)
            },
        )

        return MetadataConfigApplier.apply(
            ProviderSeriesMetadata(
                id = ProviderSeriesId("${metadata.id}/${metadata.key}"),
                metadata = seriesMetadata,
                books = emptyList(),
            ),
            seriesMetadataConfig,
        )
    }

    fun toBookMetadata(metadata: SchaleNetworkMetadata, thumbnail: Image? = null): ProviderBookMetadata {
        val bookMetadata = BookMetadata(
            title = metadata.title,
            thumbnail = thumbnail,
            links = listOf(WebLink("Schale Network", metadata.url())),
        )
        return MetadataConfigApplier.apply(
            ProviderBookMetadata(id = ProviderBookId("${metadata.id}/${metadata.key}"), metadata = bookMetadata),
            bookMetadataConfig,
        )
    }
}

private fun SchaleNetworkMetadata.url(): String = "https://schale.network/g/$id/$key"
