plugins { id("com.android.library"); kotlin("plugin.compose"); `maven-publish` }
android {
    namespace = "io.github.mothlight.android"
    compileSdk = 37
    defaultConfig { minSdk = 23; consumerProguardFiles(rootProject.file("consumer-rules.pro")) }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    publishing { singleVariant("release") { withSourcesJar() } }
}
dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    api(project(":mothlight-compose"))
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
}
afterEvaluate {
    publishing { publications { register<MavenPublication>("release") { from(components["release"]) } } }
}
