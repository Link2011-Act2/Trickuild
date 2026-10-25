plugins {
    // Reuse the Kotlin plugin supplied by the root kotlin-dsl plugin.
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
