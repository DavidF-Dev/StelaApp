import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing is driven by a git-ignored keystore.properties; absent on CI/dev clones.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) FileInputStream(keystorePropertiesFile).use { load(it) }
}

// Single source of truth: versionName drives versionCode (major*10000 + minor*100 + patch),
// so bumping the name is enough and the code can never be forgotten or go backwards.
val stelaVersionName = "1.4.0"
val stelaVersionCode = stelaVersionName.split(".").map(String::toInt).let { (major, minor, patch) ->
    major * 10000 + minor * 100 + patch
}

// Name build outputs "stela-<variant>.apk" rather than the default "app-<variant>.apk".
base.archivesName.set("stela")

android {
    namespace = "dev.davidfdev.stela"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.davidfdev.stela"
        minSdk = 26
        targetSdk = 36
        versionCode = stelaVersionCode
        versionName = stelaVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Sign with the real upload key when present, else debug-sign so release builds
            // stay runnable for testing on machines without the secret keystore.
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // Expose the exported Room schemas to instrumented migration tests.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

ksp {
    // Export each schema version so future migrations have a checked-in baseline.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.glance.appwidget)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.vanniktech.emoji)
    // Bundled colour sprites render every emoji consistently and fully offline (no font dependency).
    implementation(libs.vanniktech.emoji.google)
    // AppCompatActivity (a FragmentActivity) is required by the emoji picker's search dialog.
    implementation(libs.androidx.appcompat)
    // Material Components (View system): hosts the emoji picker in a themed, scrollable BottomSheetDialog.
    implementation(libs.material)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    // Override the older espresso-core that Compose ui-test pulls transitively;
    // pre-3.7 versions call a removed InputManager method on API 34+.
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    constraints {
        implementation(libs.androidx.concurrent.futures) {
            because(
                "AGP 8.13 consistent resolution needs one concurrent-futures across main and androidTest: " +
                    "espresso/test-core (atomic androidx.concurrent group) require 1.2.0, but profileinstaller pulls 1.1.0",
            )
        }
    }
}
