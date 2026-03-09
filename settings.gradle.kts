rootProject.name = "MyApplication"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val includeComposeApp = startParameter.projectProperties["includeComposeApp"] != "false"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":server")
include(":shared")

if (includeComposeApp) {
    include(":composeApp")
}
