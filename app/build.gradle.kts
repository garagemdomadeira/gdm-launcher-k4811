plugins {
    id("com.android.application")
}

android {
    namespace = "com.garagemdomadeira.nwd.launcher"
    // compileSdk only defines which APIs the compiler knows; compatibility comes from minSdk/targetSdk.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.garagemdomadeira.nwd.launcher"
        minSdk = 17     // Android 4.2, oldest NWD head units
        targetSdk = 19  // Android 4.4, keeps legacy behavior on the head unit
        versionCode = 5
        versionName = "0.5.0"
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key to install directly on the head unit.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        // App installed directly on the head unit (outside the Play Store): the old target is intentional.
        disable += listOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
