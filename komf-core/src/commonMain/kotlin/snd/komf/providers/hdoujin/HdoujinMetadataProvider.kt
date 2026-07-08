package snd.komf.providers.hdoujin

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

class HdoujinMetadataProvider(
    private val fileReader: HdoujinReader,
    private val metadataMapper: HdoujinMetadataMapper,
) : MetadataProvider {

    override fun providerName(): CoreProviders = CoreProviders.HDOUJIN

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val info = fileReader.readMetadata(seriesId.value)
            ?: throw IllegalStateException("No HDoujin metadata found for: ${seriesId.value}")
        return metadataMapper.toSeriesMetadata(info, seriesId.value)
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? = null

    override suspend fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        val info = fileReader.readMetadata(seriesId.value)
            ?: return metadataMapper.toBookMetadata(HdoujinInfo(null, null, emptyMap()))
        return metadataMapper.toBookMetadata(info)
    }

    override suspend fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> = emptyList()

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        for (bookPath in matchQuery.bookPaths) {
            val info = fileReader.readMetadata(bookPath)
            if (info == null) continue
            return metadataMapper.toSeriesMetadata(info, bookPath)
        }
        return null
    }
}
