package snd.komf.providers.german

import snd.komf.providers.german.model.GermanSeriesId
import snd.komf.providers.german.source.MangaPassionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Parser checks for the manga-passion volume endpoints, using captured live responses
 * (Edition 547 "Detektiv Conan Sonderbände" — special edition, number=null volumes).
 */
class MangaPassionSourceParsingTest {

    @Test
    fun parsesCollectionWithArrangementFallback() {
        val volumes = MangaPassionSource.parseVolumeCollection(COLLECTION_JSON)

        assertEquals(2, volumes.size)
        // number=null → falls back to arrangement
        assertEquals(24, volumes[0].number)
        assertEquals("Special Black Edition – Part 4", volumes[0].name)
        assertEquals("32394", volumes[0].id.value)
        assertEquals(23, volumes[1].number)
        // edition stays null so komf's number-based book matching isn't blocked
        assertNull(volumes[0].edition)
    }

    @Test
    fun parsesVolumeDetail() {
        val volume = MangaPassionSource.parseVolumeDetail(GermanSeriesId("547"), DETAIL_JSON)

        assertEquals("27534", volume?.id?.value)
        assertEquals(23, volume?.number)
        assertEquals("Monster Mysteries", volume?.title)
        assertEquals("Unser kleiner Lieblingsdetektiv hat im Laufe seiner Karriere", volume?.description?.take(60))
        assertEquals("978-3-7555-0654-6", volume?.isbn)
        assertEquals(2025, volume?.releaseDate?.year)
        assertEquals(10, volume?.releaseDate?.monthNumber)
    }

    @Test
    fun regularSeriesVolumeKeepsNumberAndNullDescription() {
        val volume = MangaPassionSource.parseVolumeDetail(GermanSeriesId("175"), REGULAR_DETAIL_JSON)

        assertEquals(22, volume?.number)
        assertNull(volume?.description)
        assertNull(volume?.title)
    }
}

private const val COLLECTION_JSON = """{"@context":"\/contexts\/Volume","@id":"\/volumes","@type":"hydra:Collection","hydra:totalItems":2,"hydra:member":[
{"@id":"\/volumes\/32394","@type":"Volume","id":32394,"number":null,"lastNumber":null,"arrangement":24,"status":0,"pages":512,"ageRating":null,"type":null,"format":0,"year":2027,"month":3,"day":9,"date":"2027-03-09T00:00:00+00:00","title":"Special Black Edition – Part 4","cover":"https:\/\/media.manga-passion.de\/c.jpg","edition":{"@id":"\/editions\/547","title":"Detektiv Conan Sonderbände"},"numberDisplay":null},
{"@id":"\/volumes\/27534","@type":"Volume","id":27534,"number":null,"lastNumber":null,"arrangement":23,"status":0,"pages":464,"ageRating":null,"type":null,"format":0,"year":2025,"month":10,"day":7,"date":"2025-10-07T00:00:00+00:00","title":"Monster Mysteries","cover":"https:\/\/media.manga-passion.de\/m.jpg","edition":{"@id":"\/editions\/547","title":"Detektiv Conan Sonderbände"},"numberDisplay":null}
]}"""

private const val DETAIL_JSON = """{"@context":"\/contexts\/Volume","@id":"\/volumes\/27534","@type":"Volume","id":27534,"number":null,"lastNumber":null,"numberOverride":null,"arrangement":23,"pages":464,"ageRating":null,"date":"2025-10-07T00:00:00+00:00","description":"Unser kleiner Lieblingsdetektiv hat im Laufe seiner Karriere nicht nur unzähligen Verbrechern das Handwerk gelegt.","isbn10":null,"isbn13":"978-3-7555-0654-6","title":"Monster Mysteries","cover":"https:\/\/media.manga-passion.de\/m.jpg","edition":{"@id":"\/editions\/547","title":"Detektiv Conan Sonderbände"}}"""

private const val REGULAR_DETAIL_JSON = """{"@context":"\/contexts\/Volume","@id":"\/volumes\/9273","@type":"Volume","id":9273,"number":22,"lastNumber":null,"numberOverride":null,"arrangement":21,"pages":null,"ageRating":null,"date":"2009-07-31T00:00:00+00:00","description":null,"isbn10":null,"isbn13":"9783866077232","title":null,"cover":"https:\/\/media.manga-passion.de\/r.jpg","edition":{"@id":"\/editions\/175","title":"20th Century Boys"}}"""
