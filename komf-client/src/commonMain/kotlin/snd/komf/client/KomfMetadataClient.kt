package snd.komf.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import snd.komf.api.KomfCoreProviders
import snd.komf.api.KomfProviderSeriesId
import snd.komf.api.KomfProviders
import snd.komf.api.KomfServerLibraryId
import snd.komf.api.KomfServerSeriesId
import snd.komf.api.MediaServer
import snd.komf.api.UnknownKomfProvider
import snd.komf.api.metadata.KomfIdentifyRequest
import snd.komf.api.metadata.KomfMetadataJobResponse
import snd.komf.api.metadata.KomfMetadataSeriesSearchResult

class KomfMetadataClient(
    private val ktor: HttpClient,
    mediaServer: MediaServer
) {
    private val metadataApiPrefix = "/api/${mediaServer.name.lowercase()}/metadata"

    suspend fun getProviders(): List<String> {
        return ktor.get("/api/metadata/providers").body()
    }

    suspend fun searchSeries(
        name: String,
        libraryId: KomfServerLibraryId? = null,
        seriesId: KomfServerSeriesId? = null
    ): List<KomfMetadataSeriesSearchResult> {
        return ktor.get("$metadataApiPrefix/search") {
            parameter("name", name)
            libraryId?.let { parameter("libraryId", it.value) }
            seriesId?.let { parameter("seriesId", it.value) }
        }.body()
    }

    suspend fun getSeriesCover(
        libraryId: KomfServerLibraryId,
        provider: KomfProviders,
        providerSeriesId: KomfProviderSeriesId
    ): ByteArray? {
        return try {
            ktor.get("$metadataApiPrefix/series-cover") {
                parameter("libraryId", libraryId.value)
                parameter(
                    "provider", when (provider) {
                        is UnknownKomfProvider -> provider.name
                        else -> provider.toString()
                    }
                )
                parameter("providerSeriesId", providerSeriesId.value)
            }.body()

        } catch (exception: ClientRequestException) {
            if (exception.response.status == HttpStatusCode.NotFound) {
                null
            } else {
                throw exception
            }
        }
    }

    suspend fun identifySeries(request: KomfIdentifyRequest): KomfMetadataJobResponse {
        return ktor.post("$metadataApiPrefix/identify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun matchSeries(
        libraryId: KomfServerLibraryId,
        seriesId: KomfServerSeriesId,
        force: Boolean = false
    ): KomfMetadataJobResponse {
        return ktor.post("$metadataApiPrefix/match/library/${libraryId.value}/series/${seriesId.value}") {
            parameter("force", force)
        }.body()
    }

    suspend fun matchLibrary(libraryId: KomfServerLibraryId, force: Boolean = false) {
        ktor.post("$metadataApiPrefix/match/library/${libraryId.value}") {
            parameter("force", force)
        }
    }

    suspend fun resetSeries(
        libraryId: KomfServerLibraryId,
        seriesId: KomfServerSeriesId,
        removeComicInfo: Boolean = false
    ) {
        ktor.post("$metadataApiPrefix/reset/library/${libraryId.value}/series/${seriesId.value}") {
            parameter("removeComicInfo", removeComicInfo)
        }
    }

    suspend fun resetLibrary(
        libraryId: KomfServerLibraryId,
        removeComicInfo: Boolean = false
    ) {
        ktor.post("$metadataApiPrefix/reset/library/${libraryId.value}") {
            parameter("removeComicInfo", removeComicInfo)
        }
    }
}