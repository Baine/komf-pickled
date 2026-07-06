# komf-german

[KOMF](https://github.com/Snd-R/komf) fork with a German metadata provider for [Komga](https://komga.org).

Provides metadata from three sources in cascade:

| Source | Priority | Scope |
|--------|----------|-------|
| [Manga Passion DE](https://www.manga-passion.de/) | 30 | Print editions, cover images, volume data, author/artist, publisher, ISBN |
| [Wikipedia DE](https://de.wikipedia.org/) | 50 | German titles, series description, genres, demographics |
| [MangaDex DE](https://mangadex.org/) | 60 | English/German titles, manga-specific descriptions |

Dropped publisher storefronts (not consumed): Carlsen, Egmont, Altraverse, Tokyopop.

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
```

See [KOMF docs](https://github.com/Snd-R/komf) for full config options.

### 2. Run with Docker

Pull from [GitHub Container Registry](https://github.com/Baine/komf-german/pkgs/container/komf-german):

```bash
docker run -d \
  --name komf-german \
  -p 8085:8085 \
  -v /path/to/config:/config \
  ghcr.io/baine/komf-german:latest
```

### 3. Build from source

```bash
git clone --recurse-submodules https://github.com/Baine/komf-german.git
cd komf-german
./gradlew :komf-app:shadowJar --no-daemon
java -jar komf-app/build/libs/komf-app-*-all.jar
```

### 4. Build the frontend (optional, for development)

The [Komelia web UI](https://github.com/Baine/Komelia-german) with GERMAN provider support is included as a git submodule (`Komelia/`). Pre-built frontend resources are checked into `komf-app/src/main/resources/komelia/`.

To rebuild:

```bash
cd Komelia
./gradlew :komelia-app:wasmJsBrowserDistribution :komelia-image-decoder:wasm-image-worker:wasmJsBrowserProductionWebpack --no-daemon
cp -r komelia-app/build/kotlin-webpack/wasmJs/productionExecutable/* ../komf-app/src/main/resources/komelia/
cp -r komelia-image-decoder/wasm-image-worker/build/kotlin-webpack/wasmJs/productionExecutable/* ../komf-app/src/main/resources/komelia/
```

### 5. API

Trigger metadata processing for a library:

```bash
# Reset existing metadata, then re-match
curl -X POST http://localhost:8085/api/komga/metadata/reset/library/LIBRARY_ID
curl -X POST http://localhost:8085/api/komga/metadata/match/library/LIBRARY_ID
```

Monitor progress at `GET /api/jobs/{jobId}`.

## Repository

- **[komf-german](https://github.com/Baine/komf-german)** — this repo: KOMF fork with German metadata provider, Docker image
- **[Komelia-german](https://github.com/Baine/Komelia-german)** — Komelia fork: KOMF web UI with GERMAN provider, Firefox extension

## License

Same as upstream: [MIT](LICENSE)
