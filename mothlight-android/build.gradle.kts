import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins { id("com.android.library"); kotlin("plugin.compose"); `maven-publish` }
android {
    namespace = "io.github.mothlight.android"
    compileSdk = 37
    defaultConfig { minSdk = 23; consumerProguardFiles(rootProject.file("consumer-rules.pro")) }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
dependencies {
    api(project(":mothlight-compose"))
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
}
extensions.configure<MavenPublishBaseExtension> {
    configure(AndroidSingleVariantLibrary(variant = "release"))
}
