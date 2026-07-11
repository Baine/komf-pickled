package snd.komf.notifications.apprise

import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komf.model.Image
import snd.komf.notifications.NotificationContext
import java.net.URLConnection
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

private val logger = KotlinLogging.logger {}

// ponytail: replaces tika-core; komf-notifications targets only JVM
class AppriseCliService(
    private val urls: Collection<String>,
    private val templateRenderer: AppriseVelocityTemplates,
    private val seriesCover: Boolean
) {

    fun send(
        context: NotificationContext,
        templates: AppriseStringTemplates? = null,
    ) {
        var coverAttachment: Path? = null
        try {
            if (urls.isEmpty()) return

            coverAttachment = getCoverAttachment(context)

            val renderResult = templates
                ?.let { templateRenderer.render(context, it) }
                ?: templateRenderer.render(context)

            val arguments = listOfNotNull(
                renderResult.title?.let { "-t" to it },
                "-b" to renderResult.body,
                coverAttachment?.let { "--attach" to it.absolutePathString() },
            ).flatMap { (k, v) -> listOf(k, v) }.plus(urls)

            val process = ProcessBuilder("apprise", *arguments.toTypedArray())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            val statusCode = process.waitFor()
            if (statusCode != 0) {
                val errorMessage = process.errorStream.bufferedReader().readText()
                logger.error { errorMessage }
                error("Apprise returned non zero exit code: $errorMessage")
            }
        } finally {
            coverAttachment?.deleteIfExists()
        }
    }

    private fun getCoverAttachment(context: NotificationContext): Path? {
        if (!seriesCover) return null
        val cover = context.seriesCover ?: return null

        val tmpFile = createTempFile(
            prefix = "${context.series.name}_",
            suffix = "_${getFileExtension(cover)}"
        )
        tmpFile.writeBytes(cover.bytes)
        return tmpFile
    }

    private fun getFileExtension(image: Image): String {
        return image.mimeType?.extension()
            ?: runCatching {
                URLConnection.guessContentTypeFromStream(image.bytes.inputStream())?.extension()
            }.onFailure { logger.catching(it) }.getOrNull()
            ?: ".jpg"
    }
}

private fun String.extension(): String = when (lowercase().substringBefore(';').trim()) {
    "image/jpeg", "image/jpg" -> ".jpg"
    "image/png" -> ".png"
    "image/webp" -> ".webp"
    "image/gif" -> ".gif"
    else -> ".jpg"
}