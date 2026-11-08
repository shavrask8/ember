import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing: keystore.properties next to this project or in ~/.ember-keys (never committed).
val keyProps = listOf(rootProject.file("keystore.properties"), File(System.getProperty("user.home"), ".ember-keys/keystore.properties"))
    .firstOrNull { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "ua.shavra.claudelimits"
    compileSdk = 36

    defaultConfig {
        applicationId = "ua.shavra.claudelimits"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keyProps != null) create("release") {
            storeFile = file(keyProps.getProperty("storeFile"))
            storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias")
            keyPassword = keyProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
