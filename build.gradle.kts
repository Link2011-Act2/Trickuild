plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

allprojects {
    group = "dev.trickuild"
    version = "0.1.0-SNAPSHOT"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("trickuild") {
            id = "dev.trickuild"
            implementationClass = "dev.trickuild.gradle.TrickuildPlugin"
            displayName = "Trickuild"
            description = "An independent Gradle plugin with an Android-independent core."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
