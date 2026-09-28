package snd.komf.providers.german.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import snd.komf.model.Image
import snd.komf.providers.german.model.DataSource
import snd.komf.providers.german.model.GermanSearchResult
import snd.komf.providers.german.model.GermanSeries
import snd.komf.providers.german.model.GermanSeriesId
import snd.komf.providers.german.model.GermanSeriesVolume
import snd.komf.providers.german.model.GermanVolume
import snd.komf.providers.german.model.GermanVolumeId
import snd.komf.providers.german.model.Publisher
import snd.komf.providers.german.model.PublisherType

private const val MP_API = "https://api.manga-passion.de"

class MangaPassionSource(
    private val ktor: HttpClient,
) : GermanDataSource {
    override val source: DataSource = DataSource.MANGAPASSION_DE

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
            val id = obj.str("id") ?: return@mapNotNull null
            val title = obj.str("title") ?: return@mapNotNull null

            GermanSearchResult(
                id = GermanSeriesId(id),
                title = title,
                imageUrl = obj.str("cover"),
                publisher = obj["publishers"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.str("name"),
                source = source,
            )
        }
    }

    override suspend fun getSeries(seriesId: GermanSeriesId): GermanSeries? {
        val response: String = ktor.get("$MP_API/editions/${seriesId.value}.jsonld").body()
        val obj = json.parseToJsonElement(response).jsonObject

        val title = obj.str("title") ?: return null
        val numVolumes = obj.int("numVolumes")

        val publisher = obj["publishers"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.str("name")
            ?.let { Publisher(it, PublisherType.LOCALIZED) }

        val sources = obj["sources"]?.jsonArray ?: emptyList()
        val sourceObj = sources.firstOrNull()?.jsonObject
        val contributors = sourceObj?.get("contributors")?.jsonArray ?: emptyList()

        val authors = contributors.mapNotNull { c ->
            val name = c.jsonObject["contributor"]?.jsonObject?.str("name")
            if (name != null && c.jsonObject.int("role") == 0) name else null
        }
        val artists = contributors.mapNotNull { c ->
            val name = c.jsonObject["contributor"]?.jsonObject?.str("name")
            if (name != null && c.jsonObject.int("role") == 1) name else null
        }

        val altTitles = sources.mapNotNull { s -> s.jsonObject.str("romaji") }
        val tags = sourceObj?.get("tags")?.jsonArray?.mapNotNull { t -> t.jsonObject.str("name") } ?: emptyList()
        val startYear = sources.firstOrNull()?.jsonObject?.int("year")
        val volumes = fetchVolumesOfEdition(title)

        return GermanSeries(
            id = seriesId,
            title = title,
            alternativeTitles = altTitles,
            description = obj.str("description")?.take(2000),
            imageUrl = obj.str("cover"),
            publisher = publisher,
            authors = authors,
            artists = artists,
            genres = tags,
            ageRating = obj.int("ageRating"),
            numberOfVolumes = numVolumes,
            startYear = startYear,
            source = source,
            volumes = volumes,
        )
    }

    /**
     * Queries all volumes of an edition, paginated. Never null: an edition without
     * volumes simply yields an empty list.
     */
    private suspend fun fetchVolumesOfEdition(editionTitle: String): List<GermanSeriesVolume> {
        val all = mutableListOf<GermanSeriesVolume>()
        var page = 1
        while (true) {
            val response: String = ktor.get("$MP_API/volumes.jsonld") {
                parameter("edition.title", editionTitle)
                parameter("itemsPerPage", 100)
                parameter("order[arrangement]", "asc")
                parameter("page", page)
            }.body<String>()

            val member = parseVolumeCollection(response)
            if (member.isEmpty()) break
            all += member
            // hydra pages are 100 items; stop when a short page comes back
            if (member.size < 100) break
            page++
        }
        return all
    }

    override suspend fun getSeriesCover(seriesId: GermanSeriesId): Image? {
        val series = getSeries(seriesId) ?: return null
        val url = series.imageUrl ?: return null
        return runCatching { Image(ktor.get(url).body()) }.getOrNull()
    }

    override suspend fun getVolume(seriesId: GermanSeriesId, volumeId: String): GermanVolume? {
        val response: String = runCatching {
            ktor.get("$MP_API/volumes/${volumeId}.jsonld")
        }.getOrNull()?.body<String>() ?: return null

        return parseVolumeDetail(seriesId, response)
    }

    override suspend fun getVolumeCover(seriesId: GermanSeriesId, volumeId: String): Image? {
        val url = getVolume(seriesId, volumeId)?.imageUrl ?: return null
        return runCatching { Image(ktor.get(url).body()) }.getOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Safe string read: JSON `null` must yield Kotlin `null`, not the string "null"
         * (JsonNull.jsonPrimitive.content returns "null" — classic MP-API parsing trap).
         */
        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

        private fun JsonObject.int(key: String): Int? = str(key)?.toIntOrNull()

        internal fun parseVolumeCollection(response: String): List<GermanSeriesVolume> {
            val parsed = json.parseToJsonElement(response).jsonObject
            val member = parsed["hydra:member"]?.jsonArray ?: return emptyList()
            return member.mapNotNull { item ->
                val vol = item.jsonObject
                val id = vol.str("id") ?: return@mapNotNull null
                // `number` is null for special-edition volumes; `arrangement` is always present (1-based position)
                val number = vol.int("number")
                    ?: vol.int("arrangement")
                    ?: return@mapNotNull null
                GermanSeriesVolume(
                    id = GermanVolumeId(id),
                    number = number,
                    name = vol.str("title")?.takeIf { it.isNotBlank() },
                    // deliberately no edition: komf's book matching groups by edition and expects
                    // the edition title inside the filename's extra data, which won't hold for MP
                    type = vol.str("type"),
                )
            }
        }

        internal fun parseVolumeDetail(seriesId: GermanSeriesId, response: String): GermanVolume? {
            val obj = json.parseToJsonElement(response).jsonObject
            val id = obj.str("id") ?: return null

            return GermanVolume(
                id = GermanVolumeId(id),
                seriesId = seriesId,
                number = obj.int("number")
                    ?: obj.int("numberOverride")
                    ?: obj.int("arrangement")
                    ?: 0,
                title = obj.str("title")?.takeIf { it.isNotBlank() },
                description = obj.str("description")?.takeIf { it.isNotBlank() },
                // MP date is a LocalDateTime string; GermanVolume stores LocalDate
                releaseDate = obj.str("date")?.take(10)?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                },
                isbn = obj.str("isbn13") ?: obj.str("isbn10"),
                numberOfPages = obj.int("pages"),
                imageUrl = obj.str("cover"),
                source = DataSource.MANGAPASSION_DE,
            )
        }
    }
}
