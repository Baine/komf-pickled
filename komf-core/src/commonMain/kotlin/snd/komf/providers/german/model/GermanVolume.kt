package snd.komf.providers.german.model

import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

@JvmInline
value class GermanVolumeId(val value: String)

data class GermanVolume(
    val id: GermanVolumeId,
    val seriesId: GermanSeriesId,
    val number: Int,
    val title: String? = null,
    val description: String? = null,
    val releaseDate: LocalDate? = null,
    val isbn: String? = null,
    val numberOfPages: Int? = null,
    val imageUrl: String? = null,
    val source: DataSource,
)
