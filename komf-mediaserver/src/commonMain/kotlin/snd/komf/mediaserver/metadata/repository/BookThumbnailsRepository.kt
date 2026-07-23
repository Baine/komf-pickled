package snd.komf.mediaserver.metadata.repository

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komf.mediaserver.db.BookThumbnailTable
import snd.komf.mediaserver.model.MediaServer
import snd.komf.mediaserver.model.MediaServerBookId
import snd.komf.mediaserver.model.MediaServerSeriesId
import snd.komf.mediaserver.model.MediaServerThumbnailId

data class BookThumbnail(
    val bookId: MediaServerBookId,
    val seriesId: MediaServerSeriesId,
    val thumbnailId: MediaServerThumbnailId?,
    val mediaServer: MediaServer,
)

class BookThumbnailsRepository(
    private val database: Database,
    private val mediaServer: MediaServer
) {

    fun findFor(bookId: MediaServerBookId): BookThumbnail? {
        return transaction(database) {
            BookThumbnailTable.selectAll()
                .where { BookThumbnailTable.bookId.eq(bookId.value) }
                .firstOrNull()
                ?.toThumbnail()
        }
    }

    fun save(
        bookId: MediaServerBookId,
        seriesId: MediaServerSeriesId,
        thumbnailId: MediaServerThumbnailId?,
    ) {
        // ponytail: mediaServer.name is shadowed by BookThumbnailTable.mediaServer inside the upsert lambda
        val serverName = mediaServer.name
        transaction(database) {
            BookThumbnailTable.upsert {
                it[BookThumbnailTable.bookId] = bookId.value
                it[BookThumbnailTable.seriesId] = seriesId.value
                it[BookThumbnailTable.thumbnailId] = thumbnailId?.value
                it[BookThumbnailTable.mediaServer] = serverName
            }
        }
    }

    fun delete(bookId: MediaServerBookId) {
        transaction(database) {
            BookThumbnailTable.deleteWhere { BookThumbnailTable.bookId.eq(bookId.value) }
        }
    }

    private fun ResultRow.toThumbnail(): BookThumbnail {
        return BookThumbnail(
            bookId = MediaServerBookId(this[BookThumbnailTable.bookId]),
            seriesId = MediaServerSeriesId(this[BookThumbnailTable.seriesId]),
            thumbnailId = this[BookThumbnailTable.thumbnailId]?.let { MediaServerThumbnailId(it) },
            mediaServer = MediaServer.valueOf(this[BookThumbnailTable.mediaServer])
        )
    }
}
