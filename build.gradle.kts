import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "com.clu.idea"
version = "1.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        java.srcDirs("src")
        resources.srcDirs("resources")
    }
    test {
        java.srcDirs("src/test/java")
        resources.srcDirs("src/test/resources")
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.apache.commons:commons-lang3:3.19.0")

    intellijPlatform {
        intellijIdea("2022.3.3")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "My Pojo To Json"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "223"
            untilBuild = "251.*"
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    named("instrumentCode") {
        enabled = false
    }

    wrapper {
        gradleVersion = "8.14.3"
    }
}
