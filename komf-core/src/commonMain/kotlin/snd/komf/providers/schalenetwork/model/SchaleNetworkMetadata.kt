package snd.komf.providers.schalenetwork.model

import kotlinx.datetime.LocalDate

data class SchaleNetworkMetadata(
    val id: String,
    val key: String,
    val title: String,
    val thumbnailUrl: String?,
    val tags: Map<String, List<String>>,
    val releasedAt: LocalDate?,
)
