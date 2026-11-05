pluginManagement {
    val jitpackGroup = providers.gradleProperty("trickuildGroup").getOrElse("com.github.Link2011-Act2.Trickuild")
    val jitpackArtifact = providers.gradleProperty("trickuildArtifact").getOrElse("trickuild-gradle-plugin")
    val trickuildVersion = providers.gradleProperty("trickuildVersion").getOrElse("c6305ca29f2e8e15a50348f64d4bbe077065cbf0")
    val token = providers.environmentVariable("JITPACK_AUTH_TOKEN").orElse(providers.gradleProperty("authToken")).orNull
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io") {
            content {
                includeModule(jitpackGroup, "trickuild-gradle-plugin")
                includeModule(jitpackGroup, "trickuild-core")
                includeModule(jitpackGroup, "trickuild-android-adapter")
                includeModule(jitpackGroup, "dev.trickuild.gradle.plugin")
                includeModule("dev.trickuild", "dev.trickuild.gradle.plugin")
            }
            // JitPack's module metadata for this build identifies the aggregate component.
            metadataSources { mavenPom(); ignoreGradleMetadataRedirection() }
            if (!token.isNullOrBlank()) {
                credentials { username = token; password = "" }
            }
        }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.trickuild" && providers.gradleProperty("trickuildDirectMarker").orNull != "true") {
                useModule("$jitpackGroup:$jitpackArtifact:${requested.version ?: trickuildVersion}")
            }
        }
    }
    plugins {
        id("com.android.application") version "9.1.1"
        id("dev.trickuild") version trickuildVersion
    }
}

dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "trickuild-jitpack-android-consumer"
include(":app")
