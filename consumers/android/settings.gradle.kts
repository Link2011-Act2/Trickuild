pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    if (!providers.gradleProperty("trickuild.testkit").isPresent) {
        plugins {
            id("com.android.application") version providers.gradleProperty("consumerAgp").getOrElse("9.1.1")
        }
    }
    if (providers.gradleProperty("trickuild.published").isPresent) {
        repositories { mavenLocal() }
        plugins { id("dev.trickuild") version "0.1.0-SNAPSHOT" }
    } else if (!providers.gradleProperty("trickuild.testkit").isPresent) {
        includeBuild("../..")
    }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "TrickuildAndroidConsumer"
include(":app")
