plugins {
    id("com.google.devtools.ksp") version "2.3.10" apply false
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("com.android.library") version "9.3.0" apply false
    id("com.android.application") version "9.3.0" apply false
    id("com.android.kotlin.multiplatform.library") version "9.3.0" apply false
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
}
allprojects {
    group = "io.github.roman-n1.mothlight"
    version = "0.1.0-SNAPSHOT"
}
