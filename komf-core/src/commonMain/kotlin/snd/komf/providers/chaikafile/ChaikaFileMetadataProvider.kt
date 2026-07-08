package snd.komf.providers.chaikafile

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

class ChaikaFileMetadataProvider(
    private val fileReader: ChaikaFileReader,
    private val metadataMapper: ChaikaFileMetadataMapper,
) : MetadataProvider {

    override fun providerName(): CoreProviders = CoreProviders.CHAIKA_FILE

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val info = fileReader.readApiJson(seriesId.value)
            ?: throw IllegalStateException("No api.json found for: ${seriesId.value}")
        return metadataMapper.toSeriesMetadata(info, seriesId.value)
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? = null

    override suspend fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        val info = fileReader.readApiJson(seriesId.value) ?: return metadataMapper.toBookMetadata(ChaikaFileInfo())
        return metadataMapper.toBookMetadata(info)
    }

    override suspend fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> = emptyList()

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        logger.info { "CF:matchSeriesMetadata bookPaths=${matchQuery.bookPaths}" }
        for (bookPath in matchQuery.bookPaths) {
            val info = fileReader.readApiJson(bookPath)
            logger.info { "CF:matchSeriesMetadata readApiJson returned: ${info != null}" }
            if (info == null) continue
            val result = metadataMapper.toSeriesMetadata(info, bookPath)
            logger.info { "CF:matchSeriesMetadata mapped title='${result.metadata.title?.name}'" }
            return result
        }
        logger.info { "CF:matchSeriesMetadata no match for any bookPath" }
        return null
    }
}
