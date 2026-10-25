pluginManagement {
    if (providers.gradleProperty("trickuild.published").isPresent) {
        repositories { mavenLocal(); gradlePluginPortal() }
        plugins { id("dev.trickuild") version "0.1.0-SNAPSHOT" }
    } else {
        includeBuild("../..")
    }
}
rootProject.name = "TrickuildJvmConsumer"
