package snd.komf.mediaserver.metadata.repository

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komf.mediaserver.db.SeriesMatchTable
import snd.komf.mediaserver.model.MediaServer
import snd.komf.mediaserver.model.MediaServerSeriesId
import snd.komf.model.MatchType
import snd.komf.model.ProviderSeriesId
import snd.komf.providers.CoreProviders

data class SeriesMatch(
    val seriesId: MediaServerSeriesId,
    val type: MatchType,
    val mediaServer: MediaServer,
    val provider: CoreProviders,
    val providerSeriesId: ProviderSeriesId,
)

class SeriesMatchRepository(
    private val database: Database,
    private val mediaServer: MediaServer,
) {

    fun findManualFor(seriesId: MediaServerSeriesId): SeriesMatch? {
        return transaction(database) {
            SeriesMatchTable.selectAll()
                .where {
                    SeriesMatchTable.type.eq(MatchType.MANUAL.name)
                        .and { SeriesMatchTable.seriesId.eq(seriesId.value) }
                        .and { SeriesMatchTable.mediaServer.eq(mediaServer.name) }
                }
                .firstOrNull()
                ?.toMatch()
        }
    }

    fun save(
        seriesId: MediaServerSeriesId,
        type: MatchType,
        provider: CoreProviders,
        providerSeriesId: ProviderSeriesId,
    ) {
        val serverName = mediaServer.name
        transaction(database) {
            SeriesMatchTable.upsert {
                it[SeriesMatchTable.seriesId] = seriesId.value
                it[SeriesMatchTable.type] = type.name
                it[SeriesMatchTable.mediaServer] = serverName
                it[SeriesMatchTable.provider] = provider.name
                it[SeriesMatchTable.providerSeriesId] = providerSeriesId.value
            }
        }
    }

    fun delete(seriesId: MediaServerSeriesId) {
        transaction(database) {
            SeriesMatchTable.deleteWhere { SeriesMatchTable.seriesId.eq(seriesId.value) }
        }
    }

    private fun ResultRow.toMatch(): SeriesMatch {
        return SeriesMatch(
            seriesId = MediaServerSeriesId(this[SeriesMatchTable.seriesId]),
            type = MatchType.valueOf(this[SeriesMatchTable.type]),
            mediaServer = MediaServer.valueOf(this[SeriesMatchTable.mediaServer]),
            provider = CoreProviders.valueOf(this[SeriesMatchTable.provider]),
            providerSeriesId = ProviderSeriesId(this[SeriesMatchTable.providerSeriesId])
        )
    }
}
