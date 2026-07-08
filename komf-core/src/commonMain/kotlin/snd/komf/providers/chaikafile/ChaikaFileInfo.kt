package snd.komf.providers.chaikafile

data class ChaikaFileInfo(
    val title: String? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val download: String? = null,
    val gallery: String? = null,
    val id: String? = null,
    val posted: String? = null,
)
