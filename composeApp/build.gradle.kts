import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

fun String.toBuildConfigString(): String {
    val escaped = replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

fun readLocalProperty(name: String): String {
    val candidateFiles = listOf(
        rootProject.file("secrets/android/local.properties"),
        rootProject.file("local.properties"),
    )

    candidateFiles.firstOrNull { it.isFile }?.let { propertiesFile ->
        val properties = Properties()
        propertiesFile.inputStream().use(properties::load)
        return properties.getProperty(name).orEmpty()
    }

    return ""
}

val serverBaseUrl = providers.gradleProperty("SERVER_BASE_URL")
    .orElse(provider { readLocalProperty("SERVER_BASE_URL") })
    .orElse("https://195.46.171.236:9878")
    .map { value -> value.trim().removeSuffix("/") }

val mapKitApiKey = providers.gradleProperty("MAPKIT_API_KEY")
    .orElse(provider { readLocalProperty("MAPKIT_API_KEY") })
    .map { value -> value.trim() }

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.coil.network.okhttp)
            implementation(libs.ktor.clientOkHttp)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.yandex.mapkit.lite)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.coil.compose)
            implementation(libs.napier)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.clientLogging)
            implementation(libs.ktor.serializationKotlinxJson)
            implementation(projects.shared)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.example.myapplication"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SERVER_BASE_URL", serverBaseUrl.get().toBuildConfigString())
        buildConfigField("String", "MAPKIT_API_KEY", mapKitApiKey.get().toBuildConfigString())
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
