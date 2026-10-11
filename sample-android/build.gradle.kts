plugins { id("com.google.devtools.ksp"); id("com.android.application") }
android {
    namespace = "io.github.mothlight.sample.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.mothlight.sample.android"
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation(project(":mothlight-shark"))
    implementation(project(":mothlight-room"))
    implementation("androidx.sqlite:sqlite-bundled:2.7.0")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation(project(":mothlight-android"))
    implementation(project(":mothlight-okhttp"))
    implementation("androidx.activity:activity:1.13.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
