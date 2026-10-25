import java.util.Base64

plugins {
    // Reuse the Kotlin plugin supplied by the root kotlin-dsl plugin.
    kotlin("jvm")
    `maven-publish`
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
    useJUnitPlatform { excludeTags("reference") }
}

tasks.register<Test>("referenceCompatibilityTest") {
    group = "verification"
    description = "Read-only hash compatibility checks against the four sibling Android apps."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("reference") }
    outputs.upToDateWhen { false }
    outputs.doNotCacheIf("Checks the current contents of external reference repositories") { true }
    val referenceRoot = file(providers.gradleProperty("trickuild.referenceRoot")
        .getOrElse(rootProject.layout.projectDirectory.dir("..").asFile.absolutePath))
    systemProperty("trickuild.referenceRoot", referenceRoot.absolutePath)
    listOf("glyphvisualizer", "shutup", "schedulernittc", "beastlocator").forEach { name ->
        val reference = referenceRoot.resolve(name)
        // Use the actual Gradle FileTree, including Ant's default VCS/OS exclusions.
        val files = fileTree(reference.resolve("app/src")) { exclude("**/build/**") }.files + listOf(
            reference.resolve("app/build.gradle.kts"), reference.resolve("build.gradle.kts"),
            reference.resolve("settings.gradle.kts"), reference.resolve("gradle/libs.versions.toml"),
        ).filter { it.isFile }
        val encodedPaths = files.joinToString(",") {
            Base64.getEncoder().encodeToString(it.relativeTo(reference).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
        }
        val manifest = layout.buildDirectory.file("reference-inputs/$name.txt").get().asFile
        systemProperty("trickuild.referenceFiles.$name", manifest.absolutePath)
        inputs.files(files)
        doFirst {
            manifest.parentFile.mkdirs()
            manifest.writeText(encodedPaths, Charsets.UTF_8)
        }
    }
}

java { withSourcesJar() }
publishing {
    publications.create<MavenPublication>("core") {
        from(components["java"])
        artifactId = "trickuild-core"
    }
}
