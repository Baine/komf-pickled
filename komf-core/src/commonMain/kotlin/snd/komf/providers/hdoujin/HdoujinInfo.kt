package snd.komf.providers.hdoujin

data class HdoujinInfo(
    val title: String?,
    val description: String?,
    val tags: Map<String, List<String>>,
)
