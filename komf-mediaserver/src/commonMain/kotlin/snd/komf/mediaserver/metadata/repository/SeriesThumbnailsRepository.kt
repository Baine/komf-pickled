package snd.komf.mediaserver.metadata.repository

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komf.mediaserver.db.SeriesThumbnailTable
import snd.komf.mediaserver.model.MediaServer
import snd.komf.mediaserver.model.MediaServerSeriesId
import snd.komf.mediaserver.model.MediaServerThumbnailId

data class SeriesThumbnail(
    val seriesId: MediaServerSeriesId,
    val thumbnailId: MediaServerThumbnailId?,
    val mediaServer: MediaServer,
)

class SeriesThumbnailsRepository(
    private val database: Database,
    private val mediaServer: MediaServer
) {

    fun findFor(seriesId: MediaServerSeriesId): SeriesThumbnail? {
        return transaction(database) {
            SeriesThumbnailTable.selectAll()
                .where { SeriesThumbnailTable.seriesId.eq(seriesId.value) }
                .firstOrNull()
                ?.toThumbnail()
        }
    }

    fun save(
        seriesId: MediaServerSeriesId,
        thumbnailId: MediaServerThumbnailId?,
    ) {
        val serverName = mediaServer.name
        transaction(database) {
            SeriesThumbnailTable.upsert {
                it[SeriesThumbnailTable.seriesId] = seriesId.value
                it[SeriesThumbnailTable.thumbnailId] = thumbnailId?.value
                it[SeriesThumbnailTable.mediaServer] = serverName
            }
        }
    }

    fun delete(seriesId: MediaServerSeriesId) {
        transaction(database) {
            SeriesThumbnailTable.deleteWhere { SeriesThumbnailTable.seriesId.eq(seriesId.value) }
        }
    }

    private fun ResultRow.toThumbnail(): SeriesThumbnail {
        return SeriesThumbnail(
            seriesId = MediaServerSeriesId(this[SeriesThumbnailTable.seriesId]),
            thumbnailId = this[SeriesThumbnailTable.thumbnailId]?.let { MediaServerThumbnailId(it) },
            mediaServer = MediaServer.valueOf(this[SeriesThumbnailTable.mediaServer])
        )
    }
}
