package snd.komf.app

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.RollingPolicy
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import snd.komf.CoreModule
import snd.komf.app.config.AppConfig
import snd.komf.app.config.ConfigLoader
import snd.komf.app.config.ConfigWriter
import snd.komf.mediaserver.MediaServerModule
import snd.komf.notifications.NotificationsModule
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory

private val logger = KotlinLogging.logger {}

class AppContext(private val configPath: Path? = null) {
    @Volatile
    var appConfig: AppConfig
        private set

    private val reloadMutex = Mutex()

    private val ktorBaseClient: HttpClient
    private val jsonBase: Json
    private val serverModule: ServerModule

    private lateinit var providersModule: CoreModule
    private lateinit var mediaServerModule: MediaServerModule
    private lateinit var notificationsModule: NotificationsModule

    private lateinit var apiRoutesDependencies: MutableStateFlow<ApiDynamicDependencies>

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            strictMode = false
        )
    )
    private val configWriter = ConfigWriter(yaml)
    private val configLoader = ConfigLoader(yaml)

    init {
        val config = loadConfig()
        configureLogging(config, configPath)
        appConfig = config

        val httpLogger = KotlinLogging.logger("http.logging")
        val baseOkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor { httpLogger.info { it } }
                .setLevel(appConfig.httpLogLevel))
            .cache(
                Cache(
                    directory = Path.of(System.getProperty("java.io.tmpdir"))
                        .resolve("komf").createDirectories()
                        .toFile(),
                    maxSize = 50L * 1024L * 1024L // 50 MiB
                )
            )
            .build()

        jsonBase = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        ktorBaseClient = HttpClient(OkHttp) {
            engine { preconfigured = baseOkHttpClient }
            expectSuccess = true
            install(UserAgent) { agent = "Snd-R/komf (https://github.com/Snd-R/komf)" }
        }

        reloadModules(config)

        serverModule = ServerModule(
            serverPort = config.server.port,
            onConfigUpdate = this::refreshState,
            dynamicDependencies = apiRoutesDependencies,
            json = jsonBase,
        )

        serverModule.startServer()
        disableFileOnlyLoggerAdditive()
    }

    suspend fun refreshState() {
        reloadMutex.withLock {
            reloadModules(this.appConfig)
        }
    }

    suspend fun refreshState(newConfig: AppConfig) {
        reloadMutex.withLock {
            appConfig = newConfig
            reloadModules(newConfig)
            writeConfig(newConfig)
        }
    }

    private fun reloadModules(config: AppConfig) {
        logger.info { "Reconfiguring application state" }

        val providersModule = CoreModule(
            config = config.metadataProviders,
            ktor = ktorBaseClient,
            onStateRefresh = this::refreshState,
        )
        val notificationsModule = NotificationsModule(config.notifications, ktorBaseClient)
        val mediaServerModule = MediaServerModule(
            komgaConfig = config.komga,
            kavitaConfig = config.kavita,
            databaseConfig = config.database,
            jsonBase = jsonBase,
            ktorBaseClient = ktorBaseClient,
            appriseService = notificationsModule.appriseService,
            discordWebhookService = notificationsModule.discordWebhookService,
            metadataProviders = providersModule.metadataProviders
        )

        if (::mediaServerModule.isInitialized) this.mediaServerModule.close()

        this.providersModule = providersModule
        this.notificationsModule = notificationsModule
        this.mediaServerModule = mediaServerModule
        apiRoutesDependencies.value = createApiRoutesDependencies()
    }

    private fun createApiRoutesDependencies() = ApiDynamicDependencies(
        config = this.appConfig,
        jobTracker = mediaServerModule.jobTracker,
        jobsRepository = mediaServerModule.jobRepository,
        komgaMediaServerClient = mediaServerModule.komgaClient,
        komgaMetadataServiceProvider = mediaServerModule.komgaMetadataServiceProvider,
        kavitaMediaServerClient = mediaServerModule.kavitaMediaServerClient,
        kavitaMetadataServiceProvider = mediaServerModule.kavitaMetadataServiceProvider,
        discordService = notificationsModule.discordWebhookService,
        discordRenderer = notificationsModule.discordVelocityRenderer,
        appriseService = notificationsModule.appriseService,
        appriseRenderer = notificationsModule.appriseVelocityRenderer,
        mangaBakaDownloader = providersModule.mangaBakaDatabaseDownloader,
        mangaBakaDbMetadata = providersModule.mangaBakaDbMetadata
    )

    private suspend fun writeConfig(config: AppConfig) {
        withContext(Dispatchers.IO) {
            configPath?.let { path -> configWriter.writeConfig(config, path) }
                ?: configWriter.writeConfigToDefaultPath(config)
        }
    }

    private fun loadConfig(): AppConfig {
        return when {
            configPath == null -> configLoader.default()
            configPath.isDirectory() -> configLoader.loadDirectory(configPath)
            else -> configLoader.loadFile(configPath)
        }
    }

    private fun configureLogging(config: AppConfig, configPath: Path?) {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        val level = Level.valueOf(config.logLevel.uppercase())
        rootLogger.level = level

        val logDirectory = resolveLogDirectory(configPath)
        logDirectory.createDirectories()

        val komfAppender = createRollingFileAppender(
            loggerContext, "KOMF_FILE", "$logDirectory/komf.log"
        ) { createTimeBasedRollingPolicy(loggerContext, it, "$logDirectory/komf.%d{yyyy-MM-dd}.log", 7) }
        rootLogger.addAppender(komfAppender)

        val providersLogger = loggerContext.getLogger("snd.komf.providers")
        providersLogger.level = level
        val providersAppender = createRollingFileAppender(
            loggerContext, "PROVIDERS_FILE", "$logDirectory/providers.log"
        ) { createTimeBasedRollingPolicy(loggerContext, it, "$logDirectory/providers.%d{yyyy-MM-dd}.log", 7) }
        providersLogger.addAppender(providersAppender)
        providersLogger.isAdditive = false

        val httpLogger = loggerContext.getLogger("http.logging")
        httpLogger.level = level
        val httpAppender = createRollingFileAppender(
            loggerContext, "HTTP_FILE", "$logDirectory/http.log"
        ) {
            SizeAndTimeBasedRollingPolicy<ILoggingEvent>().apply {
                setContext(loggerContext)
                setFileNamePattern("$logDirectory/http.%d{yyyy-MM-dd}.%i.log")
                setMaxFileSize(FileSize.valueOf("100MB"))
                maxHistory = 2
                setTotalSizeCap(FileSize.valueOf("500MB"))
                setParent(it)
                start()
            }
        }
        httpLogger.addAppender(httpAppender)
        httpLogger.isAdditive = false

        val metadataLogger = loggerContext.getLogger("snd.komf.mediaserver.metadata")
        metadataLogger.level = level
        val metadataAppender = createRollingFileAppender(
            loggerContext, "METADATA_FILE", "$logDirectory/metadata.log"
        ) { createTimeBasedRollingPolicy(loggerContext, it, "$logDirectory/metadata.%d{yyyy-MM-dd}.log", 7) }
        metadataLogger.addAppender(metadataAppender)
        metadataLogger.isAdditive = false
    }

    private fun createRollingFileAppender(
        loggerContext: LoggerContext,
        name: String,
        file: String,
        createRollingPolicy: (RollingFileAppender<ILoggingEvent>) -> RollingPolicy,
    ): RollingFileAppender<ILoggingEvent> {
        val appender = RollingFileAppender<ILoggingEvent>().apply {
            this.name = name
            this.file = file
            context = loggerContext
        }
        val encoder = PatternLayoutEncoder().apply {
            pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
            context = loggerContext
            start()
        }
        appender.encoder = encoder
        appender.rollingPolicy = createRollingPolicy(appender)
        appender.start()
        return appender
    }

    private fun createTimeBasedRollingPolicy(
        loggerContext: LoggerContext,
        appender: RollingFileAppender<ILoggingEvent>,
        fileNamePattern: String,
        maxHistory: Int,
    ): TimeBasedRollingPolicy<ILoggingEvent> {
        return TimeBasedRollingPolicy<ILoggingEvent>().apply {
            setContext(loggerContext)
            setFileNamePattern(fileNamePattern)
            this.maxHistory = maxHistory
            setParent(appender)
            start()
        }
    }

    private fun disableFileOnlyLoggerAdditive() {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        loggerContext.loggerList
            .filter {
                it.name.startsWith("snd.komf.providers.") ||
                    it.name.startsWith("snd.komf.mediaserver.metadata.")
            }
            .forEach { it.isAdditive = false }
    }

    private fun resolveLogDirectory(configPath: Path?): Path {
        return when {
            configPath == null -> Path.of("logs").toAbsolutePath().normalize()
            configPath.isDirectory() -> configPath.resolve("logs")
            else -> configPath.parent.resolve("logs")
        }
    }
}
