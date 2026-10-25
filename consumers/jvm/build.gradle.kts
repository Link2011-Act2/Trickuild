plugins {
    application
    id("dev.trickuild")
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
application { mainClass.set("example.Main") }

trickuild {
    codename.set("Epica")
    versionName.set(providers.environmentVariable("VERSION_NAME").orElse("2.2.0-IntDev_rev0"))
    buildNumber { timeZone.set("Asia/Tokyo") }
    roulette {
        enabled.set(providers.gradleProperty("consumerRoulette").map { it.toBoolean() }.orElse(false))
        namespace.set("GlyphBarty")
    }
}
