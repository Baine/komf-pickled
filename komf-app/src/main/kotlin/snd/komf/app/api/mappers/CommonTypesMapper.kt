package snd.komf.app.api.mappers

import snd.komf.api.KomfAuthorRole
import snd.komf.api.KomfCoreProviders
import snd.komf.api.KomfMediaType
import snd.komf.api.KomfNameMatchingMode
import snd.komf.api.KomfProviders
import snd.komf.api.KomfReadingDirection
import snd.komf.api.KomfUpdateMode
import snd.komf.api.UnknownKomfProvider
import snd.komf.model.AuthorRole
import snd.komf.model.MediaType
import snd.komf.model.ReadingDirection
import snd.komf.model.UpdateMode
import snd.komf.providers.CoreProviders
import snd.komf.providers.MangaBakaMode
import snd.komf.util.NameSimilarityMatcher.NameMatchingMode

// ponytail: all API↔core enum pairs share identical entry names, enumValueOf replaces the when blocks

inline fun <reified T : Enum<T>> Enum<*>.to() = enumValueOf<T>(name)

fun KomfAuthorRole.toAuthorRole() = to<AuthorRole>()
fun AuthorRole.fromAuthorRole() = to<KomfAuthorRole>()

fun KomfMediaType.toMediaType() = to<MediaType>()
fun MediaType.fromMediaType() = to<KomfMediaType>()

fun KomfNameMatchingMode.toNameMatchingMode() = to<NameMatchingMode>()
fun NameMatchingMode.fromNameMatchingMode() = to<KomfNameMatchingMode>()

fun KomfUpdateMode.toUpdateMode() = to<UpdateMode>()
fun UpdateMode.fromUpdateMode() = to<KomfUpdateMode>()

fun KomfReadingDirection.toReadingDirection() = to<ReadingDirection>()
fun ReadingDirection.fromReadingDirection() = to<KomfReadingDirection>()

fun CoreProviders.fromProvider() = to<KomfCoreProviders>()

fun KomfProviders.toProvider(): CoreProviders = when (this) {
    is KomfCoreProviders -> to()
    is UnknownKomfProvider -> CoreProviders.valueOf(this.name)
}

fun snd.komf.api.MangaBakaMode.toMangaBakaMode() = to<MangaBakaMode>()
