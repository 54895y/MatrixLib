import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    id("io.izzel.taboolib") version "2.0.36"
    kotlin("jvm") version "2.3.0"
}

taboolib {
    env {
        install(Basic, Bukkit)
    }
    description {
        name = "MatrixLib"
        bukkitApi("1.12")
    }
    version {
        taboolib = "6.2.4-99fb800"
        coroutines = "1.8.1"
    }
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
    compileOnly(kotlin("stdlib"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("1.8")
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
