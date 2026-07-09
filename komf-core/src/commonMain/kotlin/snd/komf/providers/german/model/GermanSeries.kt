package snd.komf.providers.german.model

import kotlin.jvm.JvmInline

@JvmInline
value class GermanSeriesId(val value: String)

data class GermanSeries(
    val id: GermanSeriesId,
    val title: String,
    val alternativeTitles: Collection<String> = emptyList(),
    val description: String? = null,
    val imageUrl: String? = null,
    val publisher: Publisher? = null,
    val authors: Collection<String> = emptyList(),
    val artists: Collection<String> = emptyList(),
    val genres: Collection<String> = emptyList(),
    val ageRating: Int? = null,
    val status: String? = null,
    val numberOfVolumes: Int? = null,
    val startYear: Int? = null,
    val score: Double? = null,
    val source: DataSource,
    val volumes: Collection<GermanSeriesVolume> = emptyList(),
)

data class GermanSeriesVolume(
    val id: GermanVolumeId,
    val number: Int,
    val name: String? = null,
    val edition: String? = null,
    val type: String? = null,
)

data class Publisher(
    val name: String,
    val type: PublisherType,
)

enum class PublisherType {
    LOCALIZED
}
