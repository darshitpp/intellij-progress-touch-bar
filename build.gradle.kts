plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "dev.darshit"
version = "0.1.1"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    intellijPlatform {
        intellijIdea("2026.3")
        pluginVerifier()
    }
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true") // BarRenderer paints offscreen Swing
}

intellijPlatform {
    pluginVerification {
        ides { listOf("2026.1", "2026.2", "2026.3").forEach { create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea, it) } }
    }
    pluginConfiguration { ideaVersion { sinceBuild = "261" } }
}
