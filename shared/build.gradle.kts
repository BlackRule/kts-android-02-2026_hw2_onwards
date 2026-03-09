import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.kotlin.dsl.configure

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

val enableAndroidTargets = providers.gradleProperty("enableAndroidTargets").orNull?.toBoolean() != false

if (enableAndroidTargets) {
    apply(plugin = libs.plugins.androidLibrary.get().pluginId)
}

kotlin {
    if (enableAndroidTargets) {
        androidTarget {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            // put your Multiplatform dependencies here
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

if (enableAndroidTargets) {
    extensions.configure<LibraryExtension> {
        namespace = "com.example.myapplication.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        defaultConfig {
            minSdk = libs.versions.android.minSdk.get().toInt()
        }
    }
}
