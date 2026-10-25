plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
}

allprojects {
    group = providers.gradleProperty("group").getOrElse("dev.trickuild")
    version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

val androidTestPluginClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":android-adapter"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    androidTestPluginClasspath("com.android.tools.build:gradle:${providers.gradleProperty("trickuild.androidAgp").getOrElse("9.1.1")}")
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
    useJUnitPlatform { excludeTags("android") }
}

val androidFunctionalTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs isolated Android APK tests; requires -Ptrickuild.androidSdk=/path/to/sdk."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("android") }
    systemProperty("trickuild.androidSdk", providers.gradleProperty("trickuild.androidSdk")
        .orElse(providers.environmentVariable("ANDROID_HOME"))
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT")).getOrElse(""))
    systemProperty("trickuild.projectRoot", layout.projectDirectory.asFile.absolutePath)
    systemProperty("trickuild.androidAgp", providers.gradleProperty("trickuild.androidAgp").getOrElse("9.1.1"))
    systemProperty("trickuild.androidGradleHome", providers.gradleProperty("trickuild.androidGradleHome").getOrElse(""))
    systemProperty("trickuild.testKitDir", providers.gradleProperty("trickuild.testKitDir").getOrElse(""))
    systemProperty("trickuild.androidPluginClasspath", androidTestPluginClasspath.asPath)
    inputs.files(androidTestPluginClasspath)
    inputs.files(fileTree("consumers/android") {
        exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", "**/.idea/**", "**/local.properties")
    })
    shouldRunAfter(tasks.test)
}

java { withSourcesJar() }

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "trickuild-gradle-plugin"
            pom {
                name.set("Trickuild Gradle Plugin")
                description.set("Build metadata and optional deterministic personality roulette for JVM and Android.")
                url.set("https://github.com/Link2011-Act2/Trickuild")
            }
        }
    }
}
