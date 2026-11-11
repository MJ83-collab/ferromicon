import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Regole, calcoli e modello dati: Kotlin puro, senza dipendenze Android, testabile sul PC.
plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
