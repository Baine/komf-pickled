package snd.komf.providers.german

import snd.komf.model.Author
import snd.komf.model.AuthorRole
import snd.komf.model.BookMetadata
import snd.komf.model.BookRange
import snd.komf.model.Image
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.Publisher
import snd.komf.model.PublisherType
import snd.komf.model.ReleaseDate
import snd.komf.model.SeriesBook
import snd.komf.model.SeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.model.SeriesTitle
import snd.komf.model.TitleType
import snd.komf.model.WebLink
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.CoreProviders
import snd.komf.providers.MetadataConfigApplier
import snd.komf.providers.SeriesMetadataConfig
import snd.komf.providers.german.model.DataSource
import snd.komf.providers.german.model.GermanSearchResult
import snd.komf.providers.german.model.GermanSeries
import snd.komf.providers.german.model.GermanSeriesId
import snd.komf.providers.german.model.GermanVolume

class GermanMetadataMapper(
    private val seriesMetadataConfig: SeriesMetadataConfig,
    private val bookMetadataConfig: BookMetadataConfig,
    private val authorRoles: Collection<AuthorRole>,
    private val artistRoles: Collection<AuthorRole>,
) {
    fun toSeriesMetadata(series: GermanSeries, thumbnail: Image? = null): ProviderSeriesMetadata {
        val authors = series.authors.flatMap { a -> authorRoles.map { Author(a, it) } } +
            series.artists.flatMap { a -> artistRoles.map { Author(a, it) } }

        val titles = listOfNotNull(
            SeriesTitle(series.title, TitleType.LOCALIZED, "de"),
        ) + series.alternativeTitles.map { SeriesTitle(it, null, null) }

        val publisher = series.publisher?.let { Publisher(it.name, PublisherType.LOCALIZED, "de") }

        val metadata = SeriesMetadata(
            titles = titles,
            summary = series.description,
            publisher = publisher,
            genres = series.genres,
            authors = authors,
            thumbnail = thumbnail,
            totalBookCount = series.numberOfVolumes,
            ageRating = series.ageRating,
            releaseDate = series.startYear?.let { ReleaseDate(it, null, null) },
            links = listOf(WebLink(series.source.label, seriesUrl(series.id, series.source))),
            score = series.score,
        )
        return MetadataConfigApplier.apply(
            ProviderSeriesMetadata(
                id = ProviderSeriesId(series.id.value),
                metadata = metadata,
                books = series.volumes.map {
                    SeriesBook(
                        id = ProviderBookId(it.id.value),
                        number = BookRange(it.number.toDouble()),
                        edition = it.edition,
                        type = it.type,
                        name = it.name,
                    )
                }
            ),
            seriesMetadataConfig,
        )
    }

    fun toBookMetadata(volume: GermanVolume, thumbnail: Image? = null): ProviderBookMetadata {
        val metadata = BookMetadata(
            title = volume.title,
            summary = volume.description,
            number = BookRange(volume.number.toDouble()),
            releaseDate = volume.releaseDate,
            isbn = volume.isbn,
            thumbnail = thumbnail,
        )
        return MetadataConfigApplier.apply(
            ProviderBookMetadata(id = ProviderBookId(volume.id.value), metadata = metadata),
            bookMetadataConfig,
        )
    }

    fun toSeriesSearchResult(result: GermanSearchResult): SeriesSearchResult {
        return SeriesSearchResult(
            url = seriesUrl(result.id, result.source),
            imageUrl = result.imageUrl,
            title = result.title,
            provider = CoreProviders.GERMAN,
            resultId = result.id.value,
        )
    }

    private fun seriesUrl(seriesId: GermanSeriesId, source: DataSource): String = when (source) {
        DataSource.MANGAPASSION_DE -> "https://manga-passion.de/editions/${seriesId.value}"
        DataSource.WIKIPEDIA_DE -> "https://de.wikipedia.org/wiki/${seriesId.value}"
        DataSource.MANGADEX_DE -> "https://mangadex.org/title/${seriesId.value}"
    }
}
