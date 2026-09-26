package snd.komf.app

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.CompressedFileType
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.sse.SSE
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import snd.komf.api.KomfErrorResponse
import snd.komf.app.api.ConfigRoutes
import snd.komf.app.api.JobRoutes
import snd.komf.app.api.MangaBakaRoutes
import snd.komf.app.api.MediaServerRoutes
import snd.komf.app.api.MetadataRoutes
import snd.komf.app.api.NotificationRoutes
import snd.komf.app.config.AppConfig
import snd.komf.mangabaka.external.MangaBakaDbDownloader
import snd.komf.mangabaka.repository.MangaBakaRepository
import snd.komf.mediaserver.MediaServerClient
import snd.komf.mediaserver.MetadataServiceProvider
import snd.komf.mediaserver.jobs.KomfJobTracker
import snd.komf.mediaserver.jobs.repository.KomfJobsRepository
import snd.komf.notifications.apprise.AppriseCliService
import snd.komf.notifications.apprise.AppriseVelocityTemplates
import snd.komf.notifications.discord.DiscordVelocityTemplates
import snd.komf.notifications.discord.DiscordWebhookService
import snd.komf.providers.bookwalker.db.BookWalkerDbDownloader

private val logger = KotlinLogging.logger { }

class ServerModule(
    serverPort: Int,
    private val onConfigUpdate: suspend (AppConfig) -> Unit,
    private val dependencies: StateFlow<ApiRouteDependencies>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    private val server = embeddedServer(CIO, port = serverPort) {
        install(ContentNegotiation) {
            json(json)
        }
        install(CORS) {
            anyMethod()
            allowHeaders { true }
            anyHost()
            allowNonSimpleContentTypes = true
        }
        install(SSE)
        install(DefaultHeaders) {
            header("Cross-Origin-Embedder-Policy", "require-corp")
            header("Cross-Origin-Opener-Policy", "same-origin")
        }

        install(CachingHeaders)
        install(StatusPages) {
            exception<IllegalStateException> { call, cause ->
                logger.catching(cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    KomfErrorResponse("${cause::class.simpleName} :${cause.message}")
                )
            }
            exception<IllegalArgumentException> { call, cause ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    KomfErrorResponse("${cause::class.simpleName} :${cause.message}")
                )
            }

            exception<Throwable> { call, cause ->
                logger.catching(cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    KomfErrorResponse("${cause::class.simpleName} :${cause.message}")
                )
            }
        }

        val wasmContentType = ContentType("application", "wasm")
        val komeliaResourcePath = "/komelia/"

        routing {
            get("/{name}.wasm") {
                val name = call.parameters["name"] ?: return@get
                val resource = this::class.java.getResource("$komeliaResourcePath$name.wasm")
                if (resource != null) {
                    call.respondBytes(resource.readBytes(), wasmContentType)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            staticResources(remotePath = "/", basePackage = "komelia", index = "index.html") {
                default("index.html")
                preCompressed(CompressedFileType.GZIP)
            }

            route("/api") {
                ConfigRoutes(
                    config = dependencies.map { it.config },
                    onConfigUpdate = onConfigUpdate,
                    mangaBakaDownloader = dependencies.map { it.mangaBakaDownloader },
                    mangaBakaRepository = dependencies.map { it.mangaBakaRepository },
                    bookWalkerDbDownloader = dependencies.map { it.bookWalkerDbDownloader },
                    json = json,
                ).registerRoutes(this)
                JobRoutes(
                    jobTracker = dependencies.map { it.jobTracker },
                    jobsRepository = dependencies.map { it.jobsRepository },
                    json = json
                ).registerRoutes(this)

                NotificationRoutes(
                    discordService = dependencies.map { it.discordService },
                    discordRenderer = dependencies.map { it.discordRenderer },
                    appriseService = dependencies.map { it.appriseService },
                    appriseRenderer = dependencies.map { it.appriseRenderer }
                ).registerRoutes(this)

                val currentDeps = dependencies.value
                if (currentDeps.komgaMediaServerClient != null && currentDeps.komgaMetadataServiceProvider != null) {
                    route("/komga") {
                        MetadataRoutes(
                            metadataServiceProvider = dependencies.map { it.komgaMetadataServiceProvider },
                            mediaServerClient = dependencies.map { it.komgaMediaServerClient },
                        ).registerRoutes(this)

                        MediaServerRoutes(
                            mediaServerClient = dependencies.map { it.komgaMediaServerClient }
                        ).registerRoutes(this)
                    }
                }

                route("/kavita") {
                    MetadataRoutes(
                        metadataServiceProvider = dependencies.map { it.kavitaMetadataServiceProvider },
                        mediaServerClient = dependencies.map { it.kavitaMediaServerClient },
                    ).registerRoutes(this)

                    MediaServerRoutes(
                        mediaServerClient = dependencies.map { it.kavitaMediaServerClient }
                    ).registerRoutes(this)
                }

                MangaBakaRoutes(
                    mangaBakaRepository = dependencies.map { it.mangaBakaRepository },
                    httpClient = dependencies.map { it.httpClient }
                ).registerRoutes(this)

            }
        }
    }

    fun startServer() {
        server.start(wait = true)
    }
}

class ApiRouteDependencies(
    val config: AppConfig,
    val jobTracker: KomfJobTracker,
    val jobsRepository: KomfJobsRepository,
    val komgaMediaServerClient: MediaServerClient?,
    val komgaMetadataServiceProvider: MetadataServiceProvider?,
    val kavitaMediaServerClient: MediaServerClient?,
    val kavitaMetadataServiceProvider: MetadataServiceProvider?,
    val discordService: DiscordWebhookService,
    val discordRenderer: DiscordVelocityTemplates,
    val appriseService: AppriseCliService,
    val appriseRenderer: AppriseVelocityTemplates,
    val mangaBakaDownloader: MangaBakaDbDownloader,
    val bookWalkerDbDownloader: BookWalkerDbDownloader,
    val mangaBakaRepository: MangaBakaRepository,
    val httpClient: HttpClient,
)
