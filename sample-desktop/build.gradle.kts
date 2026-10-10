plugins { id("com.google.devtools.ksp"); kotlin("jvm"); kotlin("plugin.compose"); id("org.jetbrains.compose") }
dependencies {
    implementation(project(":mothlight-room3"))
    implementation("androidx.sqlite:sqlite-bundled:2.7.0")
    ksp("androidx.room3:room3-compiler:3.0.0")
    implementation(project(":mothlight-compose"))
    implementation(project(":mothlight-ktor"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.components:components-resources:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("io.ktor:ktor-client-mock:3.5.0")
}
compose.desktop { application { mainClass = "io.github.mothlight.sample.MainKt" } }
compose.resources { packageOfResClass = "io.github.mothlight.sample.resources" }
