buildscript {
    val agpVersion by extra("8.11.1")
    val kotlinVersion by extra("2.0.21")
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:$agpVersion")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.56.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    id("com.google.dagger.hilt.android") version "2.56.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory.get().asFile)
}
