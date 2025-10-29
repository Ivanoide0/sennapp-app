pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        // Registramos el plugin google-services para que pueda usarse en módulos sin version en el plugins block
        id("com.google.gms.google-services") version "4.4.0"
    }
}

dependencyResolutionManagement {
    // Mantén PREFER_SETTINGS (o FAIL_ON_PROJECT_REPOS) según tu preferencia; aquí usamos PREFER_SETTINGS
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SENAPP_2025"
include(":app")
