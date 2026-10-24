plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "com.deluxe.ps4ftp"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.deluxe.ps4ftp"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
}
dependencies {
    implementation("commons-net:commons-net:3.11.1")
}
