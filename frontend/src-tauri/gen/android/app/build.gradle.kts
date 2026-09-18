import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("rust")
}

val tauriProperties = Properties().apply {
    val propFile = file("tauri.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

android {
    compileSdk = 36
    namespace = "co.sena.ctma.sicot"
    defaultConfig {
        manifestPlaceholders["usesCleartextTraffic"] = "false"
        applicationId = "co.sena.ctma.sicot"
        minSdk = 24
        targetSdk = 36
        versionCode = tauriProperties.getProperty("tauri.android.versionCode", "1").toInt()
        versionName = tauriProperties.getProperty("tauri.android.versionName", "1.0")
    }
    // Firma de la compilación de publicación (ADR-013).
    //
    // La llave NUNCA está en el repositorio: se lee de variables de entorno que
    // el flujo de publicación rellena desde los secretos de GitHub. Si faltan,
    // la compilación de publicación sale sin firmar —como salía antes de este
    // cambio— en vez de fallar, para que el job de CI que solo comprueba que el
    // proyecto compila (android.yml, en depuración) no necesite ningún secreto.
    //
    // Por qué la custodia importa tanto como para escribirla en un ADR: Android
    // exige que cada versión de una aplicación esté firmada con la MISMA llave
    // que la anterior. Si la llave se pierde, ningún teléfono con SICOT instalado
    // puede actualizarse sin desinstalar; si se filtra, cualquiera puede publicar
    // una «actualización» que Android aceptaría como legítima.
    val llaveDeFirma = System.getenv("SICOT_ANDROID_KEYSTORE")
    signingConfigs {
        if (llaveDeFirma != null) {
            create("publicacion") {
                storeFile = file(llaveDeFirma)
                storePassword = System.getenv("SICOT_ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SICOT_ANDROID_KEY_ALIAS")
                // PKCS12 no admite una contraseña de llave distinta de la del
                // almacén, así que es la misma.
                keyPassword = System.getenv("SICOT_ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            packaging {                jniLibs.keepDebugSymbols.add("*/arm64-v8a/*.so")
                jniLibs.keepDebugSymbols.add("*/armeabi-v7a/*.so")
                jniLibs.keepDebugSymbols.add("*/x86/*.so")
                jniLibs.keepDebugSymbols.add("*/x86_64/*.so")
            }
        }
        getByName("release") {
            if (llaveDeFirma != null) {
                signingConfig = signingConfigs.getByName("publicacion")
            }
            isMinifyEnabled = true
            proguardFiles(
                *fileTree(".") { include("**/*.pro") }
                    .plus(getDefaultProguardFile("proguard-android-optimize.txt"))
                    .toList().toTypedArray()
            )
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
    }
}

rust {
    rootDirRel = "../../../"
}

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}

apply(from = "tauri.build.gradle.kts")