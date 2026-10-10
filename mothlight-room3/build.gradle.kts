plugins { id("com.android.kotlin.multiplatform.library"); kotlin("multiplatform"); `maven-publish`; id("com.google.devtools.ksp") }
kotlin {
    android { namespace = "io.github.mothlight.room3"; compileSdk = 37; minSdk = 23 }
    jvm { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { api(project(":mothlight-core")); api(project(":mothlight-sqlite")); api("androidx.room3:room3-runtime:3.0.0"); implementation("androidx.sqlite:sqlite-async:2.7.0") }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("androidx.sqlite:sqlite-bundled:2.7.0")
        }
    }
}

dependencies { add("kspJvmTest", "androidx.room3:room3-compiler:3.0.0") }
