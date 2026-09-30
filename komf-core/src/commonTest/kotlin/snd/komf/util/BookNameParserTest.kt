package snd.komf.util

import snd.komf.model.BookRange
import kotlin.test.Test
import kotlin.test.assertEquals

class BookNameParserTest {
    @Test
    fun parsesVolumeAndChapterRangeSeparately() {
        val name = "Aiki V01 CH01-07.cbz"

        assertEquals(BookRange(1.0), BookNameParser.getVolumes(name))
        assertEquals(BookRange(1.0, 7.0), BookNameParser.getChapters(name))
    }
}
