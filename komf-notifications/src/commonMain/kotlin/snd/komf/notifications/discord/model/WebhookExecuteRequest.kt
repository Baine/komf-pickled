package snd.komf.notifications.discord.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebhookExecuteRequest(
    val content: String? = null,
    val username: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val tts: Boolean? = null,
    val embeds: Collection<Embed>? = null,
    val flags: Int? = null,
)

@Serializable
data class Embed(
    val title: String? = null,
    val type: String? = null,
    val description: String? = null,
    val url: String? = null,
    val timestamp: LocalDate? = null,
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
    @SerialName("proxy_icon_url")
    val proxyIconUrl: String? = null,
)

@Serializable
data class EmbedImage(
    val url: String,
    @SerialName("proxy_url")
    val proxyUrl: String? = null,
    val height: Int? = null,
    val width: Int? = null
)

@Serializable
data class EmbedField(
    val name: String,
    val value: String,
    val inline: Boolean,
)
