import com.vanniktech.maven.publish.Checksum
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.PublishingExtension

plugins {
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    id("com.google.devtools.ksp") version "2.3.13" apply false
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    id("com.android.library") version "9.5.0" apply false
    id("com.android.application") version "9.5.0" apply false
    id("com.android.kotlin.multiplatform.library") version "9.5.0" apply false
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
}
allprojects {
    group = "io.github.roman-n1.mothlight"
    version = providers.gradleProperty("mothlightVersion").get()
}

subprojects {
    val central = providers.gradleProperty("mothlightPublishToCentral").map(String::toBoolean).getOrElse(false)
    val localRepository = providers.gradleProperty("mothlightPublicationRepository")
    val coreRelease = providers.gradleProperty("mothlightReleaseCoreOnly").orNull == "true"
    val includedInRelease = !coreRelease || path != ":mothlight-room3"
    pluginManager.withPlugin("maven-publish") {
        pluginManager.apply("com.vanniktech.maven.publish")
        extensions.configure<MavenPublishBaseExtension> {
            if (central && includedInRelease) {
                // Keep the deployment sidecars in sync with scripts/check_central_budget.py.
                checksums(Checksum.MD5, Checksum.SHA1)
                excludeSignatureChecksums()
                publishToMavenCentral()
                signAllPublications()
            }
            pom {
                name.set(project.name)
                description.set("Multiplatform debugging tools for Kotlin applications")
                url.set("https://github.com/roman-n1/mothlight")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("roman-n1")
                        url.set("https://github.com/roman-n1")
                    }
                }
                scm {
                    url.set("https://github.com/roman-n1/mothlight")
                    connection.set("scm:git:https://github.com/roman-n1/mothlight.git")
                    developerConnection.set("scm:git:ssh://git@github.com/roman-n1/mothlight.git")
                }
            }
        }
        if (localRepository.isPresent && includedInRelease) {
            extensions.configure<PublishingExtension> {
                repositories.maven {
                    name = "ReleaseCheck"
                    url = uri(localRepository.get())
                }
            }
        }
    }
}
