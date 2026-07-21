import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

android {
    compileSdk = 36
    namespace = "com.pasich.encly"

    defaultConfig {
        applicationId = "com.pasich.encly"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "1.1.1"
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/INDEX.LIST"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }



    // Release signing is read from an untracked keystore.properties (or Play upload key).
    // When absent (e.g. CI without secrets), the release build is simply left unsigned.
    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply { propsFile.inputStream().use { load(it) } }
                storeFile = props.getProperty("storeFile")?.let { file(it) }
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (rootProject.file("keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    hilt {
        enableAggregatingTask = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        abortOnError = false
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }


    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
        }
    }


}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
}

val roomVersion by extra("2.7.2")
val composeVersion by extra("1.8.3")
val materialVersion by extra("1.3.2")


dependencies {

    // Jetpack Compose
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:$materialVersion")
    implementation("androidx.compose.material3:material3-window-size-class:$materialVersion")
    implementation("androidx.compose.foundation:foundation:$composeVersion")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Drag and Drop
    implementation("sh.calvin.reorderable:reorderable:2.5.1")

    // Room
    implementation("androidx.compose.runtime:runtime-livedata:$composeVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.core:core-ktx:1.16.0")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Biometric Authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // Dagger Hilt - Use KSP consistently
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")

    // Other dependencies
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.20")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Cipher
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("cash.z.ecc.android:kotlin-bip39:1.0.9")


    // Security Crypto
    implementation("androidx.security:security-crypto:1.1.0-beta01")

    // Compose Text with Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.8.3")

    implementation("com.composables:icons-lucide:1.1.0")

    // Тести
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.mockito:mockito-core:5.18.0")

}