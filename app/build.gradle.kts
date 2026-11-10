import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// google-services.json is gitignored. Without it the app builds and runs local-only
// (FirebaseBootstrap.isConfigured == false), mirroring the iOS behaviour.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Developer-only switches (gitignored; see dev.properties.example). Debug builds only —
// the Android counterpart of the iOS scheme env vars FIREBASE_USE_EMULATORS / KGS_DEMO_AUTH_*.
val devProperties = Properties().apply {
    rootProject.file("dev.properties").takeIf { it.exists() }?.let { f -> FileInputStream(f).use(::load) }
}
fun devString(key: String, default: String = "") =
    "\"" + (devProperties.getProperty(key) ?: default).replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.stoneofhope.stoneofhope"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stoneofhope.stoneofhope"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-internal"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Release values: never the emulator, never demo credentials.
        buildConfigField("boolean", "FIREBASE_USE_EMULATORS", "false")
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
        buildConfigField("String", "FIREBASE_EMULATOR_PROJECT_ID", "\"\"")
        buildConfigField("String", "DEMO_AUTH_EMAIL", "\"\"")
        buildConfigField("String", "DEMO_AUTH_PASSWORD", "\"\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "boolean", "FIREBASE_USE_EMULATORS",
                (devProperties.getProperty("firebase.useEmulators") == "true").toString(),
            )
            // 10.0.2.2 is the host Mac as seen from the Android emulator; a physical phone needs the Mac's LAN IP.
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", devString("firebase.emulatorHost", "10.0.2.2"))
            // Must equal the iOS project id so both simulators share the same emulator data.
            buildConfigField("String", "FIREBASE_EMULATOR_PROJECT_ID", devString("firebase.projectId", "knowgrowshow-d3609"))
            buildConfigField("String", "DEMO_AUTH_EMAIL", devString("demoAuth.email"))
            buildConfigField("String", "DEMO_AUTH_PASSWORD", devString("demoAuth.password"))
        }
        release {
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric (Room DAO tests) needs merged Android resources.
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    // Exported schemas are checked in so migrations can be tested against them.
    schemaDirectory("$projectDir/schemas")
}

// Bundled KJV / CUV text, pre-built into a Room asset so nothing is parsed on first launch.
val generateBibleTextDb = tasks.register<GenerateBibleTextDb>("generateBibleTextDb") {
    kjvJson.set(layout.projectDirectory.file("bible-data/KJV.json"))
    cuvXml.set(layout.projectDirectory.file("bible-data/CUV.xml"))
    roomSchema.set(layout.projectDirectory.file("schemas/com.stoneofhope.stoneofhope.data.bible.text.BibleTextDatabase/2.json"))
    outputDir.set(layout.buildDirectory.dir("generated/bibleText"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(generateBibleTextDb, GenerateBibleTextDb::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    // On-device text recognition for photos (Chinese + Latin; iOS uses Vision).
    implementation(libs.mlkit.text.recognition.chinese)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mongodb.bson)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.turbine)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
