plugins { id("com.android.kotlin.multiplatform.library"); kotlin("multiplatform"); `maven-publish` }
kotlin {
    android {
        namespace = "io.github.mothlight.ktor"
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
            api("io.ktor:ktor-client-core:3.5.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("io.ktor:ktor-client-mock:3.5.0")
        }
    }
}
