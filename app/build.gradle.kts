plugins { id("com.android.application") }

android {
    namespace = "lol.drake.shapeicons"
    compileSdk = 36
    defaultConfig {
        applicationId = "lol.drake.shapeicons"
        minSdk = 24
        targetSdk = 36
        versionCode = 14
        versionName = "0.12"
    }
    buildTypes { release { signingConfig = signingConfigs.getByName("debug") } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
