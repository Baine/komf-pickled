package snd.komf.providers.german

import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komf.model.Image
import snd.komf.model.MatchQuery
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.providers.CoreProviders
import snd.komf.providers.CoreProviders.GERMAN
import snd.komf.providers.MetadataProvider
import snd.komf.providers.german.model.DataSource
import snd.komf.providers.german.model.GermanSeriesId
import snd.komf.providers.german.source.GermanDataSource
import snd.komf.util.NameSimilarityMatcher

private val logger = KotlinLogging.logger {}

class GermanMetadataProvider(
    private val sources: List<GermanDataSource>,
    private val metadataMapper: GermanMetadataMapper,
    private val nameMatcher: NameSimilarityMatcher,
    private val fetchSeriesCovers: Boolean,
    private val fetchBookCovers: Boolean,
) : MetadataProvider {

    override fun providerName(): CoreProviders = GERMAN

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val gid = GermanSeriesId(seriesId.value)
        for (source in sources.sortedBy { it.source.priority }) {
            val series = source.getSeries(gid) ?: continue
            logger.info { "getSeriesMetadata: ${source.source} returned data for $seriesId" }
            val thumbnail = if (fetchSeriesCovers) source.getSeriesCover(gid) else null
            return metadataMapper.toSeriesMetadata(series, thumbnail)
        }
        logger.warn { "getSeriesMetadata: no source returned data for $seriesId" }
        throw IllegalStateException("No German source returned data for series: $seriesId")
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? {
        val gid = GermanSeriesId(seriesId.value)
        for (source in sources.sortedBy { it.source.priority }) {
            val cover = source.getSeriesCover(gid) ?: continue
            return cover
        }
        return null
    }

    override suspend fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        val gid = GermanSeriesId(seriesId.value)
        for (source in sources.sortedBy { it.source.priority }) {
            val volume = source.getVolume(gid, bookId.id) ?: continue
            val thumbnail = if (fetchBookCovers) source.getVolumeCover(gid, bookId.id) else null
            return metadataMapper.toBookMetadata(volume, thumbnail)
        }
        throw IllegalStateException("No German source returned book data: $seriesId / $bookId")
    }

    override suspend fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> {
        // Collect unique results from all sources, dedup by title
        val seen = mutableSetOf<String>()
        val results = mutableListOf<SeriesSearchResult>()
        for (source in sources.sortedBy { it.source.priority }) {
            val searchResults = source.searchSeries(seriesName.take(200), limit)
            for (result in searchResults) {
                if (seen.add(result.title.lowercase())) {
                    results.add(metadataMapper.toSeriesSearchResult(result))
                }
            }
        }
        return results.take(limit)
    }

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        val seriesName = matchQuery.seriesName
        val searchResults = mutableListOf<Pair<snd.komf.providers.german.model.GermanSearchResult, GermanDataSource>>()

        for (source in sources.sortedBy { it.source.priority }) {
            val results = source.searchSeries(seriesName.take(200), 5)
            for (r in results) {
                searchResults.add(r to source)
            }
        }

        val match = searchResults.firstOrNull { (result, _) ->
            nameMatcher.matches(seriesName, listOf(result.title, result.alternativeTitle).filterNotNull())
        } ?: searchResults.firstOrNull()

        if (match == null) {
            logger.info { "matchSeriesMetadata: no match found for '$seriesName'" }
            return null
        }

        val (result, source) = match
        logger.info { "matchSeriesMetadata: selected '${result.title}' from ${source.source} (id=${result.id.value})" }
        val gid = result.id
        val series = source.getSeries(gid) ?: return null
        val thumbnail = if (fetchSeriesCovers) source.getSeriesCover(gid) else null
        return metadataMapper.toSeriesMetadata(series, thumbnail)
    }
}
