plugins {
    id("com.android.application")
    id("dev.trickuild")
}

android {
    namespace = "dev.trickuild.externalconsumer"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.trickuild.externalconsumer"
        minSdk = 23
        targetSdk = 36
        versionCode = 42
        versionName = "2.2.0-IntDev_rev0"
    }
    buildFeatures { buildConfig = true }
    if (providers.gradleProperty("roulette").orNull == "true") {
        sourceSets.getByName("main").java.srcDir("src/roulette/java")
    }
}

trickuild {
    codename.set("Epica")
    buildNumber { timeZone.set("Asia/Tokyo") }
    roulette {
        enabled.set(providers.gradleProperty("roulette").map { it.toBoolean() }.orElse(false))
        namespace.set(providers.gradleProperty("rouletteNamespace"))
    }
}
