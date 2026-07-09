package snd.komf.providers.german.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import snd.komf.model.Image
import snd.komf.providers.german.model.DataSource
import snd.komf.providers.german.model.GermanSearchResult
import snd.komf.providers.german.model.GermanSeries
import snd.komf.providers.german.model.GermanSeriesId
import snd.komf.providers.german.model.GermanVolume
import snd.komf.providers.german.model.GermanVolumeId
import snd.komf.providers.german.model.Publisher
import snd.komf.providers.german.model.PublisherType

private const val MP_API = "https://api.manga-passion.de"

class MangaPassionSource(
    private val ktor: HttpClient,
) : GermanDataSource {
    override val source: DataSource = DataSource.MANGAPASSION_DE
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun searchSeries(query: String, limit: Int): Collection<GermanSearchResult> {
        val response: String = ktor.get("$MP_API/editions.jsonld") {
            parameter("title", query)
            parameter("itemsPerPage", limit.coerceIn(1, 20))
            parameter("format[]", "0")
        }.body()

        val parsed = json.parseToJsonElement(response).jsonObject
        val member = parsed["hydra:member"]?.jsonArray ?: return emptyList()

        return member.mapNotNull { item ->
            val obj = item.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val cover = obj["cover"]?.jsonPrimitive?.content
            val publisher = obj["publishers"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("name")?.jsonPrimitive?.content

            GermanSearchResult(
                id = GermanSeriesId(id),
                title = title,
                imageUrl = cover,
                publisher = publisher,
                source = source,
            )
        }
    }

    override suspend fun getSeries(seriesId: GermanSeriesId): GermanSeries? {
        val response: String = ktor.get("$MP_API/editions/${seriesId.value}.jsonld").body()
        val obj = json.parseToJsonElement(response).jsonObject

        val title = obj["title"]?.jsonPrimitive?.content ?: return null
        val description = obj["description"]?.jsonPrimitive?.content
        val cover = obj["cover"]?.jsonPrimitive?.content
        val numVolumes = obj["numVolumes"]?.jsonPrimitive?.content?.toIntOrNull()

        val publisher = obj["publishers"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("name")?.jsonPrimitive?.content
            ?.let { Publisher(it, PublisherType.LOCALIZED) }

        val sources = obj["sources"]?.jsonArray ?: emptyList()
        val sourceObj = sources.firstOrNull()?.jsonObject
        val contributors = sourceObj?.get("contributors")?.jsonArray ?: emptyList()

        val authors = contributors.mapNotNull { c ->
            val role = c.jsonObject["role"]?.jsonPrimitive?.content?.toIntOrNull()
            val name = c.jsonObject["contributor"]?.jsonObject?.get("name")?.jsonPrimitive?.content
            if (name != null && role == 0) name else null
        }
        val artists = contributors.mapNotNull { c ->
            val role = c.jsonObject["role"]?.jsonPrimitive?.content?.toIntOrNull()
            val name = c.jsonObject["contributor"]?.jsonObject?.get("name")?.jsonPrimitive?.content
            if (name != null && role == 1) name else null
        }

        val altTitles = sources.mapNotNull { s ->
            s.jsonObject["romaji"]?.jsonPrimitive?.content
        }.filter { it.isNotBlank() }

        val tags = sourceObj?.get("tags")?.jsonArray?.mapNotNull { t ->
            t.jsonObject["name"]?.jsonPrimitive?.content
        } ?: emptyList()

        return GermanSeries(
            id = seriesId,
            title = title,
            alternativeTitles = altTitles,
            description = description?.take(2000),
            imageUrl = cover,
            publisher = publisher,
            authors = authors,
            artists = artists,
            genres = tags,
            numberOfVolumes = numVolumes,
            source = source,
        )
    }

    override suspend fun getSeriesCover(seriesId: GermanSeriesId): Image? {
        val series = getSeries(seriesId) ?: return null
        val url = series.imageUrl ?: return null
        return runCatching { Image(ktor.get(url).body()) }.getOrNull()
    }

    override suspend fun getVolume(seriesId: GermanSeriesId, volumeId: String): GermanVolume? = null
    override suspend fun getVolumeCover(seriesId: GermanSeriesId, volumeId: String): Image? = null
}
