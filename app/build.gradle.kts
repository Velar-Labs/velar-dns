plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Assinatura de release: vem de variáveis de ambiente (GitHub Actions secrets).
// Sem elas, o release é assinado com a chave de debug (serve para testar,
// mas não permite atualizar por cima de uma versão assinada com outra chave).
val releaseKeystore: String? = System.getenv("VELAR_KEYSTORE_PATH")

android {
    namespace = "app.velardns"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.velardns.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                // trim(): ignora espaços ou quebras de linha coladas junto nos secrets.
                val storePass = System.getenv("VELAR_KEYSTORE_PASSWORD")?.trim().orEmpty()
                storeFile = file(releaseKeystore)
                storePassword = storePass
                keyAlias = System.getenv("VELAR_KEY_ALIAS")?.trim()?.ifEmpty { null } ?: "velar"
                // Chave PKCS12: a senha da chave é a mesma do keystore.
                keyPassword = storePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
