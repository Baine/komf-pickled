package snd.komf.notifications.discord.model

import kotlinx.serialization.Serializable


@Serializable
data class Webhook(
    val id: String,
    val token: String,
)
