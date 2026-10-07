import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing key, kept out of git. Android only installs an update signed with the same key as
// the installed app, so every release must be built with this one keystore.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.bookyourtoken"
    compileSdk {
        version = release(libs.versions.androidCompileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.example.bookyourtoken"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        // MUST increase by 1 on every release; the release tag is v<versionCode> (see README).
        versionCode = 5
        versionName = "1.3.1"

        // The public repo that holds only the releases (no source). No token: it must stay public.
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"SomaPrasanth\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"Book-your-token-release\"")
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreProperties["storeFile"]?.let { file(it as String) }
            storePassword = keystoreProperties["storePassword"] as String?
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
        }
    }

    buildTypes {
        release {
            // Without keystore.properties (e.g. on CI) the release build is left unsigned, as before.
            if (keystoreProperties.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    // Portal client, parser, view models and the whole Compose UI live in :shared.
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.material)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
