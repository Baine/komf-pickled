package snd.komf.providers.schalenetwork

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import snd.komf.model.Image
import snd.komf.providers.schalenetwork.model.SchaleNetworkId
import snd.komf.providers.schalenetwork.model.SchaleNetworkMetadata

private val logger = KotlinLogging.logger {}

class SchaleNetworkClient(private val ktor: HttpClient) {
    private val parser = SchaleNetworkParser()

    suspend fun getMetadata(id: SchaleNetworkId): SchaleNetworkMetadata {
        val document = ktor.get(id.url()).bodyAsText()
        return parser.parse(document, id)
    }

    suspend fun getThumbnail(url: String): Image? {
        return runCatching {
            val bytes: ByteArray = ktor.get(url).body()
            Image(bytes)
        }.onFailure { logger.warn(it) { "Failed to fetch SchaleNetwork thumbnail $url" } }
            .getOrNull()
    }
}
