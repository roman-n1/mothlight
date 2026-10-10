plugins { id("com.android.kotlin.multiplatform.library"); kotlin("multiplatform"); kotlin("plugin.compose"); id("org.jetbrains.compose"); `maven-publish` }
kotlin {
    android {
        namespace = "io.github.mothlight.compose"
        compileSdk = 37
        minSdk = 23
        withHostTestBuilder { sourceSetTreeName = "test" }.configure { }
    }
    jvm { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    iosArm64()
    iosSimulatorArm64()
    js { browser(); nodejs() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser(); nodejs() }
    sourceSets {
        commonMain.dependencies {
            api(project(":mothlight-core"))
            api(project(":mothlight-memory"))
            api(project(":mothlight-retention"))
            api("org.jetbrains.compose.runtime:runtime:1.11.1")
            api("org.jetbrains.compose.ui:ui:1.11.1")
            implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.components:components-resources:1.11.1")
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.11.1")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
compose.resources { packageOfResClass = "io.github.mothlight.compose.resources" }
