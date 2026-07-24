package snd.komf.notifications.discord.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebhookExecuteRequest(
    val embeds: Collection<Embed>? = null,
)

@Serializable
data class Embed(
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val color: Int? = null,
    val footer: EmbedFooter? = null,
    val image: EmbedImage? = null,
    val fields: Collection<EmbedField>? = null,
)

@Serializable
data class EmbedFooter(
    val text: String,
    @SerialName("icon_url")
    val iconUrl: String? = null,
)

@Serializable
data class EmbedImage(
    val url: String,
)

@Serializable
data class EmbedField(
    val name: String,
    val value: String,
    val inline: Boolean,
)
