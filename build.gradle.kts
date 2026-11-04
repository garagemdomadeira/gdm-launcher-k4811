import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.util.Properties

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
}

/*
 * Package de cada app (applicationId), definido só aqui. As classes Java ficam em com.k4811.*.
 *
 * Os apps de função usam o package original da NWD: assinados com a mesma chave dos originais,
 * instalam por cima deles (versionCode maior). Exceções, que ficam ao lado dos originais:
 *   - launcher: com.garagemdomadeira.nwd.launcher (o original é com.android.launcher);
 *   - car-setting: com.nwd.car.setting (o original, com.android.car.setting, usa sharedUserId
 *     com.android.settings, que exige a chave de plataforma).
 *
 * O módulo common recebe essa tabela no BuildConfig (Apps.RADIO etc.), para o dock abrir os apps.
 */
val packages = linkedMapOf(
    "launcher" to "com.garagemdomadeira.nwd.launcher",
    "car-setting" to "com.nwd.car.setting",
    "radio" to "com.nwd.radio",
    "music" to "com.nwd.android.music.ui",
    "bt-music" to "com.nwd.bt.music",
    "phone" to "com.nwd.android.phone",
    "eq" to "com.nwd.audioset",
    "fan" to "com.nwd.cpumonitor",
)

/** Nome do campo no BuildConfig do common para cada módulo (PKG_RADIO, PKG_BT_MUSIC…). */
fun fieldOf(module: String) = "PKG_" + module.uppercase().replace('-', '_')

/*
 * Assinatura dos APKs de release.
 *
 * keystore.properties na raiz (fora do git, ver keystore.properties.example):
 *   storeFile=caminho/para/chave.jks
 *   storePassword=...
 *   keyAlias=...
 *   keyPassword=...
 * Sem o arquivo, o release sai assinado com a chave de debug.
 */
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            defaultConfig.applicationId = packages.getValue(project.name)
            val key = if (signing.getProperty("storeFile").isNullOrBlank()) null else signingConfigs.create("key") {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
            buildTypes.getByName("release").signingConfig = key ?: signingConfigs.getByName("debug")
        }
        // Beta: o workflow beta.yml injeta a versão via -PbetaVersionName / -PbetaVersionCode.
        // Sobrescreve na API de variantes (depois do android{} de cada app), senão o valor fixo vence.
        // Sem essas props (build normal/release), vale o que está no build.gradle.kts de cada app.
        val betaName = (findProperty("betaVersionName") as String?)?.takeIf { it.isNotBlank() }
        val betaCode = (findProperty("betaVersionCode") as String?)?.toIntOrNull()
        if (betaName != null || betaCode != null) {
            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    variant.outputs.forEach { output ->
                        if (betaName != null) output.versionName.set(betaName)
                        if (betaCode != null) output.versionCode.set(betaCode)
                    }
                }
            }
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            buildFeatures.buildConfig = true
            for ((module, pkg) in packages) defaultConfig.buildConfigField("String", fieldOf(module), "\"$pkg\"")
        }
    }
}
