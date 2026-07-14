package snd.komf.providers.schalenetwork

import com.fleeksoft.ksoup.Ksoup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.char
import snd.komf.providers.schalenetwork.model.SchaleNetworkId
import snd.komf.providers.schalenetwork.model.SchaleNetworkMetadata

private val DATE_FORMAT = LocalDate.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    day()
}

class SchaleNetworkParser {

    fun parse(html: String, id: SchaleNetworkId): SchaleNetworkMetadata {
        val document = Ksoup.parse(html)

        val title = document.getElementsByTag("h1")
            .firstOrNull()?.text()?.trim()
            ?: throw IllegalStateException("SchaleNetwork page has no title: ${id.url()}")

        val thumbnailUrl = document.selectFirst("figure a img")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val tags = parseTags(document)
        val releasedAt = parseReleaseDate(document)

        return SchaleNetworkMetadata(
            id = id.id,
            key = id.key,
            title = title,
            thumbnailUrl = thumbnailUrl,
            tags = tags,
            releasedAt = releasedAt,
        )
    }

    private fun parseTags(document: com.fleeksoft.ksoup.nodes.Document): Map<String, List<String>> =
        document.select("div.tags > div.flex")
            .flatMap { block ->
                val label = block.selectFirst("span.shrink-0")?.text()?.trim()?.lowercase() ?: return@flatMap emptyList()
                block.select("a[rel=tag]").mapNotNull { link ->
                    val value = link.selectFirst("span")?.text()?.trim() ?: link.text().trim()
                    if (value.isEmpty()) return@mapNotNull null
                    val namespace = when (label) {
                        "tags" -> parseNamespaceFromHref(link.attr("href")) ?: "tag"
                        else -> label
                    }
                    namespace to value
                }
            }
            .groupBy({ it.first }, { it.second })

    private fun parseNamespaceFromHref(href: String): String? {
        val match = Regex("/tag/([^:]+):").find(href) ?: return null
        return match.groupValues[1]
    }

    @Suppress("DEPRECATION")
    private fun parseReleaseDate(document: com.fleeksoft.ksoup.nodes.Document): LocalDate? {
        val dateText = document.select("div.meta div.top span")
            .map { it.text() }
            .firstOrNull { it.matches(Regex("^\\d{4}-\\d{2}-\\d{2}.*")) }
            ?: return null
        return runCatching { LocalDate.parse(dateText.take(10), DATE_FORMAT) }.getOrNull()
    }
}
