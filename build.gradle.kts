// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}


/*
 * Añadimos buildscript classpath para el plugin de Google Services (Firebase).
 * Esto permite usar apply(plugin = "com.google.gms.google-services") en el módulo :app
 */
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.0")
    }
}

/*
 * Repositorios comunes para subproyectos. Si ya configuraste repositories en settings.gradle.kts
 * o mediante dependencyResolutionManagement, esto no causará problemas; es redundante pero seguro.
 */
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
