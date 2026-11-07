plugins { id("com.google.devtools.ksp"); kotlin("jvm"); kotlin("plugin.compose"); id("org.jetbrains.compose") }
dependencies {
    implementation(project(":mothlight-room3"))
    implementation("androidx.sqlite:sqlite-bundled:2.7.1")
    ksp("androidx.room3:room3-compiler:3.0.3")
    implementation(project(":mothlight-compose"))
    implementation(project(":mothlight-ktor"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.components:components-resources:1.12.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("io.ktor:ktor-client-cio:3.6.1")
    testImplementation("io.ktor:ktor-client-mock:3.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.compose.ui:ui-test:1.12.2")
}
kotlin { jvmToolchain(21) }
val desktopJavaLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
compose.desktop {
    application {
        mainClass = "io.github.mothlight.sample.MainKt"
        javaHome = desktopJavaLauncher.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            packageVersion = project.version.toString().substringBefore('-')
            // macOS jpackage requires a positive major version.
            macOS { packageVersion = "1.0.0" }
            modules("java.instrument", "java.management", "jdk.unsupported")
        }
    }
}
compose.resources { packageOfResClass = "io.github.mothlight.sample.resources" }
