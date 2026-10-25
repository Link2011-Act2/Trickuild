plugins {
    id("com.android.application")
    id("dev.trickuild")
}

android {
    namespace = "dev.trickuild.consumer"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.trickuild.consumer"
        minSdk = 23
        targetSdk = 36
        versionCode = 42
        versionName = "2.2.0-IntDev_rev0"
    }
    buildFeatures { buildConfig = true }
}

trickuild {
    codename.set("Epica")
    // An absent override inherits Android's existing per-variant versionName.
    versionName.set(providers.environmentVariable("VERSION_NAME").orElse(providers.gradleProperty("consumerVersion")))
    buildNumber {
        hashLength.set(5)
        timeZone.set("Asia/Tokyo")
    }
    roulette {
        enabled.set(providers.gradleProperty("consumerRoulette").map { it.toBoolean() }.orElse(false))
        namespace.set("GlyphBarty")
    }
}
