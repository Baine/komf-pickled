package snd.komf.providers.german.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

private const val WIKI_API = "https://de.wikipedia.org/w/api.php"

class WikipediaDeSource(
    private val ktor: HttpClient,
) : GermanDataSource {
    override val source: DataSource = DataSource.WIKIPEDIA_DE
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun searchSeries(query: String, limit: Int): Collection<GermanSearchResult> {
        val response: String = ktor.get(WIKI_API) {
            url {
                parameters.append("action", "opensearch")
                parameters.append("search", query)
                parameters.append("limit", limit.toString())
                parameters.append("namespace", "0")
                parameters.append("format", "json")
            }
        }.body()
        val parsed = json.parseToJsonElement(response).jsonArray
        if (parsed.size < 3) return emptyList()
        val titles = parsed[1].jsonArray.map { it.jsonPrimitive.content }
        val descriptions = parsed[2].jsonArray.map { it.jsonPrimitive.content }
        val urls = parsed[3].jsonArray.map { it.jsonPrimitive.content }

        return titles.mapIndexedNotNull { i, title ->
            val pageName = urls.getOrNull(i)?.substringAfter("/wiki/")?.replace("_", " ") ?: return@mapIndexedNotNull null
            GermanSearchResult(
                id = GermanSeriesId(pageName),
                title = title,
                description = descriptions.getOrNull(i)?.ifBlank { null },
                source = source,
            )
        }
    }

    override suspend fun getSeries(seriesId: GermanSeriesId): GermanSeries? {
        val response: String = ktor.get(WIKI_API) {
            url {
                parameters.append("action", "query")
                parameters.append("prop", "extracts|pageimages|infobox")
                parameters.append("exintro", "1")
                parameters.append("explaintext", "1")
                parameters.append("titles", seriesId.value)
                parameters.append("format", "json")
                parameters.append("redirects", "1")
            }
        }.body()

        val parsed = json.parseToJsonElement(response).jsonObject
        val pages = parsed["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
        val page = pages.values.firstOrNull()?.jsonObject ?: return null
        if (page.contains("missing")) return null
        val title = page["title"]?.jsonPrimitive?.content ?: return null
        val extract = page["extract"]?.jsonPrimitive?.content
        val thumbnail = page["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content

        val extractLower = extract?.lowercase() ?: ""
        if (!extractLower.contains("manga") && !extractLower.contains("comic") &&
            !extractLower.contains("zeichner") && !extractLower.contains("autor")
        ) return null

        return GermanSeries(
            id = GermanSeriesId(title),
            title = title,
            description = extract?.take(1000),
            imageUrl = thumbnail,
            source = source,
        )
    }

    override suspend fun getSeriesCover(seriesId: GermanSeriesId): Image? {
        val response: String = ktor.get(WIKI_API) {
            url {
                parameters.append("action", "query")
                parameters.append("prop", "pageimages")
                parameters.append("titles", seriesId.value)
                parameters.append("format", "json")
                parameters.append("pithumbsize", "800")
            }
        }.body()

        val parsed = json.parseToJsonElement(response).jsonObject
        val pages = parsed["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
        val url = pages.values.firstOrNull()?.jsonObject?.get("thumbnail")?.jsonObject?.get("source")?.jsonPrimitive?.content ?: return null
        return runCatching { Image(ktor.get(url).body()) }.getOrNull()
    }

    override suspend fun getVolume(seriesId: GermanSeriesId, volumeId: String): GermanVolume? = null
    override suspend fun getVolumeCover(seriesId: GermanSeriesId, volumeId: String): Image? = null
}
