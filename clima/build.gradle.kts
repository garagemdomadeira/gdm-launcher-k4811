plugins {
    id("com.android.application")
}

android {
    namespace = "com.k4811.clima"
    compileSdk = 36

    defaultConfig {
        // applicationId: tabela de packages do build.gradle.kts da raiz (com.nwd.climate)
        minSdk = 17
        targetSdk = 19
        versionCode = 20271300
        versionName = "2.1.3"
    }

    buildFeatures {
        aidl = false
        buildConfig = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        disable += listOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":common"))
}
