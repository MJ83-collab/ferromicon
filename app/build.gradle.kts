import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "it.ferromicon"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.ferromicon"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "0.0.21"
    }

    // Chiave di firma fissa (così l'APK si installa sopra quello precedente). Il file della chiave non sta
    // nella repo: la compilazione su GitHub lo ricrea dai segreti FM_KEYSTORE_BASE64 e FM_KEYSTORE_PASSWORD.
    val chiave = file("ferromicon.jks")
    val passwordChiave: String? = System.getenv("FM_KEYSTORE_PASSWORD")
    val firmaFissa = chiave.exists() && !passwordChiave.isNullOrEmpty()
    signingConfigs {
        if (firmaFissa) {
            create("ferromicon") {
                storeFile = chiave
                storePassword = passwordChiave
                keyAlias = "ferromicon"
                keyPassword = passwordChiave
            }
        }
    }

    buildTypes {
        debug {
            if (firmaFissa) signingConfig = signingConfigs.getByName("ferromicon")
        }
        release {
            isMinifyEnabled = false
            if (firmaFissa) signingConfig = signingConfigs.getByName("ferromicon")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Il progetto usa solo API di sistema: non bloccare la build per avvisi di stile.
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    // Lettura del testo dei PDF (cedolini e CU), tutto sul telefono
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.core:core:1.13.1")
}
