plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

// Pure JVM module: no Android imports, so the importer and lookup run in unit
// tests and in a desktop CLI, and can be reused by the future Compose app.
dependencies {
    api(libs.kotlinx.serialization.json)
    // Morphological segmentation. The IPADIC model ships in the artifact.
    implementation(libs.kuromoji.ipadic)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.sqlite.jdbc)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
