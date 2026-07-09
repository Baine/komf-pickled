package snd.komf.providers.german.source

import snd.komf.model.Image
import snd.komf.providers.german.model.DataSource
import snd.komf.providers.german.model.GermanSearchResult
import snd.komf.providers.german.model.GermanSeries
import snd.komf.providers.german.model.GermanSeriesId
import snd.komf.providers.german.model.GermanVolume

interface GermanDataSource {
    val source: DataSource
    suspend fun searchSeries(query: String, limit: Int): Collection<GermanSearchResult>
    suspend fun getSeries(seriesId: GermanSeriesId): GermanSeries?
    suspend fun getSeriesCover(seriesId: GermanSeriesId): Image?
    suspend fun getVolume(seriesId: GermanSeriesId, volumeId: String): GermanVolume?
    suspend fun getVolumeCover(seriesId: GermanSeriesId, volumeId: String): Image?
}
