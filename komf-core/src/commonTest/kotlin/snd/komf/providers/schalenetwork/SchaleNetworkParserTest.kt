package snd.komf.providers.schalenetwork

import snd.komf.providers.schalenetwork.model.SchaleNetworkId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchaleNetworkParserTest {

    @Test
    fun parsesSamplePage() {
        val parser = SchaleNetworkParser()
        val metadata = parser.parse(SAMPLE_HTML, SchaleNetworkId("27365", "476cc8cb73be"))

        assertEquals("[Azumi Kyohei] Saccharine Girlfriend (Comic Bavel 2026-05)", metadata.title)
        assertEquals("27365", metadata.id)
        assertEquals("476cc8cb73be", metadata.key)
        assertEquals(
            "https://aoba.erocdn.net/thumbnail/116133/842578f99c55/8995ebf94243649cd6a77034a125fb70116272ca6ede4a2f9e52967c1ca513de/8257402e-2cab-4c2f-b17b-a027b2f5cec8/896.jpg",
            metadata.thumbnailUrl
        )

        val artists = metadata.tags["artist"].orEmpty()
        assertEquals(listOf("azumi kyohei"), artists)

        val languages = metadata.tags["languages"].orEmpty()
        assertTrue(languages.contains("english"))
        assertTrue(languages.contains("translated"))

        val magazine = metadata.tags["magazine"].orEmpty()
        assertEquals(listOf("comic bavel 2026-05"), magazine)

        val tags = metadata.tags["tag"].orEmpty()
        assertTrue(tags.contains("apron"))
        assertTrue(tags.contains("ponytail"))

        val female = metadata.tags["female"].orEmpty()
        assertTrue(female.contains("busty"))

        val other = metadata.tags["other"].orEmpty()
        assertTrue(other.contains("uncensored"))

        assertEquals(2026, metadata.releasedAt?.year)
    }

    private val SAMPLE_HTML = """
<!doctype html>
<html lang="en">
<head>
  <title>[Azumi Kyohei] Saccharine Girlfriend (Comic Bavel 2026-05) - Schale Network</title>
</head>
<body>
  <main>
    <section id="gallery">
      <header>
        <figure>
          <a href="/g/27365/476cc8cb73be/read/1">
            <img src="https://aoba.erocdn.net/thumbnail/116133/842578f99c55/8995ebf94243649cd6a77034a125fb70116272ca6ede4a2f9e52967c1ca513de/8257402e-2cab-4c2f-b17b-a027b2f5cec8/896.jpg" alt="cover"/>
          </a>
        </figure>
        <h1 class="text-2xl font-[600]">[Azumi Kyohei] Saccharine Girlfriend (Comic Bavel 2026-05)</h1>
        <div class="meta">
          <div class="top flex flex-wrap gap-10 mb-10">
            <a href="/browse?s=pages:>%3D14+pages:<%3D34"><span>24 Pages</span></a>
            <div><span>2026-07-09 11:47 CEST</span></div>
          </div>
          <div class="tags leading-tight my-10">
            <div class="flex mt-4 first:mt-0">
              <span class="shrink-0 w-90 py-3">Artist</span>
              <div class="flex flex-wrap grow gap-4 w-0">
                <a href="/tag/artist:azumi+kyohei" rel="tag">
                  <span>azumi kyohei</span><span>22</span>
                </a>
              </div>
            </div>
            <div class="flex mt-4 first:mt-0">
              <span class="shrink-0 w-90 py-3">Languages</span>
              <div class="flex flex-wrap grow gap-4 w-0">
                <a href="/tag/language:english" rel="tag"><span>english</span><span>17.8K</span></a>
                <a href="/tag/language:translated" rel="tag"><span>translated</span><span>17.9K</span></a>
              </div>
            </div>
            <div class="flex mt-4 first:mt-0">
              <span class="shrink-0 w-90 py-3">Magazine</span>
              <div class="flex flex-wrap grow gap-4 w-0">
                <a href="/tag/magazine:comic+bavel+2026-05" rel="tag"><span>comic bavel 2026-05</span><span>8</span></a>
              </div>
            </div>
            <div class="flex mt-4 first:mt-0">
              <span class="shrink-0 w-90 py-3">Tags</span>
              <div class="flex flex-wrap grow gap-4 w-0">
                <a href="/tag/tag:apron" rel="tag"><span>apron</span><span>226</span></a>
                <a href="/tag/tag:ponytail" rel="tag"><span>ponytail</span><span>493</span></a>
                <a href="/tag/female:busty" rel="tag"><span>busty</span><span>11.5K</span></a>
                <a href="/tag/other:uncensored" rel="tag"><span>uncensored</span><span>15.2K</span></a>
              </div>
            </div>
          </div>
        </div>
      </header>
    </section>
  </main>
</body>
</html>
""".trimIndent()
}
