plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.expensetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.expensetracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.1"
        vectorDrawables { useSupportLibrary = true }
    }

    // Two editions. "full" reads bank SMS; "lite" declares no SMS permissions at
    // all, so Google's sideload fraud protection does not block installing it.
    // Both share an applicationId and signing key, so either installs straight
    // over the other and keeps existing data.
    flavorDimensions += "capture"
    productFlavors {
        create("full") {
            dimension = "capture"
            buildConfigField("boolean", "SMS_CAPTURE", "true")
        }
        create("lite") {
            dimension = "capture"
            buildConfigField("boolean", "SMS_CAPTURE", "false")
            versionNameSuffix = "-lite"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("ET_KEYSTORE") ?: "../keystore/release.jks")
            storePassword = System.getenv("ET_STORE_PASSWORD") ?: ""
            keyAlias = "expensetracker"
            keyPassword = System.getenv("ET_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            // R8 needs ~3 GB of headroom this machine does not currently have, and the
            // dependency set is small enough that the unshrunk APK stays reasonable.
            // Flip these back on (with a free machine) to roughly halve the APK size.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // FlowRow is used throughout for wrapping chip rows (no horizontal scrolling).
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.documentfile:documentfile:1.0.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.8.5")
}
