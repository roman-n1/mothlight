plugins {
    id("com.google.devtools.ksp") version "2.3.13" apply false
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    id("com.android.library") version "9.5.0" apply false
    id("com.android.application") version "9.5.0" apply false
    id("com.android.kotlin.multiplatform.library") version "9.5.0" apply false
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
}
allprojects {
    group = "io.github.roman-n1.mothlight"
    version = "0.1.0-SNAPSHOT"
}
