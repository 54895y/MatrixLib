import io.izzel.taboolib.gradle.*
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    `maven-publish`
    id("io.izzel.taboolib") version "2.0.36"
    id("org.jetbrains.dokka") version "1.9.20"
    kotlin("jvm") version "2.3.0"
}

base {
    archivesName.set("MatrixLib")
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
    relocate("org.bstats", "${project.group}.libs.bstats")
    relocate("com.google.gson", "${project.group}.libs.gson")
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    taboo("org.bstats:bstats-bukkit:3.2.1")
    taboo("com.google.code.gson:gson:2.11.0")
    compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs") {
        include("*.jar")
    })
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<DokkaTask>().configureEach {
    moduleName.set("MatrixLib API")
    dokkaSourceSets.configureEach {
        includes.from("docs/api/overview.md")
        jdkVersion.set(8)
        skipDeprecated.set(false)
        reportUndocumented.set(false)
        noStdlibLink.set(false)
        noJdkLink.set(false)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("1.8")
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("dokkaJavadoc"))
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaJavadoc"))
}

tasks.named("assemble") {
    dependsOn(dokkaJavadocJar)
}

val sharedApiRepoDirCandidates = listOf(
    layout.projectDirectory.dir("../_publish/matrix-api"),
    layout.projectDirectory.dir("../../_publish/matrix-api")
)
val sharedApiRepoDir = sharedApiRepoDirCandidates.firstOrNull {
    val parent = it.asFile.parentFile
    parent != null && parent.exists()
} ?: sharedApiRepoDirCandidates.first()

publishing {
    publications {
        create<MavenPublication>("matrixlibApi") {
            from(components["java"])
            artifactId = "matrixlib-api"
            artifact(dokkaJavadocJar)
        }
    }
    repositories {
        maven {
            name = "matrixPublic"
            url = uri(sharedApiRepoDir)
        }
    }
}

tasks.register("publishMatrixApi") {
    group = "publishing"
    description = "Publish MatrixLib API artifact to the shared local repository."
    dependsOn("publishAllPublicationsToMatrixPublicRepository")
}

tasks.register("generateJavadoc") {
    group = "documentation"
    description = "Generate Javadoc-style API docs with Dokka."
    dependsOn("dokkaJavadoc")
}
