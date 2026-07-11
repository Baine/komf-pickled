package snd.komf.notifications.discord.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Webhook(
    val type: Int,
    val id: String,
    val name: String,
    @SerialName("channel_id")
    val channelId: String,
    val token: String,
)
