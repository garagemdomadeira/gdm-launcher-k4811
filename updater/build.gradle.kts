plugins {
    id("com.android.application")
}

android {
    namespace = "com.k4811.updater"
    // compileSdk só define quais APIs o compilador conhece; a compatibilidade vem de minSdk/targetSdk.
    compileSdk = 36

    defaultConfig {
        // applicationId: definido na tabela de packages do build.gradle.kts da raiz
        minSdk = 17     // como os outros apps da suíte
        targetSdk = 19  // permissões concedidas na instalação (ver AGENTS.md)
        versionCode = 2  // GDM Atualizações 1.1
        versionName = "1.1"
    }

    buildFeatures {
        aidl = false
        buildConfig = false
    }

    // -PsemSistema: tira o sharedUserId (para testar no emulador, que tem outra chave de plataforma).
    if (project.hasProperty("semSistema")) {
        sourceSets.getByName("release").manifest.srcFile("src/teste/AndroidManifest.xml")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Assinatura: a da suíte pelo build.gradle.kts da raiz; depois tools/assina_plataforma.sh troca pela
            // chave de plataforma da ROM (sharedUserId android.uid.system exige).
        }
    }

    lint {
        disable += listOf("ExpiredTargetSdkVersion", "OldTargetApi", "ProtectedPermissions")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":common"))
}
