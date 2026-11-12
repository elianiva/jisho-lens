plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// The release workflow passes the pushed tag, as -PversionName=1.2.3, so the
// artifact carries the version it is released under. A build without it keeps
// the default. versionCode follows the version, as 1.2.3 -> 10203, so every
// release tag counts up from the one before it.
val appVersionName = providers.gradleProperty("versionName").getOrElse("2.0.0")
val versionParts = appVersionName.split(".")
require(versionParts.size == 3 && versionParts.all { it.toIntOrNull() != null }) {
    "versionName must look like 1.2.3, not $appVersionName"
}
val (versionMajor, versionMinor, versionPatch) = versionParts.map(String::toInt)
require(versionMinor < 100 && versionPatch < 100) {
    "versionName minor and patch must be below 100, not $appVersionName"
}

// A release is signed when the keystore and its passwords are in the
// environment. CI decodes the keystore from a repository secret; without the
// four variables a release build stays unsigned, which is what a local build
// does and what the release workflow refuses to publish.
val keystoreFile = providers.environmentVariable("JISHO_KEYSTORE_FILE")
val keystorePassword = providers.environmentVariable("JISHO_KEYSTORE_PASSWORD")
val keyAliasName = providers.environmentVariable("JISHO_KEY_ALIAS")
val keyPasswordValue = providers.environmentVariable("JISHO_KEY_PASSWORD")
val canSignRelease = listOf(keystoreFile, keystorePassword, keyAliasName, keyPasswordValue)
    .all { it.isPresent && it.get().isNotBlank() }

android {
    namespace = "com.elianiva.jisholens"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.elianiva"
        minSdk = 26
        targetSdk = 37
        versionCode = versionMajor * 10_000 + versionMinor * 100 + versionPatch
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    flavorDimensions += "channel"
    productFlavors {
        create("preview") {
            dimension = "channel"
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            resValue("string", "app_name", "Jisho Lens Preview")
        }
        create("stable") {
            dimension = "channel"
            resValue("string", "app_name", "Jisho Lens")
        }
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(keystoreFile.get())
                storePassword = keystorePassword.get()
                keyAlias = keyAliasName.get()
                keyPassword = keyPasswordValue.get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // Each flavor sets app_name with resValue.
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
            // Kuromoji and its IPADIC model both ship these; the attribution
            // lives in THIRD_PARTY_NOTICES.md.
            "/META-INF/CONTRIBUTORS.md",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Screenshot tests render many full-size frames in one JVM.
        unitTests.all { it.maxHeapSize = "896m" }
    }
}

kotlin {
    compilerOptions {
        // The design system builds on Material 3 Expressive, which is still experimental.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.ui.text.ExperimentalTextApi",
        )
    }
}

dependencies {
    implementation(project(":dictionary"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.graphics.shapes)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.text.recognition.japanese)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.roborazzi.preview.scanner)
    testImplementation(libs.composable.preview.scanner)
    testImplementation(libs.compose.ui.test.manifest)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    // Recording writes the screenshots to build/screenshots, which is not a
    // declared task output. A cache hit would skip the render and leave that
    // directory without the new images, so this task always runs.
    outputs.cacheIf("Roborazzi screenshots are not task outputs") { false }
}
