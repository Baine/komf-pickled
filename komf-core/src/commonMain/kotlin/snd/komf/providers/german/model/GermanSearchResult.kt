package snd.komf.providers.german.model

data class GermanSearchResult(
    val id: GermanSeriesId,
    val title: String,
    val alternativeTitle: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val publisher: String? = null,
    val volumesNumber: Int? = null,
    val score: Double? = null,
    val source: DataSource,
)
