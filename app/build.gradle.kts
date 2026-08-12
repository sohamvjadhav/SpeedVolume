plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.speedvolume"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.speedvolume"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}