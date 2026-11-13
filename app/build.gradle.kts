import com.github.jk1.license.render.JsonReportRenderer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.github.jk1.dependency-license-report") version "2.9"
}

android {
    namespace = "it.ferromicon"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.ferromicon"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "0.0.24"
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

// ---- Licenze open source -------------------------------------------------------------------
// Ad ogni build si legge l'elenco reale delle dipendenze (anche indirette) e lo si mette nell'app
// (assets/licenze.json, schermata Impostazioni > Licenze). Scrive anche LICENZE.md nella radice.
// Se compare una licenza non libera o sconosciuta, la build si ferma.
licenseReport {
    renderers = arrayOf(JsonReportRenderer("dipendenze.json"))
    configurations = arrayOf("releaseRuntimeClasspath")
    excludeBoms = true
}

val cartellaLicenze = layout.buildDirectory.dir("generated/licenze/assets")

val controllaLicenze by tasks.registering {
    dependsOn("generateLicenseReport")
    val origine = layout.buildDirectory.file("reports/dependency-license/dipendenze.json")
    val destinazione = cartellaLicenze.map { it.file("licenze.json") }
    val markdown = rootProject.layout.projectDirectory.file("LICENZE.md")
    inputs.file(origine)
    outputs.file(destinazione)
    outputs.file(markdown)
    doLast {
        val radice = origine.get().asFile.let { JsonSlurper().parse(it) as Map<*, *> }
        val voci = (radice["dependencies"] as? List<*> ?: emptyList<Any>()).mapNotNull { it as? Map<*, *> }

        // Licenze accettate: permissive o copyleft deboli (Bouncy Castle è una licenza permissiva in stile MIT). Tutto il resto ferma la build.
        val ammesse = Regex("apache|\\bmit\\b|\\bbsd\\b|\\bisc\\b|eclipse|\\bepl\\b|mozilla|\\bmpl\\b|public domain|unlicense|\\bcc0\\b|bouncy castle", RegexOption.IGNORE_CASE)

        fun licenzaDi(v: Map<*, *>): String? {
            (v["moduleLicense"] as? String)?.takeIf { it.isNotBlank() }?.let { return it }
            val blocco = v["moduleLicenses"] as? Map<*, *> ?: return null
            val nomi = (blocco["moduleLicense"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("name") as? String }
            return nomi?.takeIf { it.isNotEmpty() }?.joinToString(" + ")
        }

        val righe = voci.map { v ->
            val nome = (v["moduleName"] as? String) ?: "?"
            val versione = (v["moduleVersion"] as? String) ?: "?"
            Triple(nome, versione, licenzaDi(v) ?: "SCONOSCIUTA")
        }.distinct().sortedBy { it.first }

        val nonAmmesse = righe.filter { !ammesse.containsMatchIn(it.third) }
        if (nonAmmesse.isNotEmpty()) {
            throw GradleException("Licenze non ammesse o sconosciute: " +
                nonAmmesse.joinToString("; ") { "${it.first} ${it.second} (${it.third})" } +
                ". Verifica la licenza o sostituisci la dipendenza.")
        }

        val json = mapOf("dipendenze" to righe.map { mapOf("nome" to it.first, "versione" to it.second, "licenza" to it.third) })
        destinazione.get().asFile.apply { parentFile.mkdirs() }.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)))

        markdown.asFile.writeText(buildString {
            appendLine("# Licenze open source")
            appendLine()
            appendLine("Elenco generato automaticamente in fase di build (dipendenze incluse quelle indirette).")
            appendLine()
            appendLine("| Componente | Versione | Licenza |")
            appendLine("|---|---|---|")
            righe.forEach { appendLine("| ${it.first} | ${it.second} | ${it.third.replace("|", "/")} |") }
        })
    }
}

android.sourceSets["main"].assets.srcDir(cartellaLicenze)

tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach {
    dependsOn(controllaLicenze)
}
