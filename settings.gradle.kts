pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "mothlight"
include(":mothlight-core")
include(":mothlight-compose", ":mothlight-ktor", ":sample-desktop")
include(":mothlight-okhttp")
include(":mothlight-android", ":sample-android")

include(":mothlight-sqlite", ":mothlight-room", ":mothlight-room3")
