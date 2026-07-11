package snd.komf.providers.german.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.LocalDate
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

private const val MANGADEX_API = "https://api.mangadex.org"

class MangaDexDeSource(
    private val ktor: HttpClient,
) : GermanDataSource {
    override val source: DataSource = DataSource.MANGADEX_DE
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun searchSeries(query: String, limit: Int): Collection<GermanSearchResult> {
        val response: String = ktor.get("$MANGADEX_API/manga") {
            parameter("title", query)
            parameter("limit", limit.coerceIn(1, 20))
            parameter("availableTranslatedLanguage[]", "de")
            parameter("contentRating[]", "safe")
            parameter("contentRating[]", "suggestive")
            parameter("contentRating[]", "erotica")
            parameter("includes[]", "cover_art")
        }.body()

        val parsed = json.parseToJsonElement(response).jsonObject
        val data = parsed["data"]?.jsonArray ?: return emptyList()

        return data.mapNotNull { item ->
            val obj = item.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null
            val titleObj = attrs["title"]?.jsonObject
            val deTitle = titleObj?.get("de")?.jsonPrimitive?.content
            val enTitle = titleObj?.get("en")?.jsonPrimitive?.content
            val title = deTitle ?: enTitle ?: return@mapNotNull null

            val descObj = attrs["description"]?.jsonObject
            val deDesc = descObj?.get("de")?.jsonPrimitive?.content

            val coverFileName = obj["relationships"]?.jsonArray?.firstOrNull { rel ->
                rel.jsonObject["type"]?.jsonPrimitive?.content == "cover_art"
            }?.jsonObject?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.content
            val imageUrl = coverFileName?.let { "https://uploads.mangadex.org/covers/$id/$it" }

            GermanSearchResult(
                id = GermanSeriesId(id),
                title = title,
                description = deDesc?.take(300),
                imageUrl = imageUrl,
                source = source,
            )
        }
    }

    override suspend fun getSeries(seriesId: GermanSeriesId): GermanSeries? {
        val response: String = ktor.get("$MANGADEX_API/manga/${seriesId.value}") {
            parameter("includes[]", "cover_art")
            parameter("includes[]", "author")
            parameter("includes[]", "artist")
        }.body()

        val parsed = json.parseToJsonElement(response).jsonObject
        val data = parsed["data"]?.jsonObject ?: return null
        val attrs = data["attributes"]?.jsonObject ?: return null

        val titleObj = attrs["title"]?.jsonObject
        val deTitle = titleObj?.get("de")?.jsonPrimitive?.content
        val enTitle = titleObj?.get("en")?.jsonPrimitive?.content
        val title = deTitle ?: enTitle ?: return null

        val descObj = attrs["description"]?.jsonObject
        val deDesc = descObj?.get("de")?.jsonPrimitive?.content
        val enDesc = descObj?.get("en")?.jsonPrimitive?.content
        val description = deDesc ?: enDesc

        val altTitles = attrs["altTitles"]?.jsonArray?.mapNotNull { alt ->
            alt.jsonObject?.get("de")?.jsonPrimitive?.content
        } ?: emptyList()

        val tags = attrs["tags"]?.jsonArray?.mapNotNull { tag ->
            tag.jsonObject?.get("attributes")?.jsonObject?.get("name")?.jsonObject?.get("de")?.jsonPrimitive?.content
        } ?: emptyList()

        val coverUrl = data["relationships"]?.jsonArray?.firstOrNull { rel ->
            rel.jsonObject["type"]?.jsonPrimitive?.content == "cover_art"
        }?.let { rel ->
            val coverId = rel.jsonObject["id"]?.jsonPrimitive?.content ?: return@let null
            val coverResponse = runCatching {
                ktor.get("$MANGADEX_API/cover/$coverId").body<String>()
            }.getOrNull() ?: return@let null
            val coverParsed = json.parseToJsonElement(coverResponse).jsonObject
            val fileName = coverParsed["data"]?.jsonObject?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.content ?: return@let null
            "https://uploads.mangadex.org/covers/$seriesId/$fileName"
        }

        return GermanSeries(
            id = seriesId,
            title = title,
            alternativeTitles = altTitles,
            description = description?.take(2000),
            imageUrl = coverUrl,
            genres = tags,
            source = source,
        )
    }

    override suspend fun getSeriesCover(seriesId: GermanSeriesId): Image? {
        val series = getSeries(seriesId) ?: return null
        val url = series.imageUrl ?: return null
        return runCatching { Image(ktor.get(url).body()) }.getOrNull()
    }

    override suspend fun getVolume(seriesId: GermanSeriesId, volumeId: String): GermanVolume? {
        val response: String = ktor.get("$MANGADEX_API/manga/${seriesId.value}/aggregate") {
            parameter("translatedLanguage[]", "de")
        }.body()

        val parsed = json.parseToJsonElement(response).jsonObject
        val volumes = parsed["volumes"]?.jsonObject ?: return null
        val volKey = volumes.keys.find { key ->
            key.toIntOrNull()?.toString() == volumeId || key == volumeId
        } ?: return null
        val volData = volumes[volKey]?.jsonObject ?: return null
        val chapters = volData["chapters"]?.jsonArray ?: return null

        return GermanVolume(
            id = GermanVolumeId(volumeId),
            seriesId = seriesId,
            number = volumeId.toIntOrNull() ?: 0,
            source = source,
        )
    }

    override suspend fun getVolumeCover(seriesId: GermanSeriesId, volumeId: String): Image? = null
}
