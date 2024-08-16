plugins {
    kotlin("jvm") version "1.9.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Google API client libraries
    implementation("com.google.api-client:google-api-client:1.33.2")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.33.2")
    implementation("com.google.apis:google-api-services-youtube:v3-rev222-1.25.0")
    implementation("com.google.code.gson:gson:2.8.8")

    // Kotlin coroutines for asynchronous operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.1")

    // Other utilities
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")
}

application {
    mainClass.set("com.youtube.spawnpk.MainKt")
}
