plugins {
    alias(libs.plugins.kotlinAtomicfu) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.mavenPublish) apply false
}

dependencies {
    implementation("io.github.microutils:kotlin-algorithms:1.3")
}

tasks.wrapper {
    gradleVersion = "8.9"
    distributionType = Wrapper.DistributionType.ALL
}
