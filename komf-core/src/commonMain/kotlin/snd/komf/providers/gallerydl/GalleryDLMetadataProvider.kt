package snd.komf.providers.gallerydl

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

private val logger = KotlinLogging.logger {}

class GalleryDLMetadataProvider(
    private val fileReader: GalleryDLFileReader,
    private val metadataMapper: GalleryDLMetadataMapper,
) : MetadataProvider {

    override fun providerName(): CoreProviders = CoreProviders.GALLERY_DL

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val info = fileReader.readInfoJson(seriesId.value)
            ?: throw IllegalStateException("No gallery-dl info.json found for: ${seriesId.value}")
        return metadataMapper.toSeriesMetadata(info, seriesId.value)
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? = null

    override suspend fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        val info = fileReader.readInfoJson(seriesId.value)
            ?: return metadataMapper.toBookMetadata(GalleryDLInfo())
        return metadataMapper.toBookMetadata(info)
    }

    override suspend fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> = emptyList()

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        logger.info { "GDL:matchSeriesMetadata bookPaths=${matchQuery.bookPaths}" }
        for (bookPath in matchQuery.bookPaths) {
            val info = fileReader.readInfoJson(bookPath)
            logger.info { "GDL:matchSeriesMetadata readInfoJson returned: ${info != null}" }
            if (info == null) continue
            val result = metadataMapper.toSeriesMetadata(info, bookPath)
            logger.info { "GDL:matchSeriesMetadata mapped title='${result.metadata.title?.name}'" }
            return result
        }
        logger.info { "GDL:matchSeriesMetadata no match for any bookPath" }
        return null
    }
}
