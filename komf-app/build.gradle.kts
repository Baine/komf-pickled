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
        jvmTarget.set(JvmTarget.JVM_21)
        optIn.add("kotlin.time.ExperimentalTime")
    }
}
java {
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":komf-core"))
    implementation(project(":komf-mediaserver"))
    implementation(project(":komf-notifications"))
    implementation(project(":komf-api-models"))

    implementation(libs.logback.core)
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
    implementation(libs.okhttp.sse)
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
        ":komelia-image-decoder:wasm-image-worker:wasmJsBrowserProductionWebpack",
        "--no-daemon"
    )
}

val copyFrontend by tasks.registering(Copy::class) {
    description = "Copy Komelia Wasm output to resources"
    group = "build"
    dependsOn(buildFrontend)
    onlyIf { buildFrontend.get().didWork }

    val wasmApp = komeliaDir.resolve("komelia-app/build/dist/wasmJs/productionExecutable")
    val wasmWorker = komeliaDir.resolve("komelia-image-decoder/wasm-image-worker/build/kotlin-webpack/wasmJs/productionExecutable")

    from(wasmApp) { include("**") }
    from(wasmWorker) { include("**") }
    into(layout.projectDirectory.dir("src/main/resources/komelia"))
}

tasks {
    shadowJar {
        dependsOn(copyFrontend)
        manifest {
            attributes(Pair("Main-Class", "snd.komf.app.ApplicationKt"))
        }
    }
}

tasks.register("depsize") {
    description = "Prints dependencies for \"runtime\" configuration"
    doLast {
        listConfigurationDependencies(configurations["runtimeClasspath"])
    }
}

fun listConfigurationDependencies(configuration: Configuration) {
    val formatStr = "%,10.2f"

    val size = configuration.sumOf { it.length() / (1024.0 * 1024.0) }

    val out = StringBuffer()
    out.append("\nConfiguration name: \"${configuration.name}\"\n")
    if (size > 0) {
        out.append("Total dependencies size:".padEnd(65))
        out.append("${String.format(formatStr, size)} Mb\n\n")

        configuration.sortedBy { -it.length() }
            .forEach {
                out.append(it.name.padEnd(65))
                out.append("${String.format(formatStr, (it.length() / 1024.0))} kb\n")
            }
    } else {
        out.append("No dependencies found")
    }
    println(out)
}
