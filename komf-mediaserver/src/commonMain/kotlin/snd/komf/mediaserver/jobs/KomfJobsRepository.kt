package snd.komf.mediaserver.jobs

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komf.mediaserver.db.KomfJobRecordTable
import snd.komf.mediaserver.model.MediaServerSeriesId
import java.util.UUID
import kotlin.time.Instant

class KomfJobsRepository(private val database: Database) {

    fun get(id: MetadataJobId): MetadataJob? {
        return transaction(database) {
            KomfJobRecordTable.selectAll()
                .where { KomfJobRecordTable.id.eq(id.value.toString()) }
                .firstOrNull()
                ?.toMetadataJob()
        }
    }

    fun countAll(status: MetadataJobStatus? = null): Long {
        return transaction(database) {
            KomfJobRecordTable.selectAll()
                .apply { status?.let { where { KomfJobRecordTable.status.eq(it.name) } } }
                .count()
        }
    }

    fun findAll(
        status: MetadataJobStatus? = null,
        limit: Long = 1000,
        offset: Long = 0
    ): List<MetadataJob> {
        return transaction(database) {
            KomfJobRecordTable.selectAll()
                .apply { status?.let { where { KomfJobRecordTable.status.eq(it.name) } } }
                .orderBy(KomfJobRecordTable.startedAt to SortOrder.DESC)
                .limit(limit.toInt())
                .offset(offset)
                .map { it.toMetadataJob() }
        }
    }

    fun save(job: MetadataJob) {
        transaction(database) {
            KomfJobRecordTable.upsert {
                it[id] = job.id.value.toString()
                it[seriesId] = job.seriesId.value
                it[status] = job.status.name
                it[message] = job.message
                it[startedAt] = job.startedAt.toEpochMilliseconds()
                it[finishedAt] = job.finishedAt?.toEpochMilliseconds()
            }
        }
    }

    fun cancelAllRunning() {
        transaction(database) {
            KomfJobRecordTable.update({ KomfJobRecordTable.status.eq(MetadataJobStatus.RUNNING.name) }) {
                it[status] = MetadataJobStatus.FAILED.name
                it[message] = "Cancelled"
            }
        }
    }

    fun deleteAllBeforeDate(instant: Instant) {
        transaction(database) {
            KomfJobRecordTable.deleteWhere { KomfJobRecordTable.startedAt.lessEq(instant.toEpochMilliseconds()) }
        }
    }

    fun deleteAll() {
        transaction(database) {
            KomfJobRecordTable.deleteAll()
        }
    }

    private fun ResultRow.toMetadataJob(): MetadataJob {
        return MetadataJob(
            id = MetadataJobId(UUID.fromString(this[KomfJobRecordTable.id])),
            seriesId = MediaServerSeriesId(this[KomfJobRecordTable.seriesId]),
            status = MetadataJobStatus.valueOf(this[KomfJobRecordTable.status]),
            message = this[KomfJobRecordTable.message],
            startedAt = Instant.fromEpochMilliseconds(this[KomfJobRecordTable.startedAt]),
            finishedAt = this[KomfJobRecordTable.finishedAt]?.let { Instant.fromEpochMilliseconds(it) }
        )
    }
}
