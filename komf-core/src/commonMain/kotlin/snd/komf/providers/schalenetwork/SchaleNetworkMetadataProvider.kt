package snd.komf.providers.schalenetwork

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
import snd.komf.providers.schalenetwork.model.SchaleNetworkId

private val logger = KotlinLogging.logger {}

class SchaleNetworkMetadataProvider(
    private val archiveReader: SchaleNetworkArchiveReader,
    private val client: SchaleNetworkClient,
    private val metadataMapper: SchaleNetworkMetadataMapper,
) : MetadataProvider {

    override fun providerName(): CoreProviders = CoreProviders.SCHALE_NETWORK

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val id = parseSeriesId(seriesId.value)
            ?: throw IllegalStateException("Invalid SchaleNetwork series id: ${seriesId.value}")
        val metadata = client.getMetadata(id)
        val thumbnail = metadata.thumbnailUrl?.let { client.getThumbnail(it) }
        return metadataMapper.toSeriesMetadata(metadata, thumbnail)
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? {
        val id = parseSeriesId(seriesId.value) ?: return null
        return runCatching {
            val metadata = client.getMetadata(id)
            metadata.thumbnailUrl?.let { client.getThumbnail(it) }
        }.onFailure { logger.warn(it) { "Failed to fetch SchaleNetwork cover for ${seriesId.value}" } }
            .getOrNull()
    }

    override suspend fun getBookMetadata(
        seriesId: ProviderSeriesId,
        bookId: ProviderBookId
    ): ProviderBookMetadata {
        val id = parseSeriesId(seriesId.value)
            ?: return metadataMapper.toBookMetadata(
                snd.komf.providers.schalenetwork.model.SchaleNetworkMetadata(
                    id = "", key = "", title = "", thumbnailUrl = null, tags = emptyMap(), releasedAt = null
                )
            )
        val metadata = client.getMetadata(id)
        val thumbnail = metadata.thumbnailUrl?.let { client.getThumbnail(it) }
        return metadataMapper.toBookMetadata(metadata, thumbnail)
    }

    override suspend fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> = emptyList()

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        for (bookPath in matchQuery.bookPaths) {
            logger.info { "Checking SchaleNetwork info.yaml in $bookPath" }
            val id = archiveReader.readSource(bookPath)
            if (id == null) {
                logger.info { "No SchaleNetwork source found in $bookPath" }
                continue
            }
            logger.info { "Matched SchaleNetwork source $id in $bookPath" }
            val metadata = client.getMetadata(id)
            val thumbnail = metadata.thumbnailUrl?.let { client.getThumbnail(it) }
            return metadataMapper.toSeriesMetadata(metadata, thumbnail)
        }
        return null
    }

    private fun parseSeriesId(value: String): SchaleNetworkId? {
        // Accept either "https://schale.network/g/<id>/<key>" or "<id>/<key>"
        val urlMatch = Regex("https?://schale\\.network/g/(\\d+)/([^/]+)").matchEntire(value)
        if (urlMatch != null) return SchaleNetworkId(urlMatch.groupValues[1], urlMatch.groupValues[2])
        val shortMatch = Regex("^(\\d+)/([^/]+)$").matchEntire(value) ?: return null
        return SchaleNetworkId(shortMatch.groupValues[1], shortMatch.groupValues[2])
    }
}
