package snd.komf.providers.gallerydl

data class GalleryDLInfo(
    val title: String? = null,
    val tags: Map<String, List<String>> = emptyMap(),
    val artists: List<String> = emptyList(),
    val language: String? = null,
    val description: String? = null,
    val url: String? = null,
    val source: String? = null,
)
