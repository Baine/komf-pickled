package snd.komf.providers.schalenetwork.model

private val SOURCE_REGEX = Regex("^SchaleNetwork:/g/(\\d+)/([^/]+)$", RegexOption.IGNORE_CASE)

@JvmInline
value class SchaleNetworkSource(val value: String) {
    fun parse(): SchaleNetworkId? {
        val match = SOURCE_REGEX.matchEntire(value.trim()) ?: return null
        return SchaleNetworkId(id = match.groupValues[1], key = match.groupValues[2])
    }
}

data class SchaleNetworkId(val id: String, val key: String) {
    fun url(): String = "https://schale.network/g/$id/$key"
}
