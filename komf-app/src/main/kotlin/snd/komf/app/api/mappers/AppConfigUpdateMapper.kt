package snd.komf.app.api.mappers

import snd.komf.api.KomfNameMatchingMode
import snd.komf.api.PatchValue
import snd.komf.api.config.AniListConfigUpdateRequest
import snd.komf.api.config.AppriseConfigUpdateRequest
import snd.komf.api.config.BookMetadataConfigUpdateRequest
import snd.komf.api.config.DiscordConfigUpdateRequest
import snd.komf.api.config.EventListenerConfigUpdateRequest
import snd.komf.api.config.KavitaConfigUpdateRequest
import snd.komf.api.config.KomfConfigUpdateRequest
import snd.komf.api.config.KomgaConfigUpdateRequest
import snd.komf.api.config.MangaBakaConfigUpdateRequest
import snd.komf.api.config.MangaDexConfigUpdateRequest
import snd.komf.api.config.MetadataPostProcessingConfigUpdateRequest
import snd.komf.api.config.MetadataProcessingConfigUpdateRequest
import snd.komf.api.config.MetadataProvidersConfigUpdateRequest
import snd.komf.api.config.MetadataUpdateConfigUpdateRequest
import snd.komf.api.config.ProviderConfigUpdateRequest
import snd.komf.api.config.ProvidersConfigUpdateRequest
import snd.komf.util.NameSimilarityMatcher
import snd.komf.api.config.SeriesMetadataConfigUpdateRequest
import snd.komf.api.config.SpecYAMLConfigUpdateRequest
import snd.komf.app.config.AppConfig
import snd.komf.mediaserver.config.EventListenerConfig
import snd.komf.mediaserver.config.KavitaConfig
import snd.komf.mediaserver.config.KomgaConfig
import snd.komf.mediaserver.config.MetadataPostProcessingConfig
import snd.komf.mediaserver.config.MetadataProcessingConfig
import snd.komf.mediaserver.config.MetadataUpdateConfig
import snd.komf.mediaserver.metadata.PublisherTagNameConfig
import snd.komf.notifications.apprise.AppriseConfig
import snd.komf.notifications.discord.DiscordConfig
import snd.komf.providers.AniListConfig
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.MangaBakaConfig
import snd.komf.providers.MangaDexConfig
import snd.komf.providers.MetadataProvidersConfig
import snd.komf.providers.ProviderConfig
import snd.komf.providers.ProvidersConfig
import snd.komf.providers.SeriesMetadataConfig
import snd.komf.providers.SpecYAMLConfig
import snd.komf.providers.mangadex.model.MangaDexLink
import snd.komf.util.NameSimilarityMatcher.NameMatchingMode

private fun <T> patchValue(current: T?, patch: PatchValue<T>): T? = when (patch) {
    PatchValue.None -> null
    is PatchValue.Some -> patch.value
    PatchValue.Unset -> current
}

private fun <T, R> patchValue(current: R?, patch: PatchValue<T>, transform: (T) -> R): R? = when (patch) {
    PatchValue.None -> null
    is PatchValue.Some -> transform(patch.value)
    PatchValue.Unset -> current
}

class AppConfigUpdateMapper {

    fun patch(config: AppConfig, patch: KomfConfigUpdateRequest): AppConfig {
        return config.copy(
            metadataProviders = patch.metadataProviders.getOrNull()
                ?.let { metadataProviders(config.metadataProviders, it) }
                ?: config.metadataProviders,
            komga = patch.komga.getOrNull()?.let { komgaConfig(config.komga, it) } ?: config.komga,
            kavita = patch.kavita.getOrNull()?.let { kavitaConfig(config.kavita, it) } ?: config.kavita,
            notifications = config.notifications.copy(
                apprise = patch.notifications.getOrNull()?.apprise?.getOrNull()
                    ?.let { apprise(config.notifications.apprise, it) }
                    ?: config.notifications.apprise,
                discord = patch.notifications.getOrNull()?.discord?.getOrNull()
                    ?.let { discord(config.notifications.discord, it) }
                    ?: config.notifications.discord
            ),
        )
    }

    private fun metadataProviders(
        config: MetadataProvidersConfig,
        patch: MetadataProvidersConfigUpdateRequest
    ): MetadataProvidersConfig {
        return config.copy(
            malClientId = patchValue(config.malClientId, patch.malClientId),
            comicVineApiKey = patchValue(config.comicVineApiKey, patch.comicVineClientId),
            comicVineSearchLimit = patchValue(config.comicVineSearchLimit, patch.comicVineSearchLimit),
            comicVineIssueName = patchValue(config.comicVineIssueName, patch.comicVineIssueName),
            comicVineIdFormat = patchValue(config.comicVineIdFormat, patch.comicVineIdFormat),
            nameMatchingMode = when (val mode = patch.nameMatchingMode) {
                PatchValue.None -> config.nameMatchingMode
                is PatchValue.Some -> mode.value.toNameMatchingMode()
                PatchValue.Unset -> config.nameMatchingMode
            },
            defaultProviders = patch.defaultProviders.getOrNull()
                ?.let { providersConfig(config.defaultProviders, it) } ?: config.defaultProviders,
            libraryProviders = patch.libraryProviders.getOrNull()
                ?.let { libraryProviders(config.libraryProviders, it) }
                ?: config.libraryProviders,
        )
    }

    private fun libraryProviders(
        config: Map<String, ProvidersConfig>,
        patch: Map<String, ProvidersConfigUpdateRequest?>
    ): Map<String, ProvidersConfig> {
        val removeConfig = mutableSetOf<String>()
        val addConfigDto = mutableMapOf<String, ProvidersConfigUpdateRequest>()
        val updateConfigDto = mutableMapOf<String, ProvidersConfigUpdateRequest>()

        patch.forEach { (libraryId, configDto) ->
            if (configDto == null) removeConfig.add(libraryId)
            else if (config.containsKey(libraryId)) updateConfigDto[libraryId] = configDto
            else addConfigDto[libraryId] = configDto
        }

        val addConfig = addConfigDto.map { (libraryId, configDto) ->
            libraryId to providersConfig(configDto)
        }.toMap()

        return config.filterKeys { !removeConfig.contains(it) }
            .map { (libraryId, config) ->
                libraryId to (updateConfigDto[libraryId]?.let { providersConfig(config, it) } ?: config)
            }.toMap() + addConfig
    }

    private fun providersConfig(config: ProvidersConfig, patch: ProvidersConfigUpdateRequest): ProvidersConfig {
        return config.copy(
            mangaUpdates = patch.mangaUpdates.getOrNull()
                ?.let { providerConfig(config.mangaUpdates, it) } ?: config.mangaUpdates,
            mal = patch.mal.getOrNull()
                ?.let { providerConfig(config.mal, it) } ?: config.mal,
            nautiljon = patch.nautiljon.getOrNull()
                ?.let { providerConfig(config.nautiljon, it) } ?: config.nautiljon,
            aniList = patch.aniList.getOrNull()
                ?.let { aniListProviderConfig(config.aniList, it) } ?: config.aniList,
            yenPress = patch.yenPress.getOrNull()
                ?.let { providerConfig(config.yenPress, it) } ?: config.yenPress,
            kodansha = patch.kodansha.getOrNull()
                ?.let { providerConfig(config.kodansha, it) } ?: config.kodansha,
            viz = patch.viz.getOrNull()
                ?.let { providerConfig(config.viz, it) } ?: config.viz,
            bookWalker = patch.bookWalker.getOrNull()
                ?.let { providerConfig(config.bookWalker, it) } ?: config.bookWalker,
            mangaDex = patch.mangaDex.getOrNull()
                ?.let { mangaDexProviderConfig(config.mangaDex, it) } ?: config.mangaDex,
            bangumi = patch.bangumi.getOrNull()
                ?.let { providerConfig(config.bangumi, it) } ?: config.bangumi,
            comicVine = patch.comicVine.getOrNull()
                ?.let { providerConfig(config.comicVine, it) } ?: config.comicVine,
            hentag = patch.hentag.getOrNull()
                ?.let { providerConfig(config.hentag, it) } ?: config.hentag,
            german = patch.german.getOrNull()
                ?.let { providerConfig(config.german, it) } ?: config.german,
            mangaBaka = patch.mangaBaka.getOrNull()
                ?.let { mangaBakaProviderConfig(config.mangaBaka, it) } ?: config.mangaBaka,
            webtoons = patch.webtoons.getOrNull()
                ?.let { providerConfig(config.webtoons, it) } ?: config.webtoons,
            chaikaFile = patch.chaikaFile.getOrNull()
                ?.let { providerConfig(config.chaikaFile, it) } ?: config.chaikaFile,
            hdoujin = patch.hdoujin.getOrNull()
                ?.let { providerConfig(config.hdoujin, it) } ?: config.hdoujin,
            galleryDl = patch.galleryDl.getOrNull()
                ?.let { providerConfig(config.galleryDl, it) } ?: config.galleryDl,
            specYaml = patch.specYaml.getOrNull()
                ?.let { specYamlProviderConfig(config.specYaml, it) }
                ?: config.specYaml,
        )
    }

    private fun providersConfig(patch: ProvidersConfigUpdateRequest): ProvidersConfig {
        val config = ProvidersConfig()
        return providersConfig(config, patch)
    }

    private fun providerConfig(config: ProviderConfig, patch: ProviderConfigUpdateRequest): ProviderConfig {
        return config.copy(
            priority = patch.priority.getOrNull() ?: config.priority,
            enabled = patch.enabled.getOrNull() ?: config.enabled,
            mediaType = patch.mediaType.getOrNull()?.toMediaType() ?: config.mediaType,
            authorRoles = patch.authorRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.authorRoles,
            artistRoles = patch.artistRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.artistRoles,
            seriesMetadata = patch.seriesMetadata.getOrNull()
                ?.let { seriesMetadataConfig(config.seriesMetadata, it) }
                ?: config.seriesMetadata,
            bookMetadata = patch.bookMetadata.getOrNull()
                ?.let { bookMetadataConfig(config.bookMetadata, it) }
                ?: config.bookMetadata,
            nameMatchingMode = patchValue<KomfNameMatchingMode, NameMatchingMode?>(config.nameMatchingMode, patch.nameMatchingMode) { it.toNameMatchingMode() },
        )
    }

    private fun aniListProviderConfig(config: AniListConfig, patch: AniListConfigUpdateRequest): AniListConfig {
        return config.copy(
            priority = patch.priority.getOrNull() ?: config.priority,
            enabled = patch.enabled.getOrNull() ?: config.enabled,
            mediaType = patch.mediaType.getOrNull()?.toMediaType() ?: config.mediaType,
            authorRoles = patch.authorRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.authorRoles,
            artistRoles = patch.artistRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.artistRoles,
            tagsScoreThreshold = patch.tagsScoreThreshold.getOrNull() ?: config.tagsScoreThreshold,
            tagsSizeLimit = patch.tagsSizeLimit.getOrNull() ?: config.tagsSizeLimit,
            seriesMetadata = patch.seriesMetadata.getOrNull()
                ?.let { seriesMetadataConfig(config.seriesMetadata, it) }
                ?: config.seriesMetadata,
            nameMatchingMode = patchValue<KomfNameMatchingMode, NameMatchingMode?>(config.nameMatchingMode, patch.nameMatchingMode) { it.toNameMatchingMode() },
        )
    }

    private fun mangaDexProviderConfig(config: MangaDexConfig, patch: MangaDexConfigUpdateRequest): MangaDexConfig {
        return config.copy(
            priority = patch.priority.getOrNull() ?: config.priority,
            enabled = patch.enabled.getOrNull() ?: config.enabled,
            mediaType = patch.mediaType.getOrNull()?.toMediaType() ?: config.mediaType,
            authorRoles = patch.authorRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.authorRoles,
            artistRoles = patch.artistRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.artistRoles,
            bookMetadata = patch.bookMetadata.getOrNull()
                ?.let { bookMetadataConfig(config.bookMetadata, it) }
                ?: config.bookMetadata,
            seriesMetadata = patch.seriesMetadata.getOrNull()
                ?.let { seriesMetadataConfig(config.seriesMetadata, it) }
                ?: config.seriesMetadata,
            nameMatchingMode = patchValue<KomfNameMatchingMode, NameMatchingMode?>(config.nameMatchingMode, patch.nameMatchingMode) { it.toNameMatchingMode() },
            coverLanguages = patch.coverLanguages.getOrNull() ?: config.coverLanguages,
            links = patch.links.getOrNull()?.map { MangaDexLink.valueOf(it.name) } ?: config.links
        )
    }

    private fun mangaBakaProviderConfig(config: MangaBakaConfig, patch: MangaBakaConfigUpdateRequest): MangaBakaConfig {
        return config.copy(
            priority = patch.priority.getOrNull() ?: config.priority,
            enabled = patch.enabled.getOrNull() ?: config.enabled,
            mediaType = patch.mediaType.getOrNull()?.toMediaType() ?: config.mediaType,
            authorRoles = patch.authorRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.authorRoles,
            artistRoles = patch.artistRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.artistRoles,
            seriesMetadata = patch.seriesMetadata.getOrNull()
                ?.let { seriesMetadataConfig(config.seriesMetadata, it) }
                ?: config.seriesMetadata,
            nameMatchingMode = patchValue<KomfNameMatchingMode, NameMatchingMode?>(config.nameMatchingMode, patch.nameMatchingMode) { it.toNameMatchingMode() },
            mode = patch.mode.getOrNull()?.toMangaBakaMode() ?: config.mode
        )
    }

    private fun specYamlProviderConfig(config: SpecYAMLConfig, patch: SpecYAMLConfigUpdateRequest): SpecYAMLConfig {
        return config.copy(
            priority = patch.priority.getOrNull() ?: config.priority,
            enabled = patch.enabled.getOrNull() ?: config.enabled,
            mediaType = patch.mediaType.getOrNull()?.toMediaType() ?: config.mediaType,
            authorRoles = patch.authorRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.authorRoles,
            artistRoles = patch.artistRoles.getOrNull()?.map { it.toAuthorRole() } ?: config.artistRoles,
            seriesMetadata = patch.seriesMetadata.getOrNull()
                ?.let { seriesMetadataConfig(config.seriesMetadata, it) }
                ?: config.seriesMetadata,
            bookMetadata = patch.bookMetadata.getOrNull()
                ?.let { bookMetadataConfig(config.bookMetadata, it) }
                ?: config.bookMetadata,
            nameMatchingMode = patchValue<KomfNameMatchingMode, NameMatchingMode?>(config.nameMatchingMode, patch.nameMatchingMode) { it.toNameMatchingMode() },
            mediaRoots = patch.mediaRoots.getOrNull() ?: config.mediaRoots,
        )
    }

    private fun seriesMetadataConfig(
        config: SeriesMetadataConfig,
        patch: SeriesMetadataConfigUpdateRequest
    ): SeriesMetadataConfig {
        return config.copy(
            status = patch.status.getOrNull() ?: config.status,
            title = patch.title.getOrNull() ?: config.title,
            summary = patch.summary.getOrNull() ?: config.summary,
            publisher = patch.publisher.getOrNull() ?: config.publisher,
            readingDirection = patch.readingDirection.getOrNull() ?: config.readingDirection,
            ageRating = patch.ageRating.getOrNull() ?: config.ageRating,
            language = patch.language.getOrNull() ?: config.language,
            genres = patch.genres.getOrNull() ?: config.genres,
            tags = patch.tags.getOrNull() ?: config.tags,
            totalBookCount = patch.totalBookCount.getOrNull() ?: config.totalBookCount,
            authors = patch.authors.getOrNull() ?: config.authors,
            releaseDate = patch.releaseDate.getOrNull() ?: config.releaseDate,
            thumbnail = patch.thumbnail.getOrNull() ?: config.thumbnail,
            links = patch.links.getOrNull() ?: config.links,
            books = patch.books.getOrNull() ?: config.books,
            useOriginalPublisher = patch.useOriginalPublisher.getOrNull() ?: config.useOriginalPublisher,
        )
    }

    private fun bookMetadataConfig(
        config: BookMetadataConfig,
        patch: BookMetadataConfigUpdateRequest
    ): BookMetadataConfig {
        return config.copy(
            title = patch.title.getOrNull() ?: config.title,
            summary = patch.summary.getOrNull() ?: config.summary,
            number = patch.number.getOrNull() ?: config.number,
            numberSort = patch.numberSort.getOrNull() ?: config.numberSort,
            releaseDate = patch.releaseDate.getOrNull() ?: config.releaseDate,
            authors = patch.authors.getOrNull() ?: config.authors,
            tags = patch.tags.getOrNull() ?: config.tags,
            isbn = patch.isbn.getOrNull() ?: config.isbn,
            links = patch.links.getOrNull() ?: config.links,
            thumbnail = patch.thumbnail.getOrNull() ?: config.thumbnail
        )
    }

    private fun komgaConfig(config: KomgaConfig, patch: KomgaConfigUpdateRequest): KomgaConfig {
        return config.copy(
            baseUri = (patch.baseUri.getOrNull() ?: config.baseUri).removeSuffix("/"),
            komgaUser = patch.komgaUser.getOrNull() ?: config.komgaUser,
            komgaPassword = patch.komgaPassword.getOrNull() ?: config.komgaPassword,
            eventListener = patch.eventListener.getOrNull()
                ?.let { eventListener(config.eventListener, it) }
                ?: config.eventListener,
            metadataUpdate = patch.metadataUpdate.getOrNull()
                ?.let { metadataUpdate(config.metadataUpdate, it) }
                ?: config.metadataUpdate,
        )
    }

    private fun kavitaConfig(config: KavitaConfig, patch: KavitaConfigUpdateRequest): KavitaConfig {
        return config.copy(
            baseUri = (patch.baseUri.getOrNull() ?: config.baseUri).removeSuffix("/"),
            apiKey = patch.apiKey.getOrNull() ?: config.apiKey,
            eventListener = patch.eventListener.getOrNull()
                ?.let { eventListener(config.eventListener, it) }
                ?: config.eventListener,
            metadataUpdate = patch.metadataUpdate.getOrNull()
                ?.let { metadataUpdate(config.metadataUpdate, it) }
                ?: config.metadataUpdate,
        )
    }

    private fun eventListener(
        config: EventListenerConfig,
        patch: EventListenerConfigUpdateRequest,
    ): EventListenerConfig {
        return config.copy(
            enabled = patch.enabled.getOrNull() ?: config.enabled,
            metadataLibraryFilter = patch.metadataLibraryFilter.getOrNull()
                ?: config.metadataLibraryFilter,
            notificationsLibraryFilter = patch.notificationsLibraryFilter.getOrNull()
                ?: config.notificationsLibraryFilter
        )
    }

    private fun metadataUpdate(
        config: MetadataUpdateConfig,
        patch: MetadataUpdateConfigUpdateRequest
    ): MetadataUpdateConfig {
        return config.copy(
            default = patch.default.getOrNull()
                ?.let { metadataProcessingConfig(config.default, it) } ?: config.default,
            library = patch.library.getOrNull()
                ?.let { libraryMetadataProcessing(config.library, it) } ?: config.library,
        )
    }

    private fun libraryMetadataProcessing(
        config: Map<String, MetadataProcessingConfig>,
        patch: Map<String, MetadataProcessingConfigUpdateRequest?>
    ): Map<String, MetadataProcessingConfig> {
        val removeConfig = mutableSetOf<String>()
        val addConfigDto = mutableMapOf<String, MetadataProcessingConfigUpdateRequest>()
        val updateConfigDto = mutableMapOf<String, MetadataProcessingConfigUpdateRequest>()

        patch.forEach { (libraryId, configDto) ->
            if (configDto == null) removeConfig.add(libraryId)
            else if (config.containsKey(libraryId)) updateConfigDto[libraryId] = configDto
            else addConfigDto[libraryId] = configDto
        }

        val addConfig = addConfigDto
            .map { (libraryId, configDto) ->
                libraryId to metadataProcessingConfig(configDto)
            }.toMap()

        return config.filterKeys { !removeConfig.contains(it) }
            .map { (libraryId, config) ->
                libraryId to (updateConfigDto[libraryId]?.let { metadataProcessingConfig(config, it) } ?: config)
            }.toMap() + addConfig
    }

    private fun metadataProcessingConfig(patch: MetadataProcessingConfigUpdateRequest): MetadataProcessingConfig {
        val config = MetadataProcessingConfig()
        return metadataProcessingConfig(config, patch)
    }

    private fun metadataProcessingConfig(
        config: MetadataProcessingConfig,
        patch: MetadataProcessingConfigUpdateRequest
    ): MetadataProcessingConfig {
        return config.copy(
            libraryType = patch.libraryType.getOrNull()?.toMediaType() ?: config.libraryType,
            aggregate = patch.aggregate.getOrNull() ?: config.aggregate,
            mergeTags = patch.mergeTags.getOrNull() ?: config.mergeTags,
            mergeGenres = patch.mergeGenres.getOrNull() ?: config.mergeGenres,
            bookCovers = patch.bookCovers.getOrNull() ?: config.bookCovers,
            seriesCovers = patch.seriesCovers.getOrNull() ?: config.seriesCovers,
            overrideExistingCovers = patch.overrideExistingCovers.getOrNull() ?: config.overrideExistingCovers,
            lockCovers = patch.lockCovers.getOrNull() ?: config.lockCovers,
            updateModes = patch.updateModes.getOrNull()?.map { it.toUpdateMode() } ?: config.updateModes,
            postProcessing = patch.postProcessing.getOrNull()
                ?.let { metadataPostProcessingConfig(config.postProcessing, it) }
                ?: config.postProcessing
        )
    }

    private fun metadataPostProcessingConfig(
        config: MetadataPostProcessingConfig,
        patch: MetadataPostProcessingConfigUpdateRequest
    ): MetadataPostProcessingConfig {
        return config.copy(
            seriesTitle = patch.seriesTitle.getOrNull() ?: config.seriesTitle,
            seriesTitleLanguage = patchValue(config.seriesTitleLanguage, patch.seriesTitleLanguage) { it.ifBlank { null } },
            alternativeSeriesTitles = patch.alternativeSeriesTitles.getOrNull() ?: config.alternativeSeriesTitles,
            alternativeSeriesTitleLanguages = patch.alternativeSeriesTitleLanguages.getOrNull()
                ?: config.alternativeSeriesTitleLanguages,
            orderBooks = patch.orderBooks.getOrNull() ?: config.orderBooks,
            readingDirectionValue = patchValue(config.readingDirectionValue, patch.readingDirectionValue) { it.toReadingDirection() },
            languageValue = patchValue(config.languageValue, patch.languageValue),
            fallbackToAltTitle = patch.fallbackToAltTitle.getOrNull() ?: config.fallbackToAltTitle,

            scoreTagName = patchValue(config.scoreTagName, patch.scoreTagName),
            originalPublisherTagName = patchValue(config.originalPublisherTagName, patch.originalPublisherTagName),
            publisherTagNames = patch.publisherTagNames.getOrNull()
                ?.map { PublisherTagNameConfig(it.tagName, it.language) }
                ?: config.publisherTagNames,
        )
    }

    private fun discord(config: DiscordConfig, patch: DiscordConfigUpdateRequest): DiscordConfig {
        val oldWebhooks = config.webhooks?.mapIndexed { index, value -> index to value }?.toMap()
        val newWebhooks = when (val patchWebhooks = patch.webhooks) {
            is PatchValue.Some -> oldWebhooks?.let { it + patchWebhooks.value } ?: patchWebhooks.value
            PatchValue.None, PatchValue.Unset -> oldWebhooks
        }

        return config.copy(
            webhooks = newWebhooks?.values?.filterNotNull(),
            seriesCover = patch.seriesCover.getOrNull() ?: config.seriesCover,
        )
    }

    private fun apprise(config: AppriseConfig, patch: AppriseConfigUpdateRequest): AppriseConfig {
        val oldUrls = config.urls?.mapIndexed { index, value -> index to value }?.toMap()
        val newUrls = when (val patchWebhooks = patch.urls) {
            is PatchValue.Some -> oldUrls?.let { it + patchWebhooks.value } ?: patchWebhooks.value
            PatchValue.None, PatchValue.Unset -> oldUrls
        }

        return config.copy(
            urls = newUrls?.values?.filterNotNull(),
            seriesCover = patch.seriesCover.getOrNull() ?: config.seriesCover,
        )
    }
}