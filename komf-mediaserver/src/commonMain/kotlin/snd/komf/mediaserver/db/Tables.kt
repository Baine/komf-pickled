package snd.komf.mediaserver.db

import org.jetbrains.exposed.v1.core.Table

object KomfJobRecordTable : Table("KomfJobRecord") {
    val id = text("id")
    val seriesId = text("seriesId")
    val status = text("status")
    val message = text("message").nullable()
    val startedAt = long("startedAt")
    val finishedAt = long("finishedAt").nullable()
    override val primaryKey = PrimaryKey(id)
}

object SeriesMatchTable : Table("SeriesMatch") {
    val seriesId = text("seriesId")
    val type = text("type")
    val mediaServer = text("mediaServer")
    val provider = text("provider")
    val providerSeriesId = text("providerSeriesId")
    override val primaryKey = PrimaryKey(seriesId, mediaServer)
}

object BookThumbnailTable : Table("BookThumbnail") {
    val bookId = text("bookId")
    val seriesId = text("seriesId")
    val thumbnailId = text("thumbnailId").nullable()
    val mediaServer = text("mediaServer")
    override val primaryKey = PrimaryKey(bookId, mediaServer)
}

object SeriesThumbnailTable : Table("SeriesThumbnail") {
    val seriesId = text("seriesId")
    val thumbnailId = text("thumbnailId").nullable()
    val mediaServer = text("mediaServer")
    override val primaryKey = PrimaryKey(seriesId)
}
