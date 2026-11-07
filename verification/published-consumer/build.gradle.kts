plugins {
    kotlin("jvm") version "2.4.21"
    application
}

kotlin { jvmToolchain(21) }

val mothlightVersion = providers.gradleProperty("mothlightVersion").get()
val coreRelease = providers.gradleProperty("mothlightReleaseCoreOnly").orNull == "true"
dependencies {
    implementation("io.github.roman-n1.mothlight:mothlight-core:$mothlightVersion")
    implementation("io.github.roman-n1.mothlight:mothlight-compose:$mothlightVersion")
    implementation("io.github.roman-n1.mothlight:mothlight-ktor:$mothlightVersion")
    implementation("io.github.roman-n1.mothlight:mothlight-okhttp:$mothlightVersion")
    implementation("io.github.roman-n1.mothlight:mothlight-room:$mothlightVersion")
    if (!coreRelease) {
        implementation("io.github.roman-n1.mothlight:mothlight-room3:$mothlightVersion")
    }
}

application { mainClass.set("MainKt") }
