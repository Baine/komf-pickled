package snd.komf.mediaserver

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import snd.komf.comicinfo.ComicInfoWriter
import snd.komf.mediaserver.config.DatabaseConfig
import snd.komf.mediaserver.config.KavitaConfig
import snd.komf.mediaserver.config.KomgaConfig
import snd.komf.mediaserver.config.MetadataProcessingConfig
import snd.komf.mediaserver.config.MetadataUpdateConfig
import snd.komf.mediaserver.db.BookThumbnailTable
import snd.komf.mediaserver.db.KomfJobRecordTable
import snd.komf.mediaserver.db.SeriesMatchTable
import snd.komf.mediaserver.db.SeriesThumbnailTable
import snd.komf.mediaserver.jobs.KomfJobTracker
import snd.komf.mediaserver.jobs.KomfJobsRepository
import snd.komf.mediaserver.kavita.KavitaAuthClient
import snd.komf.mediaserver.kavita.KavitaClient
import snd.komf.mediaserver.kavita.KavitaEventHandler
import snd.komf.mediaserver.kavita.KavitaMediaServerClientAdapter
import snd.komf.mediaserver.kavita.KavitaTokenProvider
import snd.komf.mediaserver.komga.KomgaEventHandler
import snd.komf.mediaserver.komga.KomgaMediaServerClientAdapter
import snd.komf.mediaserver.metadata.MetadataEventHandler
import snd.komf.mediaserver.metadata.MetadataMapper
import snd.komf.mediaserver.metadata.MetadataMerger
import snd.komf.mediaserver.metadata.MetadataPostProcessor
import snd.komf.mediaserver.metadata.MetadataService
import snd.komf.mediaserver.metadata.MetadataUpdater
import snd.komf.mediaserver.metadata.repository.BookThumbnailsRepository
import snd.komf.mediaserver.metadata.repository.SeriesMatchRepository
import snd.komf.mediaserver.metadata.repository.SeriesThumbnailsRepository
import snd.komf.mediaserver.model.MediaServer
import snd.komf.notifications.apprise.AppriseCliService
import snd.komf.notifications.discord.DiscordWebhookService
import snd.komf.providers.ProvidersModule
import snd.komga.client.KomgaClientFactory
import java.nio.file.Path
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

class MediaServerModule(
    komgaConfig: KomgaConfig,
    kavitaConfig: KavitaConfig,
    databaseConfig: DatabaseConfig,
    jsonBase: Json,
    ktorBaseClient: HttpClient,
    appriseService: AppriseCliService,
    discordWebhookService: DiscordWebhookService,
    private val metadataProviders: ProvidersModule.MetadataProviders,
) {
    private val mediaServerDatabase = createDatabase(Path.of(databaseConfig.file))
    val jobRepository = KomfJobsRepository(mediaServerDatabase)
    val jobTracker = KomfJobTracker(jobRepository)

    val komgaClient: KomgaMediaServerClientAdapter?
    val komgaMetadataServiceProvider: MetadataServiceProvider?
    private val komgaEventHandler: KomgaEventHandler?

    val kavitaMediaServerClient: KavitaMediaServerClientAdapter?
    val kavitaMetadataServiceProvider: MetadataServiceProvider?
    private val kavitaEventHandler: KavitaEventHandler?

    init {
        val komgaEnabled = komgaConfig.baseUri.isNotBlank()
        val kavitaEnabled = kavitaConfig.baseUri.isNotBlank()

        if (komgaEnabled) {
            val komgaClientFactory = KomgaClientFactory.Builder()
                .ktor(ktorBaseClient)
                .cookieStorage(AcceptAllCookiesStorage())
                .username(komgaConfig.komgaUser)
                .password(komgaConfig.komgaPassword)
                .baseUrlBuilder { URLBuilder(komgaConfig.baseUri).appendPathSegments("/") }
                .useragent("Snd-R/komf (https://github.com/Snd-R/komf)")
                .build()
            val komgaAdapter = KomgaMediaServerClientAdapter(
                komgaClientFactory.bookClient(),
                komgaClientFactory.seriesClient(),
                komgaClientFactory.libraryClient(),
                komgaConfig.thumbnailSizeLimit
            )
            komgaClient = komgaAdapter
            val komgaBookThumbnailRepository = BookThumbnailsRepository(
                mediaServerDatabase,
                MediaServer.KOMGA
            )
            val komgaSerThumbnailsRepository = SeriesThumbnailsRepository(
                mediaServerDatabase,
                MediaServer.KOMGA
            )
            val komgaSeriesMatchRepository = SeriesMatchRepository(
                mediaServerDatabase,
                MediaServer.KOMGA
            )
            val komgaProvider = createMetadataServiceProvider(
                config = komgaConfig.metadataUpdate,
                mediaServerClient = komgaAdapter,
                seriesThumbnailsRepository = komgaSerThumbnailsRepository,
                bookThumbnailsRepository = komgaBookThumbnailRepository,
                seriesMatchRepository = komgaSeriesMatchRepository,
            )
            komgaMetadataServiceProvider = komgaProvider

            val komgaMetadataEventHandler = MetadataEventHandler(
                metadataServiceProvider = komgaProvider,
                bookThumbnailsRepository = komgaBookThumbnailRepository,
                seriesThumbnailsRepository = komgaSerThumbnailsRepository,
                seriesMatchRepository = komgaSeriesMatchRepository,
                jobTracker = jobTracker,
                libraryFilter = {
                    val libraries = komgaConfig.eventListener.metadataLibraryFilter
                    if (libraries.isEmpty()) true
                    else libraries.contains(it)
                },
                seriesFilter = { seriesId -> komgaConfig.eventListener.metadataSeriesExcludeFilter.none { seriesId == it } },
            )
            val komgaNotificationsHandler = NotificationsEventHandler(
                mediaServerClient = komgaAdapter,
                appriseService = appriseService,
                discordWebhookService = discordWebhookService,
                libraryFilter = {
                    val libraries = komgaConfig.eventListener.notificationsLibraryFilter
                    if (libraries.isEmpty()) true
                    else libraries.contains(it)
                },
                mediaServer = MediaServer.KOMGA
            )

            komgaEventHandler = KomgaEventHandler(
                eventSourceFactory = { komgaClientFactory.sseSession() },
                eventListeners = listOfNotNull(komgaMetadataEventHandler, komgaNotificationsHandler),
            )
            if (komgaConfig.eventListener.enabled) {
                komgaEventHandler.start()
            }
        } else {
            komgaClient = null
            komgaMetadataServiceProvider = null
            komgaEventHandler = null
            logger.info { "Komga is disabled (baseUri is blank)" }
        }

        if (kavitaEnabled) {
            val kavitaBookThumbnailRepository = BookThumbnailsRepository(
                mediaServerDatabase,
                MediaServer.KAVITA
            )
            val kavitaSerThumbnailsRepository = SeriesThumbnailsRepository(
                mediaServerDatabase,
                MediaServer.KAVITA
            )
            val kavitaSeriesMatchRepository = SeriesMatchRepository(
                mediaServerDatabase,
                MediaServer.KAVITA
            )
            val kavitaKtorBase = ktorBaseClient.config {
                defaultRequest {
                    url {
                        this.takeFrom(io.ktor.http.URLBuilder(kavitaConfig.baseUri).appendPathSegments("/"))
                    }
                }
                install(ContentNegotiation) { json(jsonBase) }
            }
            val kavitaAuthClient = KavitaAuthClient(kavitaKtorBase)
            val kavitaTokenProvider = KavitaTokenProvider(
                kavitaClient = kavitaAuthClient,
                apiKey = kavitaConfig.apiKey,
                clock = Clock.System
            )
            val kavitaKtorClient = kavitaKtorBase.config {
                install(Auth) {
                    bearer { loadTokens { BearerTokens(kavitaTokenProvider.getToken(), null) } }
                }
            }
            val kavitaClientInstance = KavitaClient(kavitaKtorClient, jsonBase, kavitaConfig.apiKey)
            val kavitaAdapter = KavitaMediaServerClientAdapter(kavitaClientInstance)
            kavitaMediaServerClient = kavitaAdapter
            val kavitaProvider = createMetadataServiceProvider(
                config = kavitaConfig.metadataUpdate,
                mediaServerClient = kavitaAdapter,
                seriesThumbnailsRepository = kavitaSerThumbnailsRepository,
                bookThumbnailsRepository = kavitaBookThumbnailRepository,
                seriesMatchRepository = kavitaSeriesMatchRepository,
            )
            kavitaMetadataServiceProvider = kavitaProvider

            val kavitaMetadataEventHandler = MetadataEventHandler(
                metadataServiceProvider = kavitaProvider,
                bookThumbnailsRepository = kavitaBookThumbnailRepository,
                seriesThumbnailsRepository = kavitaSerThumbnailsRepository,
                seriesMatchRepository = kavitaSeriesMatchRepository,
                jobTracker = jobTracker,
                libraryFilter = {
                    val libraries = kavitaConfig.eventListener.metadataLibraryFilter
                    if (libraries.isEmpty()) true
                    else libraries.contains(it)
                },
                seriesFilter = { seriesId -> kavitaConfig.eventListener.metadataSeriesExcludeFilter.none { seriesId == it } },
            )
            val kavitaNotificationsHandler = NotificationsEventHandler(
                mediaServerClient = kavitaAdapter,
                appriseService = appriseService,
                discordWebhookService = discordWebhookService,
                libraryFilter = {
                    val libraries = kavitaConfig.eventListener.notificationsLibraryFilter
                    if (libraries.isEmpty()) true
                    else libraries.contains(it)
                },
                mediaServer = MediaServer.KAVITA
            )

            kavitaEventHandler = KavitaEventHandler(
                baseUrl = io.ktor.http.URLBuilder(kavitaConfig.baseUri),
                kavitaClient = kavitaClientInstance,
                tokenProvider = kavitaTokenProvider,
                clock = Clock.System,
                eventListeners = listOfNotNull(kavitaMetadataEventHandler, kavitaNotificationsHandler),
            )
            if (kavitaConfig.eventListener.enabled) {
                kavitaEventHandler.start()
            }
        } else {
            kavitaMediaServerClient = null
            kavitaMetadataServiceProvider = null
            kavitaEventHandler = null
            logger.info { "Kavita is disabled (baseUri is blank)" }
        }
    }

    fun close() {
        komgaEventHandler?.stop()
        kavitaEventHandler?.stop()
    }

    private fun createMetadataServiceProvider(
        config: MetadataUpdateConfig,
        mediaServerClient: MediaServerClient,
        seriesThumbnailsRepository: SeriesThumbnailsRepository,
        bookThumbnailsRepository: BookThumbnailsRepository,
        seriesMatchRepository: SeriesMatchRepository,
    ): MetadataServiceProvider {
        val defaultUpdaterService = createMetadataUpdateService(
            config = config.default,
            mediaServerClient = mediaServerClient,
            seriesThumbnailsRepository = seriesThumbnailsRepository,
            bookThumbnailsRepository = bookThumbnailsRepository
        )

        val libraryUpdaterServices = config.library
            .map { (libraryId, config) ->
                libraryId to createMetadataUpdateService(
                    config = config,
                    mediaServerClient = mediaServerClient,
                    seriesThumbnailsRepository = seriesThumbnailsRepository,
                    bookThumbnailsRepository = bookThumbnailsRepository
                )
            }
            .toMap()

        val defaultMetadataService = createMetadataService(
            config = config.default,
            mediaServerClient = mediaServerClient,
            seriesMatchRepository = seriesMatchRepository,
            metadataUpdateService = defaultUpdaterService
        )
        val libraryMetadataServices = config.library
            .map { (libraryId, config) ->
                libraryId to createMetadataService(
                    config = config,
                    mediaServerClient = mediaServerClient,
                    seriesMatchRepository = seriesMatchRepository,
                    metadataUpdateService = libraryUpdaterServices[libraryId] ?: defaultUpdaterService
                )
            }
            .toMap()

        return MetadataServiceProvider(
            defaultMetadataService = defaultMetadataService,
            libraryMetadataServices = libraryMetadataServices,
            defaultUpdateService = defaultUpdaterService,
            libraryUpdaterServices = libraryUpdaterServices
        )
    }

    private fun createMetadataService(
        config: MetadataProcessingConfig,
        metadataUpdateService: MetadataUpdater,
        mediaServerClient: MediaServerClient,
        seriesMatchRepository: SeriesMatchRepository,
    ): MetadataService {
        return MetadataService(
            mediaServerClient = mediaServerClient,
            metadataProviders = metadataProviders,
            aggregateMetadata = config.aggregate,
            metadataUpdateService = metadataUpdateService,
            seriesMatchRepository = seriesMatchRepository,
            metadataMerger = MetadataMerger(mergeTags = config.mergeTags, mergeGenres = config.mergeGenres),
            libraryType = config.libraryType,
            jobTracker = jobTracker,
        )
    }

    private fun createMetadataUpdateService(
        config: MetadataProcessingConfig,
        mediaServerClient: MediaServerClient,
        seriesThumbnailsRepository: SeriesThumbnailsRepository,
        bookThumbnailsRepository: BookThumbnailsRepository,
    ): MetadataUpdater {
        val postProcessor = MetadataPostProcessor(
            libraryType = config.libraryType,
            seriesTitle = config.postProcessing.seriesTitle,
            seriesTitleLanguage = config.postProcessing.seriesTitleLanguage,
            alternativeSeriesTitles = config.postProcessing.alternativeSeriesTitles,
            alternativeSeriesTitleLanguages = config.postProcessing.alternativeSeriesTitleLanguages,
            orderBooks = config.postProcessing.orderBooks,
            readingDirectionValue = config.postProcessing.readingDirectionValue,
            languageValue = config.postProcessing.languageValue,
            fallbackToAltTitle = config.postProcessing.fallbackToAltTitle,

            scoreTagName = config.postProcessing.scoreTagName,
            originalPublisherTagName = config.postProcessing.originalPublisherTagName,
            publisherTagNames = config.postProcessing.publisherTagNames
        )

        return MetadataUpdater(
            mediaServerClient = mediaServerClient,
            seriesThumbnailsRepository = seriesThumbnailsRepository,
            bookThumbnailsRepository = bookThumbnailsRepository,
            metadataUpdateMapper = MetadataMapper(),
            postProcessor = postProcessor,
            comicInfoWriter = ComicInfoWriter.Companion.getInstance(config.overrideComicInfo),

            updateModes = config.updateModes.toSet(),
            uploadBookCovers = config.bookCovers,
            uploadSeriesCovers = config.seriesCovers,
            overrideExistingCovers = config.overrideExistingCovers,
            lockCovers = config.lockCovers,
        )
    }

    private fun createDatabase(file: Path): Database {
        val database = Database.connect(
            url = "jdbc:sqlite:${file}",
            driver = "org.sqlite.JDBC"
        )
        transaction(database) {
            SchemaUtils.create(
                KomfJobRecordTable,
                SeriesMatchTable,
                BookThumbnailTable,
                SeriesThumbnailTable,
            )
        }
        return database
    }
}
