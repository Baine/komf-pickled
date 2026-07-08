package snd.komf.providers.gallerydl

import snd.komf.model.Author
import snd.komf.model.AuthorRole
import snd.komf.model.BookMetadata
import snd.komf.model.Image
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.SeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.model.SeriesTitle
import snd.komf.model.TitleType
import snd.komf.model.WebLink
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.CoreProviders
import snd.komf.providers.MetadataConfigApplier
import snd.komf.providers.SeriesMetadataConfig

class GalleryDLMetadataMapper(
    private val seriesMetadataConfig: SeriesMetadataConfig,
    private val bookMetadataConfig: BookMetadataConfig,
    private val authorRoles: Collection<AuthorRole>,
    private val artistRoles: Collection<AuthorRole>,
) {
    fun toSeriesMetadata(
        info: GalleryDLInfo,
        archivePath: String,
        thumbnail: Image? = null,
    ): ProviderSeriesMetadata {
        val authors = info.artists.flatMap { a -> authorRoles.map { Author(a, it) } } + info.artists.flatMap { a -> artistRoles.map { Author(a, it) } }
        val tags = info.tags.flatMap { (_, values) -> values }.toSet()

        val metadata = SeriesMetadata(
            title = info.title?.let { SeriesTitle(it, TitleType.LOCALIZED, "en") },
            titles = info.title?.let { listOf(SeriesTitle(it, TitleType.LOCALIZED, "en")) }.orEmpty(),
            summary = info.description,
            language = info.language,
            tags = tags,
            authors = authors,
            links = info.url?.let { listOf(WebLink("gallery-dl", it)) }.orEmpty(),
            thumbnail = thumbnail,
        )
        return MetadataConfigApplier.apply(
            ProviderSeriesMetadata(
                id = ProviderSeriesId(archivePath),
                metadata = metadata,
                books = emptyList(),
            ),
            seriesMetadataConfig,
        )
    }

    fun toBookMetadata(info: GalleryDLInfo, thumbnail: Image? = null): ProviderBookMetadata {
        val authors = info.artists.flatMap { a -> authorRoles.map { Author(a, it) } } + info.artists.flatMap { a -> artistRoles.map { Author(a, it) } }
        val tags = info.tags.flatMap { (_, values) -> values }.toSet()

        val metadata = BookMetadata(
            title = info.title,
            summary = info.description,
            authors = authors,
            tags = tags,
        )
        return MetadataConfigApplier.apply(
            ProviderBookMetadata(id = ProviderBookId(""), metadata = metadata),
            bookMetadataConfig,
        )
    }

    fun toSeriesSearchResult(info: GalleryDLInfo, archivePath: String): SeriesSearchResult {
        return SeriesSearchResult(
            url = archivePath,
            imageUrl = null,
            title = info.title ?: archivePath.substringAfterLast("/").substringBeforeLast("."),
            provider = CoreProviders.GALLERY_DL,
            resultId = archivePath,
        )
    }
}
