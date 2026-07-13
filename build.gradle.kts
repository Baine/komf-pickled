plugins {
    alias(libs.plugins.kotlinAtomicfu) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.mavenPublish) apply false
}

dependencies {
    implementation("com.github.h0tk3y:better-levenshtein:2.0.3")
}

tasks.wrapper {
    gradleVersion = "8.9"
    distributionType = Wrapper.DistributionType.ALL
}
