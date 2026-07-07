# komf-pickled

[KOMF](https://github.com/Snd-R/komf) fork with curated metadata providers for [Komga](https://komga.org).

**Providers:**

- **German** — cascading sources (Manga Passion DE → Wikipedia DE → MangaDex DE) for German print editions
- **SpecYAML** — reads metadata from `.yaml` files adjacent to archives; requires media volume mounted at the same path as Komga

Provides metadata from three sources in cascade:

### German provider

| Source | Priority | Scope |
|--------|----------|-------|
| [Manga Passion DE](https://www.manga-passion.de/) | 30 | Print editions, cover images, volume data, author/artist, publisher, ISBN |
| [Wikipedia DE](https://de.wikipedia.org/) | 50 | German titles, series description, genres, demographics |
| [MangaDex DE](https://mangadex.org/) | 60 | English/German titles, manga-specific descriptions |

Dropped publisher storefronts (not consumed): Carlsen, Egmont, Altraverse, Tokyopop.

### SpecYAML provider

Reads `.yaml` metadata files with the same basename as the archive, located in the same folder. Requires the media volume to be mounted at the same path as Komga. See [LANraragi SpecYAML plugin](https://github.com/ccdc06/metadata) for the YAML format reference.

| Feature | Details |
|---------|---------|
| Source | `.yaml` files adjacent to archives |
| Priority | Configurable per-library or global |
| Fields | Title, summary, authors, tags, categories, cover, series, volume, status |
| Requirement | Media volume must be mounted at the same path as Komga |

## Usage

### 1. Configure

Create `application.yml`:

```yaml
komga:
  baseUri: "https://komga.example.com"
  komgaUser: "user@example.com"
  komgaPassword: "your-password"

metadataProviders:
  defaultProviders:
    german:
      priority: 0
      enabled: true
    specYaml:
      priority: 10
      enabled: false
```

See [KOMF docs](https://github.com/Snd-R/komf) for full config options.

### 2. Run with Docker

Pull from [GitHub Container Registry](https://github.com/Baine/komf-pickled/pkgs/container/komf-pickled):

```bash
docker run -d \
  --name komf-pickled \
  -p 8085:8085 \
  -v /path/to/config:/config \
  ghcr.io/baine/komf-pickled:latest
```

### 3. Build from source

```bash
git clone https://github.com/Baine/komf-pickled.git
cd komf-pickled
./gradlew :komf-app:shadowJar --no-daemon
java -jar komf-app/build/libs/komf-app-*-all.jar
```

### 4. Build the frontend (optional, for development)

The [Komelia web UI](https://github.com/Baine/Komelia-pickled) with GERMAN provider support is maintained in a separate repo. Pre-built frontend resources are checked into `komf-app/src/main/resources/komelia/`.

To rebuild (requires cloning [Komelia-pickled](https://github.com/Baine/Komelia-pickled) separately):

```bash
cd Komelia-pickled
./gradlew :komelia-app:wasmJsBrowserDistribution :komelia-image-decoder:wasm-image-worker:wasmJsBrowserProductionWebpack --no-daemon
cp -r komelia-app/build/kotlin-webpack/wasmJs/productionExecutable/* /path/to/komf-pickled/komf-app/src/main/resources/komelia/
cp -r komelia-image-decoder/wasm-image-worker/build/kotlin-webpack/wasmJs/productionExecutable/* /path/to/komf-pickled/komf-app/src/main/resources/komelia/
```

### 5. API

Trigger metadata processing for a library (with or without SpecYAML):

```bash
# Reset existing metadata, then re-match
curl -X POST http://localhost:8085/api/komga/metadata/reset/library/LIBRARY_ID
curl -X POST http://localhost:8085/api/komga/metadata/match/library/LIBRARY_ID

# Upload SpecYAML config for a library (enables/disables per-library)
curl -X PATCH http://localhost:8085/api/komga/metadata/library/LIBRARY_ID \
  -H "Content-Type: application/json" \
  -d '{"providers":{"specYaml":{"enable":true,"priority":5}}}'
```

Monitor progress at `GET /api/jobs/{jobId}`.

### 6. Firefox Extension

The [Komelia-pickled](https://github.com/Baine/Komelia-pickled) repo includes a Firefox extension that connects Komga to KOMF.

```bash
git clone https://github.com/Baine/Komelia-pickled.git
cd Komelia-pickled
./gradlew :komelia-komf-extension:app:packageExtension --no-daemon
# output: komelia-komf-extension/app/build/distributions/webextension.zip
```

Load in Firefox: `about:debugging#/runtime/this-firefox` → "Load Temporary Add-on" → select the zip. For permanent use, submit to [AMO](https://addons.mozilla.org/).

## Repository

- **[komf-pickled](https://github.com/Baine/komf-pickled)** — this repo: KOMF fork with German + SpecYAML providers, Docker image
- **[Komelia-pickled](https://github.com/Baine/Komelia-pickled)** — Komelia fork: KOMF web UI with GERMAN provider, Firefox extension

## License

Same as upstream: [MIT](LICENSE)
