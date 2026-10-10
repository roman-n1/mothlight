plugins { id("com.android.kotlin.multiplatform.library"); kotlin("multiplatform"); `maven-publish` }
kotlin {
    android {
        namespace = "io.github.mothlight.okhttp"
        compileSdk = 37
        minSdk = 23
        withHostTestBuilder { sourceSetTreeName = "test" }.configure { }
    }
    jvm { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets {
        commonMain.dependencies { api(project(":mothlight-core")) }
        jvmMain {
            kotlin.srcDir("src/jvmAndroidMain/kotlin")
            dependencies { api("com.squareup.okhttp3:okhttp:4.12.0") }
        }
        androidMain {
            kotlin.srcDir("src/jvmAndroidMain/kotlin")
            dependencies { api("com.squareup.okhttp3:okhttp:4.12.0") }
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.squareup.retrofit2:retrofit:3.0.0")
        }
    }
}
