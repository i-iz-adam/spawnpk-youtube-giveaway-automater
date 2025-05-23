plugins {
    kotlin("jvm") version "1.9.10"
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.api-client:google-api-client:1.33.2")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.33.2")
    implementation("com.google.apis:google-api-services-youtube:v3-rev222-1.25.0")
    implementation("com.google.code.gson:gson:2.8.8")
    
    // TornadoFX for UI
    implementation("no.tornado:tornadofx:1.7.20")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")
    
    // Settings storage
    implementation("com.google.code.gson:gson:2.10.1")
}

application {
    mainClass.set("com.youtube.spawnpk.SpawnPKAppKt")
}

// Configure Kotlin compilation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

// Configure Java compilation
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

// Configure JavaFX
javafx {
    version = "11.0.2"
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.fxml")
}

// Custom task to run the application
tasks.register<JavaExec>("runApp") {
    group = "application"
    description = "Runs the SpawnPK YouTube Giveaway Automater"
    
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.youtube.spawnpk.SpawnPKAppKt")
    
    jvmArgs = listOf(
        "--module-path", classpath.asPath,
        "--add-modules", "javafx.controls,javafx.fxml",
        "--add-opens", "javafx.graphics/javafx.scene=ALL-UNNAMED",
        "--add-opens", "javafx.controls/javafx.scene.control=ALL-UNNAMED"
    )
}
