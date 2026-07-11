package snd.komf.mediaserver.kavita

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.Base64
import kotlin.time.Instant

private val base64Decoder = Base64.getUrlDecoder()

// ponytail: replaces jose4j and the stateless JvmJwtConsumer class
fun processToExpirationDateClaim(jwt: String): Instant {
    val parts = jwt.split(".")
    require(parts.size == 3) { "Invalid JWT format" }
    val payload = base64Decoder.decode(parts[1]).decodeToString()
    val exp = Json.parseToJsonElement(payload).jsonObject["exp"]?.jsonPrimitive?.long
        ?: error("JWT does not contain exp claim")
    return Instant.fromEpochSeconds(exp)
}
