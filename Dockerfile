FROM gradle:jdk21 AS build
WORKDIR /build
COPY gradle gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY komf-api-models komf-api-models
COPY komf-client komf-client
COPY komf-core komf-core
COPY komf-mediaserver komf-mediaserver
COPY komf-notifications komf-notifications
COPY komf-app komf-app
RUN gradle :komf-app:shadowJar --no-daemon

FROM eclipse-temurin:21-jre AS base-amd64
FROM eclipse-temurin:21.0.6_7-jre AS base-arm64
FROM base-${TARGETARCH} AS runtime
RUN apt-get update && apt-get install -y pipx && rm -rf /var/lib/apt/lists/*
RUN pipx install --include-deps pipx \
    && /root/.local/bin/pipx install --global --include-deps apprise
WORKDIR /app
COPY --from=build /build/komf-app/build/libs/komf-app-1.0.0-SNAPSHOT-all.jar ./
ENV LC_ALL=en_US.UTF-8
ENV KOMF_CONFIG_DIR="/config"
ENTRYPOINT ["java","-jar", "komf-app-1.0.0-SNAPSHOT-all.jar"]
EXPOSE 8085
LABEL org.opencontainers.image.url=https://github.com/Baine/komf-german org.opencontainers.image.source=https://github.com/Baine/komf-german
