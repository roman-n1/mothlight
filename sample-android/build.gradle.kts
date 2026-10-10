plugins { id("com.google.devtools.ksp"); id("com.android.application") }
android {
    namespace = "io.github.mothlight.sample.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.mothlight.sample.android"
        minSdk = 23
        targetSdk = 36
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
    implementation(project(":mothlight-room"))
    implementation("androidx.sqlite:sqlite-bundled:2.7.0")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation(project(":mothlight-android"))
    implementation(project(":mothlight-okhttp"))
    implementation("androidx.activity:activity:1.13.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
}
