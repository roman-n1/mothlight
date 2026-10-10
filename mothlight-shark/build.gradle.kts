plugins { id("com.android.kotlin.multiplatform.library"); kotlin("multiplatform"); `maven-publish` }
kotlin {
    android {
        optimization {
            consumerKeepRules.apply {
                publish = true
                file(rootProject.file("consumer-rules.pro"))
            }
        }
        namespace = "io.github.mothlight.shark"
        compileSdk = 37
        minSdk = 23
        withHostTestBuilder { sourceSetTreeName = "test" }.configure { }
    }
    jvm { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets {
        commonMain.dependencies { api(project(":mothlight-retention")) }
        jvmMain {
            kotlin.srcDir("src/jvmAndroidMain/kotlin")
            dependencies { implementation("com.squareup.leakcanary:shark:2.14") }
        }
        androidMain {
            kotlin.srcDir("src/jvmAndroidMain/kotlin")
            dependencies { implementation("com.squareup.leakcanary:shark:2.14") }
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

tasks.withType<Test>().configureEach { maxHeapSize = "256m" }
