plugins {
    application
    id("dev.trickuild")
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
application { mainClass.set("example.Main") }

trickuild {
    codename.set("Epica")
    versionName.set("2.2.0-IntDev_rev0")
    buildNumber { timeZone.set("Asia/Tokyo") }
    roulette {
        enabled.set(providers.gradleProperty("roulette").map { it.toBoolean() }.orElse(false))
        namespace.set(providers.gradleProperty("rouletteNamespace"))
    }
}

if (providers.gradleProperty("roulette").orNull == "true") {
    sourceSets.main { java.srcDir("src/roulette/java") }
}
