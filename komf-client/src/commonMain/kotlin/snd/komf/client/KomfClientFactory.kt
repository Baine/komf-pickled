package snd.komf.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import snd.komf.api.MediaServer

// ponytail: replaced private constructor + Builder with public constructor using default arguments
class KomfClientFactory(
    ktor: HttpClient? = null,
    baseUrl: () -> String = { "http://localhost:8085" },
    cookieStorage: CookiesStorage? = AcceptAllCookiesStorage(),
    json: Json = Json,
) {
    private val json = Json(json) {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val baseUrl: () -> String = baseUrl

    private val ktor: HttpClient = (ktor ?: HttpClient()).config {
        expectSuccess = true
        cookieStorage?.let { install(HttpCookies) { storage = it } }
        defaultRequest { url(baseUrl()) }
        install(ContentNegotiation) { json(json) }
        install(SSE)
    }

    fun configClient() = KomfConfigClient(ktor, json)
    fun metadataClient(mediaServer: MediaServer) = KomfMetadataClient(ktor, mediaServer)
    fun mediaServerClient(mediaServer: MediaServer) = KomfMediaServerClient(ktor, mediaServer)
    fun jobClient() = KomfJobClient(ktor = ktor, json = json)
    fun notificationClient() = KomfNotificationClient(ktor = ktor)
}
