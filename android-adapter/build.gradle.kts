plugins {
    kotlin("jvm")
    `maven-publish`
}

kotlin { jvmToolchain(21) }

dependencies {
    compileOnly(gradleApi())
    // Public API only. AGP is supplied by the consuming Android build, never transitively applied.
    compileOnly("com.android.tools.build:gradle-api:8.9.1")
}

java { withSourcesJar() }
publishing {
    publications.create<MavenPublication>("adapter") {
        from(components["java"])
        artifactId = "trickuild-android-adapter"
    }
}
