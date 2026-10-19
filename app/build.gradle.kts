import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.podcapture"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.podcapture"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        // Podcast Index API credentials from local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        buildConfigField("String", "PODCAST_INDEX_API_KEY", "\"${localProperties.getProperty("PODCAST_INDEX_API_KEY", "")}\"")
        buildConfigField("String", "PODCAST_INDEX_API_SECRET", "\"${localProperties.getProperty("PODCAST_INDEX_API_SECRET", "")}\"")
    }

    // CI test builds (-PciBuild) install alongside the regular app under a separate package,
    // so testing a PR never requires uninstalling (and losing data from) the main install.
    val isCiBuild = project.hasProperty("ciBuild")

    if (isCiBuild) {
        sourceSets.getByName("debug").res.srcDir("src/ci/res")
    }

    // Release signing credentials live outside the repo (default ~/.android/podcapture-release.properties,
    // override with -PreleaseSigningProps=<path>) with keys storeFile, storePassword, keyAlias, keyPassword.
    val releaseSigningFile = file(
        (project.findProperty("releaseSigningProps") as String?)
            ?: "${System.getProperty("user.home")}/.android/podcapture-release.properties"
    )

    signingConfigs {
        if (releaseSigningFile.exists()) {
            create("release") {
                val props = Properties().apply { releaseSigningFile.inputStream().use { load(it) } }
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (isCiBuild) {
                applicationIdSuffix = ".ci"
            }
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Media3 (ExoPlayer)
    implementation(libs.bundles.media3)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // DocumentFile (for SAF)
    implementation(libs.androidx.documentfile)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // DateTime
    implementation(libs.kotlinx.datetime)

    // Koin DI
    implementation(libs.bundles.koin)

    // Networking (Retrofit + OkHttp)
    implementation(libs.bundles.networking)

    // Image Loading
    implementation(libs.coil.compose)

    // Sherpa ONNX (Whisper for speech recognition)
    implementation(libs.sherpa.onnx)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
