package snd.komf.comicinfo

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ComicInfoWriterTest {

    private fun buildCbz(path: java.nio.file.Path) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("page1.jpg"))
            zip.write(ByteArray(10_000) { (it % 251).toByte() })
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("page2.png"))
            zip.write(ByteArray(50_000) { (it % 251).toByte() })
            zip.closeEntry()
        }
    }

    @Test
    fun `write preserves original entry content`() {
        val cbz = Files.createTempFile("komf-test", ".cbz")
        buildCbz(cbz)
        val original = ZipFile(cbz.toFile()).use { zip ->
            zip.entries().asSequence().associate { it.name to zip.getInputStream(it).readBytes() }
        }

        ComicInfoWriter.getInstance(true).writeMetadata(cbz.toString(), ComicInfo(series = "Test", number = "1"))

        ZipFile(cbz.toFile()).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(setOf("page1.jpg", "page2.png", "ComicInfo.xml"), names)
            original.forEach { (name, bytes) ->
                assertContains(names, name)
                assertEquals(bytes.toList(), zip.getInputStream(zip.getEntry(name)).readBytes().toList(), "$name content changed")
            }
            val comicInfo = zip.getInputStream(zip.getEntry("ComicInfo.xml")).readBytes().decodeToString()
            assertContains(comicInfo, "<Series>Test</Series>")
        }

        // second write (update) must also round-trip entries untouched
        ComicInfoWriter.getInstance(true).writeMetadata(cbz.toString(), ComicInfo(series = "Test", number = "2"))
        ZipFile(cbz.toFile()).use { zip ->
            assertEquals("2", zip.getInputStream(zip.getEntry("ComicInfo.xml")).readBytes().decodeToString()
                .substringAfter("<Number>").substringBefore("</Number>"))
            assertEquals(original["page1.jpg"]!!.toList(), zip.getInputStream(zip.getEntry("page1.jpg")).readBytes().toList())
        }

        Files.deleteIfExists(cbz)
    }
}
