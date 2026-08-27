import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.kutner.dofpro"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.kutner.dofpro"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    /**
     * Release signing, read from an untracked keystore.properties beside the build file,
     * or from the environment when there is no such file (a build server).
     *
     * The credentials are deliberately not in here. This file is in version control and
     * a signing key in version control is a signing key everyone has.
     */
    signingConfigs {
        create("release") {
            val propertiesFile = rootProject.file("keystore.properties")
            val properties = Properties()
            if (propertiesFile.exists()) {
                propertiesFile.inputStream().use { properties.load(it) }
            }

            fun setting(name: String): String? =
                properties.getProperty(name) ?: System.getenv(name)

            // An absolute path is taken as given, so one keystore can serve several
            // projects without a copy of the key in each of them.
            val path = setting("SIGNING_KEY_STORE_PATH") ?: "keystore.jks"
            val keystore = File(path).let { if (it.isAbsolute) it else rootProject.file(path) }

            if (keystore.exists()) {
                storeFile = keystore
                storePassword = setting("KEYSTORE_PASSWORD")
                keyAlias = setting("KEY_ALIAS")
                keyPassword = setting("KEY_PASSWORD")
            } else {
                logger.warn("No keystore at ${keystore.absolutePath}: release builds will be unsigned.")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only when there is actually a key to sign with. Handing the bundle task a
            // signing config whose fields are all null does not produce an unsigned
            // bundle, it produces a NullPointerException from inside bundletool.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
