package snd.komf.providers

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.ConstantCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode.Companion.TooManyRequests
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.exposed.v1.jdbc.Database
import snd.komf.ktor.HttpRequestRateLimiter
import snd.komf.ktor.intervalLimiter
import snd.komf.ktor.rateLimiter
import snd.komf.providers.anilist.AniListClient
import snd.komf.providers.anilist.AniListMetadataMapper
import snd.komf.providers.anilist.AniListMetadataProvider
import snd.komf.providers.bangumi.BangumiClient
import snd.komf.providers.bangumi.BangumiMetadataMapper
import snd.komf.providers.bangumi.BangumiMetadataProvider
import snd.komf.providers.bookwalker.BookWalkerClient
import snd.komf.providers.bookwalker.BookWalkerMapper
import snd.komf.providers.bookwalker.BookWalkerMetadataProvider
import snd.komf.providers.comicvine.ComicVineClient
import snd.komf.providers.comicvine.ComicVineMetadataMapper
import snd.komf.providers.comicvine.ComicVineMetadataProvider
import snd.komf.providers.comicvine.ComicVineRateLimiter
import snd.komf.providers.german.GermanMetadataMapper
import snd.komf.providers.german.GermanMetadataProvider
import snd.komf.providers.german.source.MangaDexDeSource
import snd.komf.providers.german.source.MangaPassionSource
import snd.komf.providers.german.source.WikipediaDeSource
import snd.komf.providers.hentag.HentagClient
import snd.komf.providers.hentag.HentagMetadataMapper
import snd.komf.providers.hentag.HentagMetadataProvider
import snd.komf.providers.kodansha.KodanshaClient
import snd.komf.providers.chaikafile.ChaikaFileMetadataMapper
import snd.komf.providers.chaikafile.ChaikaFileMetadataProvider
import snd.komf.providers.chaikafile.ChaikaFileReader
import snd.komf.providers.gallerydl.GalleryDLFileReader
import snd.komf.providers.gallerydl.GalleryDLMetadataMapper
import snd.komf.providers.gallerydl.GalleryDLMetadataProvider
import snd.komf.providers.hdoujin.HdoujinMetadataMapper
import snd.komf.providers.hdoujin.HdoujinMetadataProvider
import snd.komf.providers.hdoujin.HdoujinReader
import snd.komf.providers.schalenetwork.SchaleNetworkArchiveReader
import snd.komf.providers.schalenetwork.SchaleNetworkClient
import snd.komf.providers.schalenetwork.SchaleNetworkMetadataMapper
import snd.komf.providers.schalenetwork.SchaleNetworkMetadataProvider
import snd.komf.providers.specyaml.SpecYAMLFileReader
import snd.komf.providers.specyaml.SpecYAMLMetadataMapper
import snd.komf.providers.specyaml.SpecYAMLMetadataProvider
import snd.komf.providers.kodansha.KodanshaMetadataMapper
import snd.komf.providers.kodansha.KodanshaMetadataProvider
import snd.komf.providers.mal.MalClient
import snd.komf.providers.mal.MalMetadataMapper
import snd.komf.providers.mal.MalMetadataProvider
import snd.komf.providers.mangabaka.MangaBakaDataSource
import snd.komf.providers.mangabaka.MangaBakaMetadataMapper
import snd.komf.providers.mangabaka.MangaBakaMetadataProvider
import snd.komf.providers.mangabaka.api.MangaBakaApiClient
import snd.komf.providers.mangabaka.db.MangaBakaDbDataSource
import snd.komf.providers.mangadex.MangaDexClient
import snd.komf.providers.mangadex.MangaDexMetadataMapper
import snd.komf.providers.mangadex.MangaDexMetadataProvider
import snd.komf.providers.mangadex.model.MangaDexArtist
import snd.komf.providers.mangadex.model.MangaDexAuthor
import snd.komf.providers.mangadex.model.MangaDexCoverArt
import snd.komf.providers.mangadex.model.MangaDexRelationship
import snd.komf.providers.mangadex.model.MangaDexUnknownRelationship
import snd.komf.providers.mangaupdates.MangaUpdatesClient
import snd.komf.providers.mangaupdates.MangaUpdatesMetadataMapper
import snd.komf.providers.mangaupdates.MangaUpdatesMetadataProvider
import snd.komf.providers.nautiljon.NautiljonClient
import snd.komf.providers.nautiljon.NautiljonMetadataProvider
import snd.komf.providers.nautiljon.NautiljonSeriesMetadataMapper
import snd.komf.providers.viz.VizClient
import snd.komf.providers.viz.VizMetadataMapper
import snd.komf.providers.viz.VizMetadataProvider
import snd.komf.providers.webtoons.WebtoonsClient
import snd.komf.providers.webtoons.WebtoonsMetadataMapper
import snd.komf.providers.webtoons.WebtoonsMetadataProvider
import snd.komf.providers.yenpress.YenPressClient
import snd.komf.providers.yenpress.YenPressMetadataMapper
import snd.komf.providers.yenpress.YenPressMetadataProvider
import snd.komf.util.NameSimilarityMatcher
import snd.komf.util.NameSimilarityMatcher.NameMatchingMode
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger { }

private fun resolveNameMatcher(mode: NameMatchingMode?, default: NameSimilarityMatcher): NameSimilarityMatcher =
    mode?.let { NameSimilarityMatcher(it) } ?: default

class ProvidersModule(
    private val config: MetadataProvidersConfig,
    baseHttpClient: HttpClient,
    mangaBakaDatabase: Database?,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false

        serializersModule = SerializersModule {
            polymorphic(MangaDexRelationship::class) {
                subclass(MangaDexAuthor::class)
                subclass(MangaDexArtist::class)
                subclass(MangaDexCoverArt::class)
                defaultDeserializer { MangaDexUnknownRelationship.serializer() }
            }
        }
    }

    fun getMetadataProviders(): MetadataProviders {
        val defaultNameMatcher = NameSimilarityMatcher(config.nameMatchingMode)
        val defaultProviders = createMetadataProviders(
            config = config.defaultProviders,
            defaultNameMatcher = defaultNameMatcher,
            malClientId = config.malClientId,
            comicVineClientId = config.comicVineApiKey,
            comicVineSearchLimit = config.comicVineSearchLimit,
            comicVineIssueName = config.comicVineIssueName,
            comicVineIdFormat = config.comicVineIdFormat,
            bangumiToken = config.bangumiToken,
        )
        val libraryProviders = config.libraryProviders
            .map { (libraryId, libraryConfig) ->
                libraryId to createMetadataProviders(
                    config = libraryConfig,
                    defaultNameMatcher = defaultNameMatcher,
                    malClientId = config.malClientId,
                    comicVineClientId = config.comicVineApiKey,
                    comicVineSearchLimit = config.comicVineSearchLimit,
                    comicVineIssueName = config.comicVineIssueName,
                    comicVineIdFormat = config.comicVineIdFormat,
                    bangumiToken = config.bangumiToken,
                )
            }
            .toMap()

        return MetadataProviders(defaultProviders, libraryProviders)
    }

    private val baseHttpClientJson = baseHttpClient.config {
        install(ContentNegotiation) { json(json) }
    }

    private val comicVineRateLimiter = ComicVineRateLimiter()
    private val malRateLimiter = rateLimiter(eventsPerInterval = 10, interval = 10.seconds)
    private val bangumiRateLimiter = intervalLimiter(eventsPerInterval = 10, interval = 7.seconds)

    private fun HttpRequestRetryConfig.defaultRetry() {
        retryIf(3) { _, response ->
            when (response.status.value) {
                TooManyRequests.value -> true
                420 -> true // ComicVine returns 420 response code
                in 500..599 -> true
                else -> false
            }
        }
        exponentialDelay(baseDelayMs = 2000, respectRetryAfterHeader = true)
    }

    private val mangaUpdatesClient = MangaUpdatesClient(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 10.seconds
                eventsPerInterval = 15
                allowBurst = true
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )

    private val nautiljonClient = NautiljonClient(
        baseHttpClient.config {
            install(HttpRequestRateLimiter) {
                interval = 10.seconds
                eventsPerInterval = 10
                allowBurst = false
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )

    private val aniListClient = AniListClient(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 10.seconds
                eventsPerInterval = 15
                allowBurst = true
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )
    private val yenPressClient = YenPressClient(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 10.seconds
                eventsPerInterval = 10
                allowBurst = true
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )
    private val kodanshaClient = KodanshaClient(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 10.seconds
                eventsPerInterval = 10
                allowBurst = true
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )
    private val vizClient = VizClient(
        baseHttpClient.config {
            install(HttpRequestRateLimiter) {
                interval = 10.seconds
                eventsPerInterval = 5
                allowBurst = false
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )
    private val bookWalkerClient = BookWalkerClient(
        ktor = baseHttpClient.config {
            install(HttpRequestRateLimiter) {
                interval = 10.seconds
                eventsPerInterval = 10
                allowBurst = true
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        },
        json = json
    )
    private val mangaDexClient = MangaDexClient(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 10.seconds
                eventsPerInterval = 15
                allowBurst = true
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )

    private val hentagClient = HentagClient(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 5.seconds
                eventsPerInterval = 1
                allowBurst = false
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )

    private val mangaPassionClient = MangaPassionSource(
        baseHttpClient.config {
            install(HttpRequestRateLimiter) {
                interval = 5.seconds
                eventsPerInterval = 10
                allowBurst = true
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )

    private val wikipediaDeClient = WikipediaDeSource(
        baseHttpClient.config {
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )

    private val mangaDexDeClient = MangaDexDeSource(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 10.seconds
                eventsPerInterval = 15
                allowBurst = true
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )

    private val mangaBakaClient = MangaBakaApiClient(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 1.seconds
                eventsPerInterval = 1
                allowBurst = false
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
        }
    )
    private val mangaBakaCoverFetchClient = baseHttpClientJson.config {
        install(HttpRequestRateLimiter) {
            interval = 1.seconds
            eventsPerInterval = 2
            allowBurst = false
        }
        install(HttpRequestRetry) {
            defaultRetry()
        }
    }


    private val mangaBakaDbDataSource = mangaBakaDatabase?.let { MangaBakaDbDataSource(it) }

    private val schaleNetworkClient = SchaleNetworkClient(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 1.seconds
                eventsPerInterval = 3
                allowBurst = true
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
            defaultRequest {
                header("Referer", "https://schale.network/")
            }
        }
    )

    private val webtoonsClient = WebtoonsClient(
        baseHttpClientJson.config {
            install(HttpRequestRateLimiter) {
                interval = 1.seconds
                eventsPerInterval = 1
                allowBurst = false
            }
            install(HttpRequestRetry) {
                defaultRetry()
            }
            install(HttpCookies) {
                storage = ConstantCookiesStorage(
                    Cookie(
                        name = "ageGatePass",
                        value = "true",
                        domain = "www.webtoons.com",
                        path = "/"
                    ),
                    Cookie(
                        name = "locale",
                        value = "en", // Fixed to "en", maybe should be configurable?
                        domain = "www.webtoons.com",
                        path = "/"
                    ),
                    Cookie(
                        name = "needGDPR",
                        value = "false",
                        domain = "www.webtoons.com",
                        path = "/"
                    )
                )
            }
            install(ContentNegotiation) {
                json()
            }
        }
    )

    private fun createMetadataProviders(
        config: ProvidersConfig,
        defaultNameMatcher: NameSimilarityMatcher,
        malClientId: String?,
        comicVineClientId: String?,
        comicVineSearchLimit: Int?,
        comicVineIssueName: String?,
        comicVineIdFormat: String?,
        bangumiToken: String?,
    ): MetadataProvidersContainer {
        fun <P : MetadataProvider> entry(provider: CoreProviders, impl: P?, priority: Int) =
            impl?.let { MetadataProvidersContainer.Entry(provider, it, priority) }

        val entries = listOfNotNull(
            entry(CoreProviders.MANGA_UPDATES, createMangaUpdatesMetadataProvider(config.mangaUpdates, mangaUpdatesClient, defaultNameMatcher), config.mangaUpdates.priority),
            entry(CoreProviders.MAL, createMalMetadataProvider(config.mal, malClientId, defaultNameMatcher), config.mal.priority),
            entry(CoreProviders.NAUTILJON, createNautiljonMetadataProvider(config.nautiljon, nautiljonClient, defaultNameMatcher), config.nautiljon.priority),
            entry(CoreProviders.ANILIST, createAnilistMetadataProvider(config.aniList, aniListClient, defaultNameMatcher), config.aniList.priority),
            entry(CoreProviders.YEN_PRESS, createYenPressMetadataProvider(config.yenPress, yenPressClient, defaultNameMatcher), config.yenPress.priority),
            entry(CoreProviders.KODANSHA, createKodanshaMetadataProvider(config.kodansha, kodanshaClient, defaultNameMatcher), config.kodansha.priority),
            entry(CoreProviders.VIZ, createVizMetadataProvider(config.viz, vizClient, defaultNameMatcher), config.viz.priority),
            entry(CoreProviders.BOOK_WALKER, createBookWalkerMetadataProvider(config.bookWalker, bookWalkerClient, defaultNameMatcher), config.bookWalker.priority),
            entry(CoreProviders.MANGADEX, createMangaDexMetadataProvider(config.mangaDex, mangaDexClient, defaultNameMatcher), config.mangaDex.priority),
            entry(CoreProviders.BANGUMI, createBangumiMetadataProvider(config.bangumi, defaultNameMatcher, bangumiToken), config.bangumi.priority),
            entry(CoreProviders.COMIC_VINE, createComicVineMetadataProvider(
                config = config.comicVine, apiKey = comicVineClientId,
                comicVineSearchLimit = comicVineSearchLimit, comicVineIssueName = comicVineIssueName,
                comicVineIdFormat = comicVineIdFormat, rateLimiter = comicVineRateLimiter,
                defaultNameMatcher = defaultNameMatcher,
            ), config.comicVine.priority),
            entry(CoreProviders.HENTAG, createHentagMetadataProvider(config.hentag, hentagClient, defaultNameMatcher), config.hentag.priority),
            entry(CoreProviders.MANGA_BAKA, createMangaBakaMetadataProvider(
                config = config.mangaBaka,
                datasource = when (config.mangaBaka.mode) {
                    MangaBakaMode.API -> mangaBakaClient
                    MangaBakaMode.DATABASE -> mangaBakaDbDataSource
                },
                coverFetchClient = mangaBakaCoverFetchClient,
                defaultNameMatcher = defaultNameMatcher
            ), config.mangaBaka.priority),
            entry(CoreProviders.WEBTOONS, createWebtoonsMetadataProvider(config = config.webtoons, client = webtoonsClient, defaultNameMatcher = defaultNameMatcher), config.webtoons.priority),
            entry(CoreProviders.GERMAN, createGermanMetadataProvider(config = config.german, sources = listOf(mangaPassionClient, wikipediaDeClient, mangaDexDeClient), defaultNameMatcher = defaultNameMatcher), config.german.priority),
            entry(CoreProviders.CHAIKA_FILE, createChaikaFileMetadataProvider(config = config.chaikaFile), config.chaikaFile.priority),
            entry(CoreProviders.HDOUJIN, createHdoujinMetadataProvider(config = config.hdoujin), config.hdoujin.priority),
            entry(CoreProviders.GALLERY_DL, createGalleryDLMetadataProvider(config = config.galleryDl), config.galleryDl.priority),
            entry(CoreProviders.SCHALE_NETWORK, createSchaleNetworkMetadataProvider(config = config.schaleNetwork), config.schaleNetwork.priority),
            entry(CoreProviders.SPEC_YAML, createSpecYAMLMetadataProvider(config = config.specYaml), config.specYaml.priority),
        )
        return MetadataProvidersContainer(entries)
    }

    private fun createMalMetadataProvider(
        config: ProviderConfig,
        clientId: String?,
        defaultNameMatcher: NameSimilarityMatcher,
    ): MalMetadataProvider? {
        if (config.enabled.not()) return null
        requireNotNull(clientId) { "MyAnimeList clientId is not set" }

        val malClient = MalClient(
            baseHttpClientJson.config {
                install(HttpRequestRateLimiter) {
                    preconfigured = malRateLimiter
                }
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 3)
                    exponentialDelay(respectRetryAfterHeader = true)
                }
                defaultRequest {
                    header("X-MAL-CLIENT-ID", clientId)
                }
            }
        )

        val malMetadataMapper = MalMetadataMapper(
            metadataConfig = config.seriesMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )
        val malSimilarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)
        return MalMetadataProvider(
            malClient,
            malMetadataMapper,
            malSimilarityMatcher,
            config.seriesMetadata.thumbnail,
            config.mediaType,
        )
    }

    private fun createMangaUpdatesMetadataProvider(
        config: ProviderConfig,
        client: MangaUpdatesClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): MangaUpdatesMetadataProvider? {
        if (config.enabled.not()) return null

        val mangaUpdatesMetadataMapper = MangaUpdatesMetadataMapper(
            metadataConfig = config.seriesMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )
        val mangaUpdatesSimilarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)
        return MangaUpdatesMetadataProvider(
            client,
            mangaUpdatesMetadataMapper,
            mangaUpdatesSimilarityMatcher,
            config.seriesMetadata.thumbnail,
            config.mediaType
        )
    }

    private fun createNautiljonMetadataProvider(
        config: ProviderConfig,
        client: NautiljonClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): NautiljonMetadataProvider? {
        if (config.enabled.not()) return null
        val seriesMetadataMapper = NautiljonSeriesMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )
        val similarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)
        return NautiljonMetadataProvider(
            client,
            seriesMetadataMapper,
            similarityMatcher,
            config.seriesMetadata.thumbnail,
            config.bookMetadata.thumbnail,
        )
    }

    private fun createAnilistMetadataProvider(
        config: AniListConfig,
        client: AniListClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): AniListMetadataProvider? {
        if (config.enabled.not()) return null

        val metadataMapper = AniListMetadataMapper(
            metadataConfig = config.seriesMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
            tagsSizeLimit = config.tagsSizeLimit,
            tagsScoreThreshold = config.tagsScoreThreshold
        )
        val similarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)
        return AniListMetadataProvider(
            client,
            metadataMapper,
            similarityMatcher,
            config.seriesMetadata.thumbnail,
            config.mediaType
        )
    }

    private fun createYenPressMetadataProvider(
        config: ProviderConfig,
        client: YenPressClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): YenPressMetadataProvider? {
        if (config.enabled.not()) return null

        val metadataMapper = YenPressMetadataMapper(
            config.seriesMetadata,
            config.bookMetadata,
            config.authorRoles,
            config.artistRoles
        )
        val similarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)
        return YenPressMetadataProvider(
            client,
            metadataMapper,
            similarityMatcher,
            config.mediaType,
            config.seriesMetadata.thumbnail,
            config.bookMetadata.thumbnail,
        )
    }

    private fun createKodanshaMetadataProvider(
        config: ProviderConfig,
        client: KodanshaClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): KodanshaMetadataProvider? {
        if (config.enabled.not()) return null

        val metadataMapper = KodanshaMetadataMapper(config.seriesMetadata, config.bookMetadata)
        val similarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)

        return KodanshaMetadataProvider(
            client,
            metadataMapper,
            similarityMatcher,
            config.seriesMetadata.thumbnail,
            config.bookMetadata.thumbnail,
        )
    }

    private fun createVizMetadataProvider(
        config: ProviderConfig,
        client: VizClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): VizMetadataProvider? {
        if (config.enabled.not()) return null

        val metadataMapper = VizMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )
        val similarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)

        return VizMetadataProvider(
            client,
            metadataMapper,
            similarityMatcher,
            config.seriesMetadata.thumbnail,
            config.bookMetadata.thumbnail,
        )
    }

    private fun createBookWalkerMetadataProvider(
        config: ProviderConfig,
        client: BookWalkerClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): BookWalkerMetadataProvider? {
        if (config.enabled.not()) return null

        val bookWalkerMapper = BookWalkerMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )
        val similarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)

        return BookWalkerMetadataProvider(
            client,
            bookWalkerMapper,
            similarityMatcher,
            config.seriesMetadata.thumbnail,
            config.bookMetadata.thumbnail,
            config.mediaType
        )
    }

    private fun createMangaDexMetadataProvider(
        config: MangaDexConfig,
        client: MangaDexClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): MangaDexMetadataProvider? {
        if (config.enabled.not()) return null

        val mangaDexMetadataMapper = MangaDexMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
            coverLanguages = config.coverLanguages,
            linksFilter = config.links
        )

        val mangaDexSimilarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)
        return MangaDexMetadataProvider(
            client,
            mangaDexMetadataMapper,
            mangaDexSimilarityMatcher,
            config.seriesMetadata.thumbnail,
            config.bookMetadata.thumbnail
        )
    }

    private fun createBangumiMetadataProvider(
        config: ProviderConfig,
        defaultNameMatcher: NameSimilarityMatcher,
        token: String?,
    ): BangumiMetadataProvider? {
        if (config.enabled.not()) return null
        val client = BangumiClient(
            baseHttpClientJson.config {
                install(HttpRequestRateLimiter) {
                    preconfigured = bangumiRateLimiter
                }
                install(HttpRequestRetry) {
                    defaultRetry()
                }

                if (!token.isNullOrBlank()) defaultRequest { bearerAuth(token) }
            },
        )
        val bangumiMetadataMapper = BangumiMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )
        val bangumiSimilarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)
        return BangumiMetadataProvider(
            client,
            bangumiMetadataMapper,
            bangumiSimilarityMatcher,
            config.seriesMetadata.thumbnail,
            config.mediaType,
        )
    }

    private fun createComicVineMetadataProvider(
        config: ProviderConfig,
        apiKey: String?,
        comicVineSearchLimit: Int?,
        comicVineIssueName: String?,
        comicVineIdFormat: String?,
        rateLimiter: ComicVineRateLimiter,
        defaultNameMatcher: NameSimilarityMatcher,
    ): ComicVineMetadataProvider? {
        if (config.enabled.not()) return null
        requireNotNull(apiKey) { "Api key is not configured for ComicVine provider" }

        val comicVineClient = ComicVineClient(
            ktor = baseHttpClientJson.config {
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 3)
                    exponentialDelay(respectRetryAfterHeader = true)
                }
            },
            apiKey = apiKey,
            comicVineSearchLimit = comicVineSearchLimit,
            rateLimiter = rateLimiter
        )
        val metadataMapper = ComicVineMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            issueNameTemplate = comicVineIssueName,
        )
        val similarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)

        return ComicVineMetadataProvider(
            client = comicVineClient,
            mapper = metadataMapper,
            nameMatcher = similarityMatcher,
            fetchSeriesCovers = config.seriesMetadata.thumbnail,
            fetchBookCovers = config.bookMetadata.thumbnail,
            idFormat = comicVineIdFormat,
        )
    }

    private fun createHentagMetadataProvider(
        config: ProviderConfig,
        client: HentagClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): HentagMetadataProvider? {
        if (config.enabled.not()) return null

        val hentagMetadataMapper = HentagMetadataMapper(
            metadataConfig = config.seriesMetadata,
            authorRoles = config.authorRoles,
        )

        val hentagSimilarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)
        return HentagMetadataProvider(
            client,
            hentagMetadataMapper,
            hentagSimilarityMatcher,
            config.seriesMetadata.thumbnail,
        )
    }


    private fun createMangaBakaMetadataProvider(
        config: MangaBakaConfig,
        datasource: MangaBakaDataSource?,
        coverFetchClient: HttpClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): MangaBakaMetadataProvider? {
        if (config.enabled.not()) return null
        if (datasource == null) {
            logger.warn { "Failed to find MangaBaka database. Disabling MangaBaka provider" }
            return null
        }

        return MangaBakaMetadataProvider(
            dataSource = datasource,
            metadataMapper = MangaBakaMetadataMapper(
                metadataConfig = config.seriesMetadata,
                authorRoles = config.authorRoles,
                artistRoles = config.artistRoles,
            ),
            nameMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher),
            coverFetchClient = if (config.seriesMetadata.thumbnail) coverFetchClient else null,
            mediaType = config.mediaType
        )
    }

    private fun createWebtoonsMetadataProvider(
        config: ProviderConfig,
        client: WebtoonsClient,
        defaultNameMatcher: NameSimilarityMatcher,
    ): WebtoonsMetadataProvider? {
        if (config.enabled.not()) return null

        return WebtoonsMetadataProvider(
            client = client,
            metadataMapper = WebtoonsMetadataMapper(
                metadataConfig = config.seriesMetadata,
                authorRoles = config.authorRoles,
                artistRoles = config.artistRoles,
            ),
            nameMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher),
            fetchSeriesCovers = config.seriesMetadata.thumbnail,
            fetchBookCovers = config.bookMetadata.thumbnail,
        )
    }

    class MetadataProviders(
        private val defaultProviders: MetadataProvidersContainer,
        private val libraryProviders: Map<String, MetadataProvidersContainer>,
    ) {
        fun defaultProvidersList() = defaultProviders.providers

        fun providers(libraryId: String): Collection<MetadataProvider> {
            return libraryProviders[libraryId]?.providers ?: defaultProviders.providers
        }

        fun provider(libraryId: String, provider: CoreProviders) =
            libraryProviders[libraryId]?.provider(provider) ?: defaultProviders.provider(provider)
    }

    class MetadataProvidersContainer(
        entries: List<Entry>,
    ) {
        data class Entry(val provider: CoreProviders, val impl: MetadataProvider, val priority: Int)

        private val providerMap: Map<CoreProviders, MetadataProvider> = entries.associate { it.provider to it.impl }

        val providers: List<MetadataProvider> = entries
            .sortedBy { it.priority }
            .map { it.impl }

        fun provider(provider: CoreProviders): MetadataProvider? = providerMap[provider]
    }

    private fun createGermanMetadataProvider(
        config: ProviderConfig,
        sources: List<snd.komf.providers.german.source.GermanDataSource>,
        defaultNameMatcher: NameSimilarityMatcher,
    ): GermanMetadataProvider? {
        if (config.enabled.not()) return null

        val mapper = GermanMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )
        val similarityMatcher = resolveNameMatcher(config.nameMatchingMode, defaultNameMatcher)

        return GermanMetadataProvider(
            sources = sources,
            metadataMapper = mapper,
            nameMatcher = similarityMatcher,
            fetchSeriesCovers = config.seriesMetadata.thumbnail,
            fetchBookCovers = config.bookMetadata.thumbnail,
        )
    }

    private fun createChaikaFileMetadataProvider(
        config: ProviderConfig,
    ): ChaikaFileMetadataProvider? {
        if (config.enabled.not()) return null

        val fileReader = ChaikaFileReader()
        val metadataMapper = ChaikaFileMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
        )

        return ChaikaFileMetadataProvider(
            fileReader = fileReader,
            metadataMapper = metadataMapper,
        )
    }

    private fun createHdoujinMetadataProvider(
        config: ProviderConfig,
    ): HdoujinMetadataProvider? {
        if (config.enabled.not()) return null

        val fileReader = HdoujinReader()
        val metadataMapper = HdoujinMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
        )

        return HdoujinMetadataProvider(
            fileReader = fileReader,
            metadataMapper = metadataMapper,
        )
    }

    private fun createGalleryDLMetadataProvider(
        config: ProviderConfig,
    ): GalleryDLMetadataProvider? {
        if (config.enabled.not()) return null

        val fileReader = GalleryDLFileReader()
        val metadataMapper = GalleryDLMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )

        return GalleryDLMetadataProvider(
            fileReader = fileReader,
            metadataMapper = metadataMapper,
        )
    }

    private fun createSchaleNetworkMetadataProvider(
        config: ProviderConfig,
    ): SchaleNetworkMetadataProvider? {
        if (config.enabled.not()) return null

        val archiveReader = SchaleNetworkArchiveReader()
        val metadataMapper = SchaleNetworkMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )

        return SchaleNetworkMetadataProvider(
            archiveReader = archiveReader,
            client = schaleNetworkClient,
            metadataMapper = metadataMapper,
        )
    }

    private fun createSpecYAMLMetadataProvider(
        config: SpecYAMLConfig,
    ): SpecYAMLMetadataProvider? {
        if (config.enabled.not()) return null

        val fileReader = SpecYAMLFileReader()
        val metadataMapper = SpecYAMLMetadataMapper(
            seriesMetadataConfig = config.seriesMetadata,
            bookMetadataConfig = config.bookMetadata,
            authorRoles = config.authorRoles,
            artistRoles = config.artistRoles,
        )

        return SpecYAMLMetadataProvider(
            fileReader = fileReader,
            metadataMapper = metadataMapper,
        )
    }


}