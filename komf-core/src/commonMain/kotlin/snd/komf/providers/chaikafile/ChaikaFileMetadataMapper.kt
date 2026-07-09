package snd.komf.providers.chaikafile

import snd.komf.model.BookMetadata
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.SeriesMetadata
import snd.komf.model.SeriesTitle
import snd.komf.model.TitleType
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.MetadataConfigApplier
import snd.komf.providers.SeriesMetadataConfig

class ChaikaFileMetadataMapper(
    private val seriesMetadataConfig: SeriesMetadataConfig,
    private val bookMetadataConfig: BookMetadataConfig,
) {
    fun toSeriesMetadata(info: ChaikaFileInfo, archivePath: String): ProviderSeriesMetadata {
        val tags = mutableSetOf<String>()
        tags.addAll(info.tags)
        info.category?.let { tags.add(it) }
        info.download?.let { tags.add(it) }

        val identifier = info.gallery ?: info.id
        if (identifier != null) tags.add(identifier)

        info.posted?.let { tags.add(it) }

        val metadata = SeriesMetadata(
            title = info.title?.let { SeriesTitle(it, TitleType.LOCALIZED, "en") },
            titles = info.title?.let { listOf(SeriesTitle(it, TitleType.LOCALIZED, "en")) }.orEmpty(),
            tags = tags,
        )
        return MetadataConfigApplier.apply(
            ProviderSeriesMetadata(
                id = ProviderSeriesId(identifier ?: archivePath),
                metadata = metadata,
                books = emptyList(),
            ),
            seriesMetadataConfig,
        )
    }

    fun toBookMetadata(info: ChaikaFileInfo): ProviderBookMetadata {
        val metadata = BookMetadata(
            title = info.title,
        )
        return MetadataConfigApplier.apply(
            ProviderBookMetadata(id = ProviderBookId(""), metadata = metadata),
            bookMetadataConfig,
        )
    }
}
