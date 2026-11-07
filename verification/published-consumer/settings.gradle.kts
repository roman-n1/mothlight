pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    url = uri(providers.gradleProperty("mothlightPublicationRepository").get())
                }
            }
            filter { includeGroup("io.github.roman-n1.mothlight") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "mothlight-published-consumer"
