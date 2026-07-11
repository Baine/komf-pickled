import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.shadow)
}

group = "io.github.snd-r"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("kotlin.time.ExperimentalTime")
    }
}
java {
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":komf-core"))
    implementation(project(":komf-mediaserver"))
    implementation(project(":komf-notifications"))
    implementation(project(":komf-api-models"))

    implementation(libs.logback.classic)
    implementation(libs.slf4j.api)
    implementation(libs.kotlin.logging)

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kaml)
}

val komeliaDir = rootProject.projectDir.resolve("Komelia")
val frontendOut = layout.buildDirectory.dir("frontend")

val buildFrontend by tasks.registering(Exec::class) {
    description = "Build Komelia Wasm frontend for SpecYAML provider UI"
    group = "build"
    onlyIf { komeliaDir.resolve("komelia-app").isDirectory }
    workingDir = komeliaDir
    commandLine(
        if (Os.isFamily(Os.FAMILY_WINDOWS)) "gradlew.bat" else "./gradlew",
        ":komelia-app:wasmJsBrowserDistribution",
        ":komelia-infra:image-decoder:wasm-image-worker:wasmJsBrowserProductionWebpack",
        "--no-daemon"
    )
}

val copyFrontend by tasks.registering(Copy::class) {
    description = "Copy Komelia Wasm output to resources"
    group = "build"
    dependsOn(buildFrontend)
    onlyIf { buildFrontend.get().didWork }

    val wasmApp = komeliaDir.resolve("komelia-app/build/dist/wasmJs/productionExecutable")
    val wasmWorker = komeliaDir.resolve("komelia-infra/image-decoder/wasm-image-worker/build/kotlin-webpack/wasmJs/productionExecutable")

    from(wasmApp) { include("**") }
    from(wasmWorker) { include("**") }
    into(layout.projectDirectory.dir("src/main/resources/komelia"))
}

tasks {
    processResources { dependsOn(copyFrontend) }
    shadowJar {
        dependsOn(copyFrontend)
        manifest {
            attributes(Pair("Main-Class", "snd.komf.app.ApplicationKt"))
        }
    }
}
