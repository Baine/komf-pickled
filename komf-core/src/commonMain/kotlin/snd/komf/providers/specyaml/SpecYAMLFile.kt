package snd.komf.providers.specyaml

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpecYAMLFile(
    @SerialName("Title") val title: String? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("Artist") val artists: List<String>? = null,
    @SerialName("Parody") val parodies: List<String>? = null,
    @SerialName("URL") val urls: Map<String, String>? = null,
    @SerialName("Tags") val tags: List<String>? = null,
    @SerialName("Released") val released: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("Publisher") val publisher: List<String>? = null,
    @SerialName("AgeRating") val ageRating: String? = null,
    @SerialName("ReadingDirection") val readingDirection: String? = null,
    @SerialName("Status") val status: String? = null,
)