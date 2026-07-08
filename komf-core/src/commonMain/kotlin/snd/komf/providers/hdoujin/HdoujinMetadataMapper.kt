package snd.komf.providers.hdoujin

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

class HdoujinMetadataMapper(
    private val seriesMetadataConfig: SeriesMetadataConfig,
    private val bookMetadataConfig: BookMetadataConfig,
) {
    fun toSeriesMetadata(info: HdoujinInfo, archivePath: String): ProviderSeriesMetadata {
        val tags = mutableSetOf<String>()
        for ((namespace, values) in info.tags) {
            for (value in values) {
                val tag = if (namespace.isEmpty()) value else "$namespace:$value"
                tags.add(tag)
            }
        }

        val metadata = SeriesMetadata(
            title = info.title?.let { SeriesTitle(it, TitleType.LOCALIZED, "en") },
            titles = info.title?.let { listOf(SeriesTitle(it, TitleType.LOCALIZED, "en")) }.orEmpty(),
            summary = info.description,
            tags = tags,
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

    fun toBookMetadata(info: HdoujinInfo): ProviderBookMetadata {
        val metadata = BookMetadata(
            title = info.title,
            summary = info.description,
        )
        return MetadataConfigApplier.apply(
            ProviderBookMetadata(id = ProviderBookId(""), metadata = metadata),
            bookMetadataConfig,
        )
    }
}
